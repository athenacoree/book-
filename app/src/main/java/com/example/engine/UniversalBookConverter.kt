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
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

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
    private val htmlDir = File(context.filesDir, "library_html").apply { mkdirs() }

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

        val stagedInput = File(context.cacheDir, "luminabook-import-$bookId.tmp")
        copyUriToFile(uri, stagedInput)
        val inputBytes by lazy(LazyThreadSafetyMode.NONE) { stagedInput.readBytes() }

        val destEpub = File(epubDir, "$bookId.epub")
        var coverPath: String? = null
        var originalAssetPath: String? = null
        var finalTitle = baseTitle
        var finalAuthor = "Autor desconocido"
        var finalDescription = ""
        var finalLanguageHint: String? = null
        var finalGenreHint: String? = null
        var htmlWordCount = 0
        var totalChapters = 1

        when (format) {
            BookFormat.EPUB -> {
                onProgress(ConversionProgress(displayName, format, "Validando estructura EPUB…", 0.55f))
                stagedInput.copyTo(destEpub, overwrite = true)
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
                stagedInput.copyTo(tempPdf, overwrite = true)
                originalAssetPath = tempPdf.absolutePath

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
                onProgress(ConversionProgress(displayName, format, "Preparando HTML interactivo en pantalla completa…", 0.5f))
                val htmlText = decodeTextSmart(inputBytes)
                val htmlFile = File(htmlDir, "$bookId.html")
                htmlFile.writeText(htmlText, Charsets.UTF_8)
                originalAssetPath = htmlFile.absolutePath

                val doc = Jsoup.parse(htmlText)
                finalTitle = doc.title().ifBlank {
                    doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty().ifBlank { baseTitle }
                }
                finalAuthor = doc.selectFirst("meta[name=author]")?.attr("content")
                    ?.trim()?.takeIf { it.isNotEmpty() } ?: "Creador HTML"
                finalDescription = doc.selectFirst("meta[name=description], meta[property=og:description]")
                    ?.attr("content")?.trim().orEmpty()
                    .ifBlank { "Mini juego o aplicación interactiva en HTML ejecutable a pantalla completa." }
                finalGenreHint = doc.selectFirst("meta[name=genre], meta[name=category]")
                    ?.attr("content")?.trim()?.takeIf { it.isNotEmpty() } ?: "Mini Juego HTML"

                val declaredLang = doc.selectFirst("html")?.attr("lang")?.takeIf { it.isNotBlank() }
                val visibleText = doc.body().text()
                val pseudoChapter = listOf(
                    EpubChapter(
                        index = 0,
                        title = finalTitle,
                        blocks = listOf(ReaderBlock.Paragraph(visibleText))
                    )
                )
                finalLanguageHint = BookMetadataDetector.detectLanguage(declaredLang, pseudoChapter)
                htmlWordCount = visibleText.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)

                val embeddedCover = extractDataUriImageBytes(
                    doc.selectFirst("meta[name=cover], meta[property=og:image]")?.attr("content")
                        ?: doc.selectFirst("link[rel*=icon]")?.attr("href")
                )
                coverPath = saveCoverBytes(bookId, embeddedCover)

                val chapters = extractHtmlChapters(doc.body().html(), finalTitle)
                totalChapters = chapters.size
                EpubWriter.writeEpub(
                    outputFile = destEpub,
                    title = finalTitle,
                    author = finalAuthor,
                    language = declaredLang ?: "es",
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
        onProgress(
            ConversionProgress(
                displayName,
                format,
                if (format == BookFormat.HTML) "¡Juego HTML interactivo listo!" else "¡Libro listo en formato EPUB!",
                1.0f
            )
        )

        val resolvedFileSize = if (format == BookFormat.HTML && originalAssetPath != null) {
            File(originalAssetPath).length().coerceAtLeast(destEpub.length())
        } else {
            destEpub.length()
        }

        stagedInput.delete()
        ConvertedBookResult(
            id = bookId,
            title = finalTitle,
            author = finalAuthor,
            originalFormat = format,
            epubPath = destEpub.absolutePath,
            originalPath = originalAssetPath,
            coverPath = coverPath,
            totalChapters = finalParsed?.chapters?.size ?: totalChapters,
            fileSizeBytes = resolvedFileSize,
            isConvertedFromOtherFormat = format != BookFormat.EPUB && format != BookFormat.HTML,
            language = finalLanguageHint ?: finalParsed?.language ?: "Español",
            genre = finalGenreHint ?: finalParsed?.genre ?: "Lectura general",
            publisher = finalParsed?.publisher.orEmpty(),
            publicationYear = finalParsed?.publicationYear.orEmpty(),
            description = finalDescription.ifBlank { finalParsed?.description.orEmpty() },
            wordCount = if (format == BookFormat.HTML) htmlWordCount else (finalParsed?.wordCount ?: 0),
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

    suspend fun saveDownloadedHtmlGame(
        bookId: String,
        title: String,
        author: String,
        htmlBytes: ByteArray,
        language: String? = null,
        subjects: List<String> = emptyList()
    ): ConvertedBookResult = withContext(Dispatchers.IO) {
        require(htmlBytes.isNotEmpty()) { "El juego descargado está vacío" }
        require(htmlBytes.size <= 50 * 1024 * 1024) { "El juego supera el límite de 50 MiB" }

        val safeId = bookId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(80)
            .ifBlank { UUID.randomUUID().toString() }
        val htmlFile = File(htmlDir, "$safeId.html")
        val epubFile = File(epubDir, "$safeId.epub")
        val htmlText = decodeTextSmart(htmlBytes)
        require(htmlText.contains("<html", ignoreCase = true) || htmlText.contains("<body", ignoreCase = true)) {
            "El archivo descargado no parece ser un juego HTML válido"
        }

        htmlFile.writeText(htmlText, Charsets.UTF_8)
        EpubWriter.writeEpub(
            outputFile = epubFile,
            title = title,
            author = author.ifBlank { "Autor desconocido" },
            language = language ?: "es",
            chapters = listOf(
                ChapterInput(
                    title = title,
                    paragraphs = listOf("Juego HTML5 descargado para ejecutarse dentro de LuminaBook.")
                )
            )
        )

        ConvertedBookResult(
            id = safeId,
            title = title,
            author = author.ifBlank { "Autor desconocido" },
            originalFormat = BookFormat.HTML,
            epubPath = epubFile.absolutePath,
            originalPath = htmlFile.absolutePath,
            coverPath = null,
            totalChapters = 1,
            fileSizeBytes = htmlFile.length(),
            isConvertedFromOtherFormat = false,
            language = BookMetadataDetector.mapLanguageCode(language),
            genre = BookMetadataDetector.resolveGenre(subjects, title, emptyList()),
            description = "Juego HTML5 descargado del catálogo LuminaBook.",
            estimatedPages = 1
        )
    }


    suspend fun saveDownloadedGamePackage(
        bookId: String,
        title: String,
        author: String,
        packageBytes: ByteArray,
        fileFormat: String,
        entryFile: String = "index.html",
        language: String? = null,
        subjects: List<String> = emptyList()
    ): ConvertedBookResult = withContext(Dispatchers.IO) {
        require(packageBytes.isNotEmpty()) { "El paquete del juego está vacío" }
        require(packageBytes.size <= 64 * 1024 * 1024) { "El paquete supera el límite de 64 MiB" }
        val safeId = bookId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(80).ifBlank { UUID.randomUUID().toString() }
        val safeEntry = entryFile.replace('\\\\', '/').trimStart('/')
        require(safeEntry.isNotBlank() && safeEntry.split('/').none { it == ".." || it.isBlank() }) {
            "La ruta de entrada del juego no es válida"
        }
        val gameRoot = File(htmlDir, "$safeId.assets")
        val staging = File(htmlDir, "$safeId.assets.tmp")
        staging.deleteRecursively()
        staging.mkdirs()
        var extractedBytes = 0L
        var fileCount = 0
        try {
            when (fileFormat.lowercase()) {
                "html" -> {
                    val html = decodeTextSmart(packageBytes)
                    require(html.contains("<html", true) || html.contains("<body", true)) {
                        "El archivo descargado no parece HTML válido"
                    }
                    File(staging, "index.html").writeText(html, Charsets.UTF_8)
                }
                "zip" -> {
                    ZipInputStream(ByteArrayInputStream(packageBytes)).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val name = entry.name.replace('\\\\', '/')
                            val normalizedName = name.trimEnd('/')
                            require(normalizedName.isNotBlank() && !name.startsWith("/") && !name.contains(":") &&
                                normalizedName.split('/').none { it == ".." || it.isBlank() }) {
                                "El ZIP contiene una ruta no permitida"
                            }
                            if (!entry.isDirectory) {
                                fileCount++
                                require(fileCount <= 1200) { "El paquete contiene demasiados archivos" }
                                val target = File(staging, name).canonicalFile
                                require(target.path.startsWith(staging.canonicalPath + File.separator)) {
                                    "El ZIP intenta salir de la carpeta del juego"
                                }
                                target.parentFile?.mkdirs()
                                target.outputStream().use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    while (true) {
                                        val count = zip.read(buffer)
                                        if (count < 0) break
                                        extractedBytes += count
                                        require(extractedBytes <= 160L * 1024L * 1024L) {
                                            "El juego descomprimido supera 160 MiB"
                                        }
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                    require(fileCount > 0) { "El ZIP no contiene archivos" }
                }
                else -> throw IllegalArgumentException("Formato de juego no compatible: $fileFormat")
            }
            val entry = if (fileFormat.equals("html", true)) File(staging, "index.html") else File(staging, safeEntry)
            require(entry.isFile && entry.length() > 0) {
                "No se encontró el archivo de inicio '$safeEntry' dentro del paquete"
            }
            val htmlText = entry.readText(Charsets.UTF_8)
            require(htmlText.contains("<html", true) || htmlText.contains("<body", true)) {
                "El archivo de inicio no es HTML válido"
            }
            val entryParent = if (fileFormat.equals("zip", true)) {
                safeEntry.substringBeforeLast('/', "").trim('/')
            } else ""
            val baseTag = if (entryParent.isNotBlank() &&
                !Regex("""(?is)<base\\b""").containsMatchIn(htmlText)) {
                """<base href="https://luminabook.local/$entryParent/">"""
            } else ""
            val normalizedHtml = if (baseTag.isNotBlank()) {
                Regex("(?i)<head(?:\\\\s[^>]*)?>").find(htmlText)?.let {
                    htmlText.replaceRange(it.range, it.value + baseTag)
                } ?: "<head>$baseTag</head>$htmlText"
            } else htmlText
            File(staging, "index.html").writeText(normalizedHtml, Charsets.UTF_8)
            val destEpub = File(epubDir, "$safeId.epub")
            EpubWriter.writeEpub(
                outputFile = destEpub,
                title = title,
                author = author.ifBlank { "Creador GITBOOK" },
                language = language ?: "es",
                chapters = listOf(ChapterInput(
                    title = title,
                    paragraphs = listOf("Juego instalado en GITBOOK. Los recursos del paquete se conservan localmente.")
                ))
            )
            gameRoot.deleteRecursively()
            if (!staging.renameTo(gameRoot)) {
                staging.copyRecursively(gameRoot, overwrite = true)
                staging.deleteRecursively()
            }
            val htmlFile = File(gameRoot, "index.html")
            ConvertedBookResult(
                id = safeId,
                title = title,
                author = author.ifBlank { "Creador GITBOOK" },
                originalFormat = BookFormat.HTML,
                epubPath = destEpub.absolutePath,
                originalPath = htmlFile.absolutePath,
                coverPath = null,
                totalChapters = 1,
                fileSizeBytes = gameRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                isConvertedFromOtherFormat = false,
                language = BookMetadataDetector.mapLanguageCode(language),
                genre = BookMetadataDetector.resolveGenre(subjects, title, emptyList()),
                description = "Juego GITBOOK con paquete local de recursos.",
                estimatedPages = 1
            )
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }

    fun deleteBookFiles(epubPath: String, originalPath: String?, coverPath: String?) {
        runCatching { File(epubPath).delete() }
        originalPath?.let { runCatching { File(it).delete() } }
        coverPath?.let { runCatching { File(it).delete() } }
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

    private fun extractDataUriImageBytes(uriString: String?): ByteArray? {
        if (uriString.isNullOrBlank() || !uriString.startsWith("data:image/", ignoreCase = true)) {
            return null
        }
        val base64Part = uriString.substringAfter("base64,", "")
        if (base64Part.isBlank()) return null
        return runCatching {
            android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
        }.getOrNull()
    }

    private fun saveCoverBytes(bookId: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val file = File(coverDir, "$bookId.jpg")
        FileOutputStream(file).use { it.write(bytes) }
        return file.absolutePath
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        val maxBytes = 200L * 1024L * 1024L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")
        input.use { source ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw IllegalArgumentException("El archivo supera el límite de importación de 200 MiB")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
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
