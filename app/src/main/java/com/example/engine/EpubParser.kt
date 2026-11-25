package com.example.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Parses standard EPUB 2 & EPUB 3 archives into structured chapters and ReaderBlocks
 * (including inline images extracted in their exact reading position).
 */
object EpubParser {

    // Prevent oversized or highly-compressed ZIP entries from exhausting app memory.
    private const val MAX_ARCHIVE_ENTRY_BYTES = 32L * 1024L * 1024L

    fun parse(epubFile: File, imageCacheDir: File? = null): ParsedEpub {
        val resolvedImageDir = imageCacheDir?.apply { mkdirs() }
        ZipFile(epubFile).use { zip ->
            val containerBytes = readEntryBytes(zip, "META-INF/container.xml")
                ?: throw IllegalArgumentException("Archivo EPUB inválido: falta container.xml")

            val opfPath = parseContainerOpfPath(containerBytes.inputStream())
                ?: findFirstOpf(zip)
                ?: throw IllegalArgumentException("No se encontró el paquete OPF en el EPUB")

            val opfBaseDir = opfPath.substringBeforeLast('/', "")
            val opfBytes = readEntryBytes(zip, opfPath)
                ?: throw IllegalArgumentException("No se pudo leer $opfPath")

            val opfData = parseOpf(opfBytes.inputStream())
            val tocLabels = parseTocLabels(zip, opfBaseDir, opfData)
            val interactiveDemoHrefs = opfData.interactiveDemoHrefs.mapValues { (_, href) ->
                normalizeZipPath(opfBaseDir, href)
            }
            val interactiveCacheDir = imageCacheDir?.let { File(it, "interactive_demos") }

            val coverBytes = resolveCoverBytes(zip, opfBaseDir, opfData)

            val chapters = mutableListOf<EpubChapter>()
            opfData.spineIds.forEach { idRef ->
                val href = opfData.manifest[idRef] ?: return@forEach
                if (href.contains("nav.xhtml", ignoreCase = true) && opfData.spineIds.size > 1) {
                    return@forEach
                }
                val entryPath = resolvePath(opfBaseDir, href.substringBefore('#'))
                val chapterBaseDir = entryPath.substringBeforeLast('/', "")
                val htmlBytes = readEntryBytes(zip, entryPath) ?: return@forEach
                val html = htmlBytes.toString(Charsets.UTF_8)
                val (extractedTitle, blocks) = parseXhtmlBlocks(
                    html = html,
                    zip = zip,
                    chapterBaseDir = chapterBaseDir,
                    imageCacheDir = resolvedImageDir,
                    interactiveDemoHrefs = interactiveDemoHrefs,
                    interactiveCacheDir = interactiveCacheDir,
                    sourceEpubPath = epubFile.absolutePath
                )
                if (blocks.isNotEmpty()) {
                    val tocTitle = tocLabels[href.substringBefore('#')]
                        ?: tocLabels[entryPath]
                        ?: extractedTitle
                        ?: "Capítulo ${chapters.size + 1}"
                    chapters.add(
                        EpubChapter(
                            index = chapters.size,
                            title = tocTitle.trim(),
                            blocks = blocks
                        )
                    )
                }
            }

            // Fallback if spine was empty or unreadable
            if (chapters.isEmpty()) {
                val htmlEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && (it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true)) }
                    .sortedBy { it.name }
                    .toList()
                htmlEntries.forEach { entry ->
                    val htmlBytes = readEntryBytes(zip, entry.name) ?: return@forEach
                    val html = htmlBytes.toString(Charsets.UTF_8)
                    val chapterBaseDir = entry.name.substringBeforeLast('/', "")
                    val (extractedTitle, blocks) = parseXhtmlBlocks(
                        html = html,
                        zip = zip,
                        chapterBaseDir = chapterBaseDir,
                        imageCacheDir = resolvedImageDir,
                        interactiveDemoHrefs = interactiveDemoHrefs,
                        interactiveCacheDir = interactiveCacheDir,
                        sourceEpubPath = epubFile.absolutePath
                    )
                    if (blocks.isNotEmpty()) {
                        chapters.add(
                            EpubChapter(
                                index = chapters.size,
                                title = extractedTitle ?: "Capítulo ${chapters.size + 1}",
                                blocks = blocks
                            )
                        )
                    }
                }
            }

            val finalTitle = opfData.title.ifBlank { epubFile.nameWithoutExtension }
            val finalChapters = chapters.ifEmpty {
                listOf(
                    EpubChapter(
                        index = 0,
                        title = "Contenido",
                        blocks = listOf(ReaderBlock.Paragraph("No se pudo extraer texto legible de este archivo."))
                    )
                )
            }
            val detectedLanguage = BookMetadataDetector.detectLanguage(opfData.language, finalChapters)
            val resolvedGenre = BookMetadataDetector.resolveGenre(opfData.subjects, finalTitle, finalChapters)
            val stats = BookMetadataDetector.computeStats(finalChapters)

            return ParsedEpub(
                title = finalTitle,
                author = opfData.author.ifBlank { "Autor desconocido" },
                coverBytes = coverBytes,
                chapters = finalChapters,
                language = detectedLanguage,
                genre = resolvedGenre,
                publisher = opfData.publisher,
                publicationYear = opfData.publicationYear,
                description = opfData.description,
                wordCount = stats.wordCount,
                estimatedPages = stats.estimatedPages,
                totalImages = stats.totalImages
            )
        }
    }

    private data class OpfData(
        val title: String,
        val author: String,
        val language: String,
        val subjects: List<String>,
        val publisher: String,
        val publicationYear: String,
        val description: String,
        val manifest: Map<String, String>,
        val manifestMediaTypes: Map<String, String>,
        val spineIds: List<String>,
        val coverId: String?,
        val ncxId: String?,
        val navHref: String?,
        val interactiveDemoHrefs: Map<String, String>
    )

    private fun parseContainerOpfPath(stream: InputStream): String? {
        val doc = Jsoup.parse(stream, "UTF-8", "", Parser.xmlParser())
        val path = doc.selectFirst("rootfile")?.attr("full-path")
        return path?.takeIf { it.isNotBlank() }
    }

    private fun findFirstOpf(zip: ZipFile): String? {
        return zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".opf", ignoreCase = true) }
            ?.name
    }

    private fun parseOpf(stream: InputStream): OpfData {
        val doc = Jsoup.parse(stream, "UTF-8", "", Parser.xmlParser())

        val title = doc.getElementsByTag("dc:title").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("title").firstOrNull()?.text()?.trim()
            ?: ""
        val author = doc.getElementsByTag("dc:creator").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("creator").firstOrNull()?.text()?.trim()
            ?: ""
        val language = doc.getElementsByTag("dc:language").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("language").firstOrNull()?.text()?.trim()
            ?: ""
        val subjects = (doc.getElementsByTag("dc:subject") + doc.getElementsByTag("subject"))
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val publisher = doc.getElementsByTag("dc:publisher").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("publisher").firstOrNull()?.text()?.trim()
            ?: ""
        val rawDate = doc.getElementsByTag("dc:date").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("date").firstOrNull()?.text()?.trim()
            ?: ""
        val publicationYear = Regex("(1\\d{3}|20\\d{2})").find(rawDate)?.value.orEmpty()
        val rawDescription = doc.getElementsByTag("dc:description").firstOrNull()?.text()?.trim()
            ?: doc.getElementsByTag("description").firstOrNull()?.text()?.trim()
            ?: ""
        val description = Jsoup.parse(rawDescription).text().trim().take(480)

        var coverId: String? = null
        doc.select("meta[name=cover]").firstOrNull()?.attr("content")?.let {
            if (it.isNotBlank()) coverId = it
        }

        val manifest = mutableMapOf<String, String>()
        val mediaTypes = mutableMapOf<String, String>()
        var ncxId: String? = null
        var navHref: String? = null
        val interactiveDemoHrefs = mutableMapOf<String, String>()

        doc.select("manifest > item, item").forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            val mediaType = item.attr("media-type")
            val props = item.attr("properties")
            if (id.isNotBlank() && href.isNotBlank()) {
                val decodedHref = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
                manifest[id] = decodedHref
                mediaTypes[id] = mediaType
                if (props.contains("cover-image")) coverId = id
                if (props.split(Regex("\\s+")).contains("nav")) navHref = decodedHref
                val propertyTokens = props.split(Regex("\\s+"))
                if ("gitbook:interactive" in propertyTokens || "gitbook-interactive" in propertyTokens) {
                    val demoId = id.removePrefix("demo-").ifBlank { id }
                    interactiveDemoHrefs[demoId] = decodedHref
                    interactiveDemoHrefs[id] = decodedHref
                }
                if (mediaType == "application/x-dtbncx+xml") ncxId = id
            }
        }

        val spineIds = mutableListOf<String>()
        doc.select("spine > itemref, itemref").forEach { ref ->
            val idref = ref.attr("idref")
            val linear = ref.attr("linear")
            if (idref.isNotBlank() && linear != "no") {
                spineIds.add(idref)
            }
        }

        return OpfData(
            title = title,
            author = author,
            language = language,
            subjects = subjects,
            publisher = publisher,
            publicationYear = publicationYear,
            description = description,
            manifest = manifest,
            manifestMediaTypes = mediaTypes,
            spineIds = spineIds,
            coverId = coverId,
            ncxId = ncxId,
            navHref = navHref,
            interactiveDemoHrefs = interactiveDemoHrefs
        )
    }

    private fun resolveCoverBytes(zip: ZipFile, baseDir: String, opfData: OpfData): ByteArray? {
        val coverHref = opfData.coverId?.let { opfData.manifest[it] }
            ?: opfData.manifest.entries.firstOrNull { (id, href) ->
                val mt = opfData.manifestMediaTypes[id].orEmpty()
                mt.startsWith("image/") && (id.contains("cover", true) || href.contains("cover", true))
            }?.value
        if (coverHref != null) {
            readEntryBytes(zip, resolvePath(baseDir, coverHref))?.let { return it }
        }
        return null
    }

    private fun parseTocLabels(zip: ZipFile, baseDir: String, opfData: OpfData): Map<String, String> {
        val labels = mutableMapOf<String, String>()
        opfData.navHref?.let { navHref ->
            val navPath = resolvePath(baseDir, navHref)
            readEntryBytes(zip, navPath)?.let { bytes ->
                val doc = Jsoup.parse(bytes.toString(Charsets.UTF_8))
                doc.select("nav a[href]").forEach { a ->
                    val href = a.attr("href").substringBefore('#')
                    val text = a.text().trim()
                    if (href.isNotBlank() && text.isNotBlank() && !labels.containsKey(href)) {
                        labels[href] = text
                    }
                }
            }
        }
        if (labels.isEmpty()) {
            opfData.ncxId?.let { ncxId ->
                val ncxHref = opfData.manifest[ncxId] ?: return@let
                val ncxPath = resolvePath(baseDir, ncxHref)
                readEntryBytes(zip, ncxPath)?.let { bytes ->
                    val doc = Jsoup.parse(bytes.toString(Charsets.UTF_8), "", Parser.xmlParser())
                    doc.select("navPoint").forEach { point ->
                        val label = point.selectFirst("navLabel > text")?.text()?.trim().orEmpty()
                        val src = point.selectFirst("content")?.attr("src")?.substringBefore('#').orEmpty()
                        if (src.isNotBlank() && label.isNotBlank() && !labels.containsKey(src)) {
                            labels[src] = label
                        }
                    }
                }
            }
        }
        return labels
    }

    private fun parseXhtmlBlocks(
        html: String,
        zip: ZipFile,
        chapterBaseDir: String,
        imageCacheDir: File?,
        interactiveDemoHrefs: Map<String, String>,
        interactiveCacheDir: File?,
        sourceEpubPath: String
    ): Pair<String?, List<ReaderBlock>> {
        val doc = Jsoup.parse(html)
        val headTitle = doc.title().trim().takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
        val body = doc.body()

        val blocks = mutableListOf<ReaderBlock>()
        var firstHeading: String? = null

        fun extractImageBlock(src: String, alt: String) {
            if (src.isBlank() || imageCacheDir == null) return
            val cleanSrc = runCatching { URLDecoder.decode(src.substringBefore('#'), "UTF-8") }
                .getOrDefault(src.substringBefore('#'))
            val zipPath = normalizeZipPath(chapterBaseDir, cleanSrc)
            val bytes = readEntryBytes(zip, zipPath)
                ?: readEntryBytesByFilename(zip, cleanSrc.substringAfterLast('/'))
                ?: return
            if (bytes.isEmpty()) return

            val ext = cleanSrc.substringAfterLast('.', "img").lowercase()
                .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif") } ?: "jpg"
            val fileName = "img_${zipPath.hashCode().toUInt()}.$ext"
            val outFile = File(imageCacheDir, fileName)
            if (!outFile.exists()) {
                outFile.writeBytes(bytes)
            }
            blocks.add(ReaderBlock.ImageBlock(outFile.absolutePath, alt.trim()))
        }

        fun collectElement(el: Element) {
            if (el.hasAttr("data-gitbook-demo")) {
                val demoId = el.attr("data-gitbook-demo").trim()
                val demoTitle = el.attr("data-gitbook-title").trim()
                    .ifBlank { el.attr("aria-label").trim() }
                    .ifBlank { "Demostración interactiva" }
                val demo = buildInteractiveDemoBlock(
                    demoId, demoTitle, sourceEpubPath, interactiveDemoHrefs, interactiveCacheDir
                )
                if (demo != null) blocks.add(demo)
                else blocks.add(ReaderBlock.Paragraph("La demostración «$demoTitle» no está disponible en este paquete."))
                return
            }
            when (el.tagName().lowercase()) {
                "img" -> {
                    extractImageBlock(el.attr("src"), el.attr("alt"))
                }
                "image" -> {
                    val href = el.attr("xlink:href").ifBlank { el.attr("href") }
                    extractImageBlock(href, "")
                }
                "svg" -> {
                    el.select("image").forEach { img ->
                        val href = img.attr("xlink:href").ifBlank { img.attr("href") }
                        extractImageBlock(href, "")
                    }
                }
                "figure" -> {
                    el.select("img, image").forEach { img ->
                        val src = if (img.tagName().equals("img", true)) {
                            img.attr("src")
                        } else {
                            img.attr("xlink:href").ifBlank { img.attr("href") }
                        }
                        val caption = el.selectFirst("figcaption")?.text().orEmpty().ifBlank { img.attr("alt") }
                        extractImageBlock(src, caption)
                    }
                }
                "h1", "h2", "h3", "h4" -> {
                    val txt = el.text().trim()
                    if (txt.isNotEmpty()) {
                        val level = el.tagName().drop(1).toIntOrNull() ?: 2
                        if (firstHeading == null) firstHeading = txt
                        blocks.add(ReaderBlock.Heading(level, txt))
                    }
                }
                "p" -> {
                    val imgs = el.select("img")
                    val txt = el.text().trim()
                    val videoId = extractYouTubeId(txt)
                        ?: el.selectFirst("a[href]")?.attr("href")?.let(::extractYouTubeId)
                    if (videoId != null) {
                        val remainingText = txt.replace(Regex("https?://\\S+"), "").trim()
                        if (remainingText.isNotBlank()) blocks.add(ReaderBlock.Paragraph(remainingText))
                        blocks.add(ReaderBlock.YouTubeVideo(videoId))
                    } else if (txt.isNotEmpty()) {
                        blocks.add(ReaderBlock.Paragraph(txt))
                    }
                    imgs.forEach { img -> extractImageBlock(img.attr("src"), img.attr("alt")) }
                }
                "blockquote" -> {
                    val txt = el.text().trim()
                    if (txt.isNotEmpty()) {
                        blocks.add(ReaderBlock.Quote(txt))
                    }
                }
                "li" -> {
                    val txt = el.text().trim()
                    if (txt.isNotEmpty()) {
                        blocks.add(ReaderBlock.Paragraph("• $txt"))
                    }
                }
                "hr" -> {
                    blocks.add(ReaderBlock.Divider)
                }
                "div", "section", "article", "main", "header", "ul", "ol", "body", "span" -> {
                    val hasBlockOrMediaChildren = el.children().any {
                        it.tagName().lowercase() in setOf(
                            "h1", "h2", "h3", "h4", "p", "div", "section", "article",
                            "blockquote", "ul", "ol", "hr", "img", "svg", "figure"
                        )
                    }
                    if (hasBlockOrMediaChildren) {
                        el.children().forEach { collectElement(it) }
                    } else {
                        val txt = el.text().trim()
                        if (txt.isNotEmpty()) blocks.add(ReaderBlock.Paragraph(txt))
                    }
                }
                "pre" -> {
                    val txt = el.text().trim()
                    if (txt.isNotEmpty()) blocks.add(ReaderBlock.Paragraph(txt))
                }
            }
        }

        collectElement(body)

        if (blocks.isEmpty()) {
            val fallbackText = body.text().trim()
            if (fallbackText.isNotEmpty()) {
                fallbackText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                    blocks.add(ReaderBlock.Paragraph(it))
                }
            }
        }

        return (firstHeading ?: headTitle) to blocks
    }

    private fun extractYouTubeId(raw: String): String? {
        val value = raw.trim().trimEnd('.', ',', ')', ']', '}', '"', '\'')
        val match = Regex(
            """(?:youtube\.com/(?:watch\?(?:[^#]*?&)?v=|embed/|shorts/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})""",
            RegexOption.IGNORE_CASE
        ).find(value) ?: return null
        return match.groupValues[1]
    }

    private fun buildInteractiveDemoBlock(
        demoId: String,
        title: String,
        sourceEpubPath: String,
        manifest: Map<String, String>,
        cacheDir: File?
    ): ReaderBlock.InteractiveDemo? {
        if (demoId.isBlank() || demoId.length > 80) return null
        val safeId = demoId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val cleanEntry = manifest[demoId] ?: manifest["demo-$demoId"] ?: return null
        if (cleanEntry.isBlank() || cleanEntry.startsWith("/")) return null
        val folder = cleanEntry.substringBeforeLast('/', "")
        if (folder.isBlank() || cleanEntry.split('/').any { it == ".." || it.isBlank() }) return null
        return ReaderBlock.InteractiveDemo(
            id = safeId,
            title = title.take(100),
            sourceEpubPath = sourceEpubPath,
            entryPath = cleanEntry,
            cacheDirPath = cacheDir?.absolutePath.orEmpty()
        )
    }

    private fun resolvePath(baseDir: String, relative: String): String {
        val cleanRel = relative.removePrefix("/")
        return if (baseDir.isBlank()) cleanRel else "$baseDir/$cleanRel"
    }

    private fun normalizeZipPath(baseDir: String, relative: String): String {
        val combined = if (relative.startsWith("/")) {
            relative.removePrefix("/")
        } else if (baseDir.isBlank()) {
            relative
        } else {
            "$baseDir/$relative"
        }
        val parts = combined.split("/")
        val stack = ArrayDeque<String>()
        for (p in parts) {
            when (p) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun readEntryBytes(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, ignoreCase = true) }
            ?: return null
        if (entry.isDirectory || entry.size > MAX_ARCHIVE_ENTRY_BYTES) return null
        return zip.getInputStream(entry).use { readBounded(it) }
    }

    private fun readEntryBytesByFilename(zip: ZipFile, fileName: String): ByteArray? {
        if (fileName.isBlank()) return null
        val entry = zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.substringAfterLast('/').equals(fileName, ignoreCase = true) }
            ?: return null
        if (entry.size > MAX_ARCHIVE_ENTRY_BYTES) return null
        return zip.getInputStream(entry).use { readBounded(it) }
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            if (totalBytes > MAX_ARCHIVE_ENTRY_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
