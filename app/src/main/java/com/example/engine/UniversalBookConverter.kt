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

        val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado")

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

    fun seedStarterHtmlGame(): ConvertedBookResult {
        val gameId = "starter-game-orb"
        val htmlFile = File(htmlDir, "$gameId.html")
        val epubFile = File(epubDir, "$gameId.epub")
        val gameHtml = """
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <meta name="author" content="Lumina Arcade" />
  <meta name="genre" content="Mini Juego · Arcade" />
  <meta name="description" content="Mini juego HTML5 interactivo en pantalla completa: atrapa orbes dorados, esquiva meteoritos y desbloquea notificaciones de récord." />
  <title>Órbita Dorada · Mini Juego</title>
  <style>
    :root {
      --lumina-bg: #0e111a;
      --lumina-ink: #f8fafc;
      --lumina-accent: #f59e0b;
    }
    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }
    html, body {
      margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden;
      background: radial-gradient(circle at 50% 25%, #1f2538 0%, var(--lumina-bg) 75%);
      color: var(--lumina-ink);
      font-family: system-ui, -apple-system, sans-serif;
    }
    canvas { display: block; width: 100%; height: 100%; touch-action: none; }
    .hud {
      position: fixed; top: 14px; left: 16px; right: 76px;
      display: flex; gap: 10px; align-items: center; pointer-events: none; z-index: 10;
    }
    .pill {
      background: rgba(15, 23, 42, 0.86); border: 1px solid rgba(255,255,255,0.16);
      padding: 7px 12px; border-radius: 999px;
      font-size: 13px; font-weight: 700; color: #f8fafc;
    }
    .overlay {
      position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
      background: rgba(10, 12, 18, 0.84); z-index: 20; padding: 24px;
    }
    .card {
      width: 100%; max-width: 340px; background: rgba(24, 28, 42, 0.98);
      border: 1px solid rgba(245, 158, 11, 0.35); border-radius: 24px;
      padding: 24px; text-align: center; box-shadow: 0 18px 48px rgba(0,0,0,0.45);
    }
    h1 { margin: 0 0 8px; font-size: 22px; color: #fef3c7; }
    p { margin: 0 0 18px; font-size: 14px; line-height: 1.45; color: #cbd5e1; }
    button {
      width: 100%; border: 0; border-radius: 16px; padding: 14px 18px;
      background: linear-gradient(135deg, #f59e0b, #d97706); color: #111827;
      font-size: 15px; font-weight: 800; cursor: pointer; margin-bottom: 10px;
    }
    .ghost {
      background: rgba(255,255,255,0.08); color: #e2e8f0; border: 1px solid rgba(255,255,255,0.15);
      font-weight: 600; margin-bottom: 0;
    }
  </style>
</head>
<body>
  <div class="hud">
    <div class="pill" id="scorePill">Puntos: 0</div>
    <div class="pill" id="bestPill">Récord: 0</div>
    <div class="pill" id="livesPill">♥♥♥</div>
  </div>
  <canvas id="game"></canvas>
  <div class="overlay" id="menuOverlay">
    <div class="card">
      <h1>Órbita Dorada</h1>
      <p id="menuMsg">Desliza el dedo para mover tu núcleo luminoso. Atrapa los orbes dorados y esquiva los fragmentos oscuros.</p>
      <button id="playBtn">Jugar ahora</button>
      <button class="ghost" id="notifyBtn">Probar notificación</button>
    </div>
  </div>
  <script>
    const canvas = document.getElementById('game');
    const ctx = canvas.getContext('2d');
    const scorePill = document.getElementById('scorePill');
    const bestPill = document.getElementById('bestPill');
    const livesPill = document.getElementById('livesPill');
    const menuOverlay = document.getElementById('menuOverlay');
    const menuMsg = document.getElementById('menuMsg');
    const playBtn = document.getElementById('playBtn');
    const notifyBtn = document.getElementById('notifyBtn');

    let w = 0, h = 0, dpr = 1;
    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      w = window.innerWidth; h = window.innerHeight;
      canvas.width = Math.max(1, Math.floor(w * dpr));
      canvas.height = Math.max(1, Math.floor(h * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    window.addEventListener('resize', resize);
    resize();

    let best = Number(localStorage.getItem('lumina_orb_best') || '0');
    bestPill.textContent = 'Récord: ' + best;

    let running = false, score = 0, lives = 3, combo = 1, lastSpawn = 0;
    const player = { x: w / 2, y: h * 0.78, tx: w / 2, ty: h * 0.78, r: 20 };
    const items = [];
    const sparks = [];
    const stars = Array.from({ length: 36 }, () => ({
      x: Math.random(), y: Math.random(), r: 0.8 + Math.random() * 1.8, s: 0.2 + Math.random() * 0.6
    }));

    let sharedAudioCtx = null;
    function chime(freq, dur) {
      try {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (!Ctx) return;
        if (!sharedAudioCtx) sharedAudioCtx = new Ctx();
        if (sharedAudioCtx.state === 'suspended') sharedAudioCtx.resume();
        const osc = sharedAudioCtx.createOscillator();
        const gain = sharedAudioCtx.createGain();
        osc.type = 'sine'; osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.08, sharedAudioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.001, sharedAudioCtx.currentTime + dur);
        osc.connect(gain); gain.connect(sharedAudioCtx.destination);
        osc.start(); osc.stop(sharedAudioCtx.currentTime + dur);
      } catch (_) {}
    }

    function sendNotify(title, body) {
      if ('Notification' in window) {
        if (Notification.permission === 'granted') {
          new Notification(title, { body: body });
        } else {
          Notification.requestPermission().then(function(p) {
            if (p === 'granted') new Notification(title, { body: body });
          });
        }
      }
    }

    notifyBtn.addEventListener('click', function() {
      sendNotify('Órbita Dorada', '¡Las notificaciones del mini juego HTML funcionan perfectamente!');
    });

    function startGame() {
      score = 0; lives = 3; combo = 1; items.length = 0; sparks.length = 0;
      player.x = w / 2; player.y = h * 0.78; player.tx = player.x; player.ty = player.y;
      scorePill.textContent = 'Puntos: 0';
      livesPill.textContent = '♥♥♥';
      menuOverlay.style.display = 'none';
      running = true;
      chime(520, 0.14);
    }
    playBtn.addEventListener('click', startGame);

    function moveTarget(clientX, clientY) {
      player.tx = Math.max(24, Math.min(w - 24, clientX));
      player.ty = Math.max(70, Math.min(h - 24, clientY));
    }
    canvas.addEventListener('pointerdown', e => moveTarget(e.clientX, e.clientY));
    canvas.addEventListener('pointermove', e => { if (e.buttons || e.pointerType === 'touch') moveTarget(e.clientX, e.clientY); });

    function burst(x, y, color) {
      for (let i = 0; i < 12; i++) {
        const a = (Math.PI * 2 * i) / 12;
        const sp = 1.5 + Math.random() * 3.5;
        sparks.push({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, life: 1, color });
      }
    }

    function loop(ts) {
      ctx.clearRect(0, 0, w, h);
      stars.forEach(st => {
        st.y = (st.y + st.s * 0.002) % 1;
        ctx.fillStyle = 'rgba(255,255,255,0.35)';
        ctx.beginPath(); ctx.arc(st.x * w, st.y * h, st.r, 0, Math.PI * 2); ctx.fill();
      });

      if (running) {
        player.x += (player.tx - player.x) * 0.24;
        player.y += (player.ty - player.y) * 0.24;

        const spawnEvery = Math.max(280, 680 - score * 2.5);
        if (ts - lastSpawn > spawnEvery) {
          lastSpawn = ts;
          const bad = Math.random() < Math.min(0.45, 0.22 + score / 280);
          items.push({
            x: 28 + Math.random() * (w - 56), y: -24,
            vy: (bad ? 3.2 : 2.6) + Math.min(5.5, score / 45),
            r: bad ? 17 : 14, bad
          });
        }

        for (let i = items.length - 1; i >= 0; i--) {
          const it = items[i];
          it.y += it.vy;
          const dx = it.x - player.x, dy = it.y - player.y;
          if (dx * dx + dy * dy < (it.r + player.r) * (it.r + player.r)) {
            items.splice(i, 1);
            if (it.bad) {
              lives--; combo = 1;
              livesPill.textContent = '♥'.repeat(Math.max(0, lives)) || '0';
              burst(it.x, it.y, '#ef4444');
              chime(180, 0.22);
              if (navigator.vibrate) navigator.vibrate(70);
              if (lives <= 0) {
                running = false;
                if (score > best) {
                  best = score;
                  localStorage.setItem('lumina_orb_best', String(best));
                  bestPill.textContent = 'Récord: ' + best;
                  sendNotify('¡Nuevo Récord en Órbita Dorada!', 'Alcanzaste ' + best + ' puntos.');
                }
                menuMsg.textContent = 'Partida terminada · Puntuación: ' + score + ' · Récord: ' + best;
                playBtn.textContent = 'Jugar otra vez';
                menuOverlay.style.display = 'flex';
              }
            } else {
              score += 10 * combo;
              combo = Math.min(5, combo + 1);
              scorePill.textContent = 'Puntos: ' + score;
              burst(it.x, it.y, '#fbbf24');
              chime(560 + combo * 65, 0.1);
              if (score > 0 && score % 150 === 0) {
                sendNotify('¡Nivel superado!', 'Llevas ' + score + ' puntos sin parar.');
              }
            }
            continue;
          }
          if (it.y > h + 30) items.splice(i, 1);
        }
      }

      items.forEach(it => {
        ctx.beginPath();
        ctx.arc(it.x, it.y, it.r + 5, 0, Math.PI * 2);
        ctx.fillStyle = it.bad ? 'rgba(239, 68, 68, 0.22)' : 'rgba(251, 191, 36, 0.22)';
        ctx.fill();
        ctx.beginPath();
        ctx.arc(it.x, it.y, it.r, 0, Math.PI * 2);
        ctx.fillStyle = it.bad ? '#ef4444' : '#fbbf24';
        ctx.fill();
      });

      for (let i = sparks.length - 1; i >= 0; i--) {
        const sp = sparks[i];
        sp.x += sp.vx; sp.y += sp.vy; sp.life -= 0.04;
        if (sp.life <= 0) { sparks.splice(i, 1); continue; }
        ctx.globalAlpha = sp.life;
        ctx.fillStyle = sp.color;
        ctx.beginPath(); ctx.arc(sp.x, sp.y, 3.2, 0, Math.PI * 2); ctx.fill();
        ctx.globalAlpha = 1;
      }

      // Player core with lightweight soft halo
      ctx.beginPath();
      ctx.arc(player.x, player.y, player.r + 8, 0, Math.PI * 2);
      ctx.fillStyle = 'rgba(245, 158, 11, 0.22)';
      ctx.fill();
      ctx.beginPath();
      ctx.arc(player.x, player.y, player.r, 0, Math.PI * 2);
      ctx.fillStyle = '#fef3c7';
      ctx.fill();
      ctx.lineWidth = 3; ctx.strokeStyle = '#f59e0b'; ctx.stroke();

      requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);
  </script>
</body>
</html>
        """.trimIndent()

        htmlFile.writeText(gameHtml, Charsets.UTF_8)
        EpubWriter.writeEpub(
            outputFile = epubFile,
            title = "Órbita Dorada · Mini Juego",
            author = "Lumina Arcade",
            language = "es",
            chapters = listOf(
                ChapterInput(
                    title = "Juego HTML Interactivo",
                    paragraphs = listOf("Este título es un mini juego HTML5 ejecutable a pantalla completa dentro de la aplicación.")
                )
            )
        )

        return ConvertedBookResult(
            id = gameId,
            title = "Órbita Dorada",
            author = "Lumina Arcade",
            originalFormat = BookFormat.HTML,
            epubPath = epubFile.absolutePath,
            originalPath = htmlFile.absolutePath,
            coverPath = null,
            totalChapters = 1,
            fileSizeBytes = htmlFile.length(),
            isConvertedFromOtherFormat = false,
            language = "Español",
            genre = "Mini Juego · Arcade",
            publisher = "Lumina Arcade",
            publicationYear = "2026",
            description = "Mini juego HTML interactivo en pantalla completa: desliza para atrapar orbes dorados, rompe tu récord y recibe notificaciones.",
            wordCount = 32,
            estimatedPages = 1,
            totalImages = 0
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
