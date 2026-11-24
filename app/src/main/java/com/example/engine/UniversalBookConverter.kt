package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ConversionProgress(
    val fileName: String,
    val sourceFormat: BookFormat,
    val stageMessage: String,
    val progressFraction: Float
)

data class ConvertedBookResult(
    val id: String,
    val title: String,
    val author: String,
    val originalFormat: BookFormat,
    val epubPath: String,
    val originalPath: String?,
    val coverPath: String?,
    val totalChapters: Int,
    val fileSizeBytes: Long,
    val isConvertedFromOtherFormat: Boolean,
    val language: String = "Español",
    val genre: String = "Lectura general",
    val publisher: String = "",
    val publicationYear: String = "",
    val description: String = "",
    val wordCount: Int = 0,
    val estimatedPages: Int = 1,
    val totalImages: Int = 0
)

/**
 * Universal offline converter that accepts PDF, MOBI/AZW/PRC, FB2, TXT, HTML, and EPUB,
 * transforms any format into a clean reflowable EPUB (Apple Books standard),
 * and stores it in internal app storage for 100% offline reading and sharing.
 */
class UniversalBookConverter(private val context: Context) {

    private val epubDir = File(context.filesDir, "library_epubs").apply { mkdirs() }
    private val coverDir = File(context.filesDir, "book_covers").apply { mkdirs() }
    private val pdfOriginalDir = File(context.filesDir, "original_pdfs").apply { mkdirs() }

    suspend fun importAndConvert(
        uri: Uri,
        onProgress: (ConversionProgress) -> Unit = {}
    ): ConvertedBookResult = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "libro_importado.txt"
        val format = BookFormat.fromFileName(displayName)
        val baseTitle = displayName.substringBeforeLast('.').replace('_', ' ').trim()
            .ifBlank { "Libro importado" }
        val bookId = UUID.randomUUID().toString()

        onProgress(ConversionProgress(displayName, format, "Leyendo archivo ${format.label}…", 0.15f))

        val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

        val destEpub = File(epubDir, "$bookId.epub")
        var coverPath: String? = null
        var originalPdfPath: String? = null
        var finalTitle = baseTitle
        var finalAuthor = "Autor desconocido"
        var totalChapters = 1

        when (format) {
            BookFormat.EPUB -> {
                onProgress(ConversionProgress(displayName, format, "Validando estructura EPUB…", 0.55f))
                destEpub.writeBytes(inputBytes)
                val parsed = EpubParser.parse(destEpub)
                finalTitle = parsed.title.ifBlank { baseTitle }
                finalAuthor = parsed.author
                totalChapters = parsed.chapters.size
                coverPath = saveCoverBytes(bookId, parsed.coverBytes)
            }

            BookFormat.PDF -> {
                onProgress(ConversionProgress(displayName, format, "Extrayendo texto y capítulos del PDF…", 0.35f))
                PDFBoxResourceLoader.init(context.applicationContext)

                val tempPdf = File(pdfOriginalDir, "$bookId.pdf")
                tempPdf.writeBytes(inputBytes)
                originalPdfPath = tempPdf.absolutePath

                // Render first page as cover image via native PdfRenderer
                val coverBytes = renderPdfCoverBytes(tempPdf)
                coverPath = saveCoverBytes(bookId, coverBytes)

                val (pdfTitle, pdfAuthor, chapters) = extractPdfChapters(tempPdf, baseTitle) { frac ->
                    onProgress(
                        ConversionProgress(
                            displayName,
                            format,
                            "Convirtiendo páginas PDF a formato libro EPUB…",
                            0.35f + frac * 0.45f
                        )
                    )
                }
                finalTitle = pdfTitle
                finalAuthor = pdfAuthor
                totalChapters = chapters.size

                onProgress(ConversionProgress(displayName, format, "Empaquetando archivo EPUB…", 0.9f))
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    chapters = chapters,
                    coverJpegBytes = coverBytes
                )
            }

            BookFormat.MOBI -> {
                onProgress(ConversionProgress(displayName, format, "Descomprimiendo MOBI (PalmDOC)…", 0.45f))
                val mobi = MobiParser.parse(inputBytes, baseTitle)
                finalTitle = mobi.title
                finalAuthor = mobi.author
                totalChapters = mobi.chapters.size

                onProgress(ConversionProgress(displayName, format, "Convirtiendo a EPUB estándar…", 0.85f))
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    chapters = mobi.chapters
                )
            }

            BookFormat.FB2 -> {
                onProgress(ConversionProgress(displayName, format, "Analizando estructura FictionBook (FB2)…", 0.45f))
                val (fbTitle, fbAuthor, chapters) = parseFb2(inputBytes.toString(Charsets.UTF_8), baseTitle)
                finalTitle = fbTitle
                finalAuthor = fbAuthor
                totalChapters = chapters.size

                onProgress(ConversionProgress(displayName, format, "Generando EPUB…", 0.85f))
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    chapters = chapters
                )
            }

            BookFormat.HTML -> {
                onProgress(ConversionProgress(displayName, format, "Estructurando HTML a EPUB…", 0.5f))
                val doc = Jsoup.parse(inputBytes.toString(Charsets.UTF_8))
                finalTitle = doc.title().ifBlank { baseTitle }
                val chapters = extractHtmlChapters(doc.body().html(), finalTitle)
                totalChapters = chapters.size
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    chapters = chapters
                )
            }

            BookFormat.TXT -> {
                onProgress(ConversionProgress(displayName, format, "Formateando texto a capítulos EPUB…", 0.5f))
                val text = decodeTextSmart(inputBytes)
                val chapters = splitPlainTextToChapters(text, baseTitle)
                totalChapters = chapters.size
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    chapters = chapters
                )
            }
        }

        val finalParsed = runCatching { EpubParser.parse(destEpub) }.getOrNull()
        onProgress(ConversionProgress(displayName, format, "¡Libro listo en formato EPUB!", 1.0f))

        ConvertedBookResult(
            id = bookId,
            title = finalTitle,
            author = finalAuthor,
            originalFormat = format,
            epubPath = destEpub.absolutePath,
            originalPath = originalPdfPath,
            coverPath = coverPath,
            totalChapters = finalParsed?.chapters?.size ?: totalChapters,
            fileSizeBytes = destEpub.length(),
            isConvertedFromOtherFormat = format != BookFormat.EPUB,
            language = finalParsed?.language ?: "Español",
            genre = finalParsed?.genre ?: "Lectura general",
            publisher = finalParsed?.publisher.orEmpty(),
            publicationYear = finalParsed?.publicationYear.orEmpty(),
            description = finalParsed?.description.orEmpty(),
            wordCount = finalParsed?.wordCount ?: 0,
            estimatedPages = finalParsed?.estimatedPages ?: 1,
            totalImages = finalParsed?.totalImages ?: 0
        )
    }

    suspend fun saveDownloadedEpub(
        bookId: String,
        title: String,
        author: String,
        epubBytes: ByteArray,
        coverBytes: ByteArray?,
        hintLanguage: String? = null,
        hintSubjects: List<String> = emptyList()
    ): ConvertedBookResult = withContext(Dispatchers.IO) {
        val destEpub = File(epubDir, "$bookId.epub")
        destEpub.writeBytes(epubBytes)
        val parsed = runCatching { EpubParser.parse(destEpub) }.getOrNull()
        val finalCoverBytes = coverBytes ?: parsed?.coverBytes
        val coverPath = saveCoverBytes(bookId, finalCoverBytes)
        val resolvedTitle = title.ifBlank { parsed?.title ?: "Libro" }
        val resolvedLang = parsed?.language ?: BookMetadataDetector.mapLanguageCode(hintLanguage)
        val resolvedGenre = if (hintSubjects.isNotEmpty() && parsed != null) {
            BookMetadataDetector.resolveGenre(hintSubjects, resolvedTitle, parsed.chapters)
        } else {
            parsed?.genre ?: "Clásico"
        }
        ConvertedBookResult(
            id = bookId,
            title = resolvedTitle,
            author = author.ifBlank { parsed?.author ?: "Autor desconocido" },
            originalFormat = BookFormat.EPUB,
            epubPath = destEpub.absolutePath,
            originalPath = null,
            coverPath = coverPath,
            totalChapters = parsed?.chapters?.size ?: 1,
            fileSizeBytes = destEpub.length(),
            isConvertedFromOtherFormat = false,
            language = resolvedLang,
            genre = resolvedGenre,
            publisher = parsed?.publisher.orEmpty(),
            publicationYear = parsed?.publicationYear.orEmpty(),
            description = parsed?.description.orEmpty(),
            wordCount = parsed?.wordCount ?: 0,
            estimatedPages = parsed?.estimatedPages ?: 1,
            totalImages = parsed?.totalImages ?: 0
        )
    }

    fun deleteBookFiles(epubPath: String, originalPath: String?, coverPath: String?) {
        runCatching { File(epubPath).delete() }
        originalPath?.let { runCatching { File(it).delete() } }
        coverPath?.let { runCatching { File(it).delete() } }
    }

    fun seedStarterBooks(): List<ConvertedBookResult> {
        val quijoteId = "starter-quijote"
        val quijoteEpub = File(epubDir, "$quijoteId.epub")
        val quijoteChapters = listOf(
            ChapterInput(
                title = "Capítulo I · Del famoso hidalgo",
                paragraphs = listOf(
                    "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor.",
                    "Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados, lantejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda.",
                    "Frisaba la edad de nuestro hidalgo con los cincuenta años; era de complexión recia, seco de carnes, enjuto de rostro, gran madrugador y amigo de la caza.",
                    "Es, pues, de saber que este sobredicho hidalgo, los ratos que estaba ocioso —que eran los más del año—, se daba a leer libros de caballerías, con tanta afición y gusto, que olvidó casi de todo punto el ejercicio de la caza, y aun la administración de su hacienda.",
                    "En resolución, él se enfrascó tanto en su lectura, que se le pasaban las noches leyendo de claro en claro, y los días de turbio en turbio; y así, del poco dormir y del mucho leer, se le secó el cerebro de manera que vino a perder el juicio."
                )
            ),
            ChapterInput(
                title = "Capítulo II · La primera salida",
                paragraphs = listOf(
                    "Hechas, pues, estas prevenciones, no quiso aguardar más tiempo a poner en efeto su pensamiento, apretándole a ello la falta que él pensaba que hacía en el mundo su tardanza, según eran los agravios que pensaba deshacer, tuertos que enderezar, sinrazones que emendar y abusos que mejorar.",
                    "Y así, sin dar parte a persona alguna de su intención, y sin que nadie le viese, una mañana, antes del día, que era uno de los calurosos del mes de julio, se armó de todas sus armas, subió sobre Rocinante, puesta su mal compuesta celada, embrazó su adarga, tomó su lanza y por la puerta falsa de un corral salió al campo con grandísimo contento y alborozo.",
                    "¿Quién duda sino que en los venideros tiempos, cuando salga a luz la verdadera historia de mis famosos hechos, que el sabio que los escribiere no ponga, cuando llegue a contar esta mi primera salida tan de mañana, desta manera?: Apenas había el rubicundo Apolo tendido por la faz de la ancha y espaciosa tierra las doradas hebras de sus hermosos cabellos, cuando el famoso caballero don Quijote de la Mancha comenzó a caminar por el antiguo y conocido campo de Montiel."
                )
            ),
            ChapterInput(
                title = "Capítulo III · Armado caballero",
                paragraphs = listOf(
                    "Y así, fatigado deste pensamiento, abrevió su venteril y limitada cena; la cual acabada, llamó al ventero y, encerrándose con él en la caballeriza, se hincó de rodillas ante él, diciéndole: No me levantaré jamás de donde estoy, valeroso caballero, fasta que la vuestra cortesía me otorgue un don que pedirle quiero.",
                    "El ventero, que vio a su huésped a sus pies y oyó semejantes razones, estaba confuso mirándole, sin saber qué hacerse ni decirle, y porfiaba con él que se levantase, y jamás quiso, hasta que le hubo de decir que él le otorgaba el don que le pedía.",
                    "No esperaba yo menos de la gran magnificencia vuestra, señor mío —respondió don Quijote—; y así os digo que el don que os he pedido y de vuestra liberalidad me ha sido otorgado es que mañana en aquel día me habéis de armar caballero."
                )
            )
        )
        EpubWriter.writeEpub(
            outputFile = quijoteEpub,
            title = "Don Quijote de la Mancha",
            author = "Miguel de Cervantes",
            language = "es",
            chapters = quijoteChapters
        )

        val fabulasId = "starter-fabulas"
        val fabulasEpub = File(epubDir, "$fabulasId.epub")
        val fabulasChapters = listOf(
            ChapterInput(
                title = "La cigarra y la hormiga",
                paragraphs = listOf(
                    "Cantando la cigarra pasó el verano entero, sin hacer provisiones allá para el invierno.",
                    "Los fríos la obligaron a guardar el silencio y a acogerse al abrigo de su estrecho aposento.",
                    "Vio se desproveída del precioso sustento: sin mosca, sin gusano, sin trigo, sin centeno. Habitaba la hormiga allí tabique en medio, y con mil expresiones de atención y respeto la dijo: Doña Hormiga, pues que en vuestros graneros sobran las provisiones para vuestros hijuelos, prestad alguna cosa con que viva este invierno esta triste cigarra.",
                    "Respondió la hormiga: ¿Qué hacías tú cuando el tiempo era cálido y hermoso? —Cantaba noche y día libremente. —¿Cantabas? ¡Me alegra saberlo! Ahora baila."
                )
            ),
            ChapterInput(
                title = "La liebre y la tortuga",
                paragraphs = listOf(
                    "En el mundo de los animales vivía una liebre muy orgullosa y vanidosa, que no cesaba de pregonar que ella era el animal más veloz del bosque, y que se pasaba el día burlándose de la lentitud de la tortuga.",
                    "Un día, a la tortuga se le ocurrió hacerle una inusual apuesta a la liebre: Estoy segura de poder ganarte una carrera. La liebre, muy divertida, aceptó y todos los animales se reunieron para presenciar la carrera.",
                    "Confiada en su ligereza, la liebre dejó partir a la tortuga y se quedó remoloneando bajo un árbol hasta quedarse dormida. Mientras tanto, paso a paso y sin detenerse jamás, la tortuga cruzó la meta entre vítores.",
                    "Moraleja: La constancia y el ritmo sereno superan muchas veces a la prisa arrogante."
                )
            )
        )
        EpubWriter.writeEpub(
            outputFile = fabulasEpub,
            title = "Fábulas Literarias",
            author = "Félix María Samaniego",
            language = "es",
            chapters = fabulasChapters
        )

        val parsedQuijote = runCatching { EpubParser.parse(quijoteEpub) }.getOrNull()
        val parsedFabulas = runCatching { EpubParser.parse(fabulasEpub) }.getOrNull()

        return listOf(
            ConvertedBookResult(
                id = quijoteId,
                title = "Don Quijote de la Mancha",
                author = "Miguel de Cervantes",
                originalFormat = BookFormat.EPUB,
                epubPath = quijoteEpub.absolutePath,
                originalPath = null,
                coverPath = null,
                totalChapters = quijoteChapters.size,
                fileSizeBytes = quijoteEpub.length(),
                isConvertedFromOtherFormat = false,
                language = parsedQuijote?.language ?: "Español",
                genre = "Novela clásica · Aventura",
                publisher = "Edición LuminaBook",
                publicationYear = "1605",
                description = "Las aventuras del ingenioso hidalgo Alonso Quijano recorriendo los campos de la Mancha junto a Sancho Panza.",
                wordCount = parsedQuijote?.wordCount ?: 420,
                estimatedPages = (parsedQuijote?.estimatedPages ?: 2).coerceAtLeast(2),
                totalImages = parsedQuijote?.totalImages ?: 0
            ),
            ConvertedBookResult(
                id = fabulasId,
                title = "Fábulas Literarias",
                author = "Félix María Samaniego",
                originalFormat = BookFormat.EPUB,
                epubPath = fabulasEpub.absolutePath,
                originalPath = null,
                coverPath = null,
                totalChapters = fabulasChapters.size,
                fileSizeBytes = fabulasEpub.length(),
                isConvertedFromOtherFormat = false,
                language = parsedFabulas?.language ?: "Español",
                genre = "Fábula · Poesía y moral",
                publisher = "Edición LuminaBook",
                publicationYear = "1781",
                description = "Relatos clásicos breves con enseñanzas atemporales sobre la constancia, el ingenio y la prudencia.",
                wordCount = parsedFabulas?.wordCount ?: 280,
                estimatedPages = (parsedFabulas?.estimatedPages ?: 2).coerceAtLeast(2),
                totalImages = parsedFabulas?.totalImages ?: 0
            )
        )
    }

    private fun extractPdfChapters(
        pdfFile: File,
        fallbackTitle: String,
        onPageProgress: (Float) -> Unit
    ): Triple<String, String, List<ChapterInput>> {
        PDDocument.load(pdfFile).use { document ->
            val info = document.documentInformation
            val title = info?.title?.trim().takeIf { !it.isNullOrBlank() } ?: fallbackTitle
            val author = info?.author?.trim().takeIf { !it.isNullOrBlank() } ?: "Autor desconocido"
            val pageCount = document.numberOfPages.coerceAtLeast(1)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            val chapters = mutableListOf<ChapterInput>()
            var currentTitle = title
            var currentParagraphs = mutableListOf<String>()
            val chapterPattern = Regex(
                "^(CAP[IÍ]TULO|CHAPTER|PARTE|PART|LIBRO|BOOK|SECCI[ÓO]N)\\s+[IVXLCDM0-9]+.*",
                RegexOption.IGNORE_CASE
            )

            for (page in 1..pageCount) {
                onPageProgress(page.toFloat() / pageCount.toFloat())
                stripper.startPage = page
                stripper.endPage = page
                val rawPageText = stripper.getText(document) ?: ""
                val cleanedParagraphs = cleanPdfPageText(rawPageText)

                for (para in cleanedParagraphs) {
                    if (para.length < 90 && chapterPattern.matches(para)) {
                        if (currentParagraphs.isNotEmpty()) {
                            chapters.add(ChapterInput(currentTitle, currentParagraphs))
                            currentParagraphs = mutableListOf()
                        }
                        currentTitle = para
                    } else {
                        currentParagraphs.add(para)
                    }
                }

                // Group every 10 pages if no explicit chapter headers appeared
                if (currentParagraphs.size >= 48) {
                    chapters.add(ChapterInput(currentTitle, currentParagraphs))
                    currentParagraphs = mutableListOf()
                    currentTitle = "Páginas ${page + 1}–${minOf(page + 10, pageCount)}"
                }
            }

            if (currentParagraphs.isNotEmpty()) {
                chapters.add(ChapterInput(currentTitle, currentParagraphs))
            }

            if (chapters.isEmpty()) {
                chapters.add(
                    ChapterInput(
                        title = title,
                        paragraphs = listOf(
                            "Este PDF parece contener páginas escaneadas o imágenes sin capa de texto.",
                            "Usa el botón 'PDF Original' en la barra superior del lector para visualizar sus páginas originales con nitidez."
                        )
                    )
                )
            }

            return Triple(title, author, chapters)
        }
    }

    private fun cleanPdfPageText(raw: String): List<String> {
        val lines = raw.replace("\r\n", "\n").split("\n")
        val paragraphs = mutableListOf<String>()
        val buffer = StringBuilder()

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) paragraphs.add(text)
            buffer.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flush()
                continue
            }
            // Skip lone page numbers
            if (trimmed.length <= 4 && trimmed.all { it.isDigit() }) continue

            if (buffer.isEmpty()) {
                buffer.append(trimmed)
            } else if (buffer.endsWith("-")) {
                buffer.deleteCharAt(buffer.length - 1)
                buffer.append(trimmed)
            } else {
                buffer.append(" ").append(trimmed)
            }

            // Treat sentence-ending short lines as paragraph breaks
            if (trimmed.length < 55 && (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?"))) {
                flush()
            }
        }
        flush()
        return paragraphs
    }

    private fun renderPdfCoverBytes(pdfFile: File): ByteArray? {
        return runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val targetWidth = 480
                        val targetHeight = (targetWidth * (page.height.toFloat() / page.width.coerceAtLeast(1))).toInt()
                            .coerceIn(320, 860)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
                        bitmap.recycle()
                        out.toByteArray()
                    }
                }
            }
        }.getOrNull()
    }

    private fun parseFb2(xml: String, fallbackTitle: String): Triple<String, String, List<ChapterInput>> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val title = doc.selectFirst("book-title")?.text()?.trim().takeIf { !it.isNullOrBlank() } ?: fallbackTitle
        val firstName = doc.selectFirst("author > first-name")?.text()?.trim().orEmpty()
        val lastName = doc.selectFirst("author > last-name")?.text()?.trim().orEmpty()
        val author = "$firstName $lastName".trim().ifBlank { "Autor desconocido" }

        val chapters = mutableListOf<ChapterInput>()
        val sections = doc.select("body > section")
        if (sections.isNotEmpty()) {
            sections.forEachIndexed { idx, sec ->
                val secTitle = sec.selectFirst("> title")?.text()?.trim().takeIf { !it.isNullOrBlank() }
                    ?: "Capítulo ${idx + 1}"
                val paras = sec.select("p, cite, subtitle").map { it.text().trim() }.filter { it.isNotEmpty() }
                if (paras.isNotEmpty()) {
                    chapters.add(ChapterInput(secTitle, paras))
                }
            }
        } else {
            val paras = doc.select("body p").map { it.text().trim() }.filter { it.isNotEmpty() }
            if (paras.isNotEmpty()) {
                chapters.addAll(paras.chunked(45).mapIndexed { idx, chunk ->
                    ChapterInput(if (idx == 0) title else "Parte ${idx + 1}", chunk)
                })
            }
        }

        return Triple(title, author, chapters.ifEmpty {
            listOf(ChapterInput(title, listOf("No se encontró texto legible en el archivo FB2.")))
        })
    }

    private fun extractHtmlChapters(html: String, fallbackTitle: String): List<ChapterInput> {
        val doc = Jsoup.parse(html)
        val paras = doc.select("p, h1, h2, h3, blockquote, li")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        return if (paras.isEmpty()) {
            listOf(ChapterInput(fallbackTitle, listOf(doc.text().ifBlank { "Sin contenido." })))
        } else {
            paras.chunked(45).mapIndexed { idx, chunk ->
                ChapterInput(if (idx == 0) fallbackTitle else "Sección ${idx + 1}", chunk)
            }
        }
    }

    private fun splitPlainTextToChapters(text: String, fallbackTitle: String): List<ChapterInput> {
        val normalized = text.replace("\r\n", "\n")
        val rawParagraphs = normalized.split(Regex("\n\\s*\n"))
            .map { it.replace("\n", " ").trim() }
            .filter { it.isNotEmpty() }

        if (rawParagraphs.isEmpty()) {
            return listOf(ChapterInput(fallbackTitle, listOf("Archivo de texto vacío.")))
        }

        val chapterRegex = Regex("^(CAP[IÍ]TULO|CHAPTER|PARTE|LIBRO)\\s+[IVXLCDM0-9]+.*", RegexOption.IGNORE_CASE)
        val chapters = mutableListOf<ChapterInput>()
        var currentTitle = fallbackTitle
        var currentParas = mutableListOf<String>()

        for (para in rawParagraphs) {
            if (para.length < 90 && chapterRegex.matches(para)) {
                if (currentParas.isNotEmpty()) {
                    chapters.add(ChapterInput(currentTitle, currentParas))
                    currentParas = mutableListOf()
                }
                currentTitle = para
            } else {
                currentParas.add(para)
                if (currentParas.size >= 45) {
                    chapters.add(ChapterInput(currentTitle, currentParas))
                    currentParas = mutableListOf()
                    currentTitle = "Parte ${chapters.size + 1}"
                }
            }
        }
        if (currentParas.isNotEmpty()) {
            chapters.add(ChapterInput(currentTitle, currentParas))
        }
        return chapters
    }

    private fun decodeTextSmart(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            String(bytes, charset("ISO-8859-1"))
        } else {
            utf8
        }
    }

    private fun saveCoverBytes(bookId: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val file = File(coverDir, "$bookId.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
