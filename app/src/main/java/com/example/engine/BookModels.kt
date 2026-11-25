package com.example.engine

enum class BookFormat(val label: String, val extensions: List<String>) {
    EPUB("EPUB", listOf("epub")),
    PDF("PDF", listOf("pdf")),
    MOBI("MOBI", listOf("mobi", "azw", "prc")),
    FB2("FB2", listOf("fb2")),
    TXT("TXT", listOf("txt", "md")),
    HTML("HTML", listOf("html", "htm"));

    companion object {
        fun fromFileName(fileName: String): BookFormat {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: TXT
        }
    }
}

enum class ReaderTheme(val displayName: String) {
    WHITE("Blanco"),
    PAPER("Papel"),
    SEPIA("Sepia"),
    NIGHT("Noche"),
    BLACK("Negro")
}

enum class ReaderFont(val displayName: String) {
    LITERATA("Literata"),
    LORA("Lora"),
    MERRIWEATHER("Merriweather"),
    SANS("Jakarta"),
    NUNITO("Nunito")
}

enum class ReadingMode(val displayName: String) {
    HORIZONTAL_PAGES("Lado a lado"),
    VERTICAL_PAGES("Arriba / Abajo"),
    CONTINUOUS_SCROLL("Continuo")
}

enum class TapNavigationMode(val displayName: String) {
    VERTICAL_ZONES("Arriba / Abajo"),
    HORIZONTAL_ZONES("Lados")
}

enum class ReaderTextAlign(val displayName: String) {
    JUSTIFY("Justificado"),
    LEFT("Izquierda")
}

enum class PageTransitionStyle(val displayName: String) {
    CURL_3D("Libro 3D"),
    CUBE_3D("Cubo 3D"),
    FLIP_3D("Giro 3D"),
    FAN_3D("Abanico 3D"),
    SLIDE("Deslizar")
}

enum class LiveHighlightStyle(val displayName: String) {
    LIQUID_TRAIL("Estela líquida"),
    BOLD_LARGE("Grande y oscuro"),
    FOCUS_GLOW("Enfoque total")
}

enum class LiveUnitMode(val displayName: String) {
    WORD("Por palabra"),
    PHRASE("Por frases")
}

enum class AppThemeMode(val displayName: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro")
}

enum class HtmlOrientation(val displayName: String) {
    AUTO("Automática"),
    PORTRAIT("Vertical"),
    LANDSCAPE("Horizontal")
}

sealed class ReaderBlock {
    data class Heading(val level: Int, val text: String) : ReaderBlock()
    data class Paragraph(val text: String) : ReaderBlock()
    data class Quote(val text: String) : ReaderBlock()
    data class ImageBlock(val imagePath: String, val caption: String = "") : ReaderBlock()
    data class YouTubeVideo(val videoId: String, val title: String = "Vídeo de YouTube") : ReaderBlock()
    data class InteractiveDemo(
        val id: String,
        val title: String,
        val sourceEpubPath: String,
        val entryPath: String,
        val cacheDirPath: String,
        val permissions: Set<String> = emptySet()
    ) : ReaderBlock()
    data object Divider : ReaderBlock()
}

data class ChapterInput(
    val title: String,
    val paragraphs: List<String>,
    val headings: Map<Int, String> = emptyMap(),
    val xhtmlBody: String? = null
)

data class EpubAsset(val path: String, val bytes: ByteArray, val mediaType: String)

data class EpubChapter(
    val index: Int,
    val title: String,
    val blocks: List<ReaderBlock>
)

data class ParsedEpub(
    val title: String,
    val author: String,
    val coverBytes: ByteArray?,
    val chapters: List<EpubChapter>,
    val language: String = "Español",
    val genre: String = "General",
    val publisher: String = "",
    val publicationYear: String = "",
    val description: String = "",
    val wordCount: Int = 0,
    val estimatedPages: Int = 1,
    val totalImages: Int = 0
)
