package com.example.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a standards-compliant EPUB 3 / EPUB 2 compatible archive
 * readable in LuminaBook and Apple Books (iPhone/iPad).
 */
object EpubWriter {

    fun writeEpub(
        outputFile: File,
        title: String,
        author: String,
        language: String = "es",
        chapters: List<ChapterInput>,
        coverJpegBytes: ByteArray? = null,
        assets: List<EpubAsset> = emptyList()
    ) {
        outputFile.parentFile?.mkdirs()
        val bookUuid = "urn:uuid:${UUID.randomUUID()}"
        val safeTitle = escapeXml(title.ifBlank { "Libro sin título" })
        val safeAuthor = escapeXml(author.ifBlank { "Autor desconocido" })
        val safeChapters = if (chapters.isEmpty()) {
            listOf(ChapterInput("Contenido", listOf("Documento importado sin contenido textual.")))
        } else {
            chapters
        }

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. mimetype MUST be the first entry and MUST be STORED (uncompressed)
                val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
                val mimeEntry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = CRC32().apply { update(mimeBytes) }.value
                }
                zos.putNextEntry(mimeEntry)
                zos.write(mimeBytes)
                zos.closeEntry()

                // 2. META-INF/container.xml
                writeTextEntry(
                    zos,
                    "META-INF/container.xml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
                )

                // 3. OEBPS/style.css
                writeTextEntry(
                    zos,
                    "OEBPS/style.css",
                    """body {
  font-family: Georgia, serif;
  line-height: 1.65;
  margin: 5%;
  text-align: justify;
}
h1, h2 {
  font-weight: 600;
  margin-top: 1.2em;
  margin-bottom: 0.6em;
  text-align: left;
}
p {
  margin-top: 0;
  margin-bottom: 0.85em;
  text-indent: 1.2em;
}
blockquote {
  margin: 1em 1.5em;
  font-style: italic;
}"""
                )

                // 4. Optional cover image
                val hasCover = coverJpegBytes != null && coverJpegBytes.isNotEmpty()
                if (hasCover) {
                    val coverEntry = ZipEntry("OEBPS/cover.jpg")
                    zos.putNextEntry(coverEntry)
                    zos.write(coverJpegBytes)
                    zos.closeEntry()
                }

                // Imported media assets (images, audio and other EPUB resources).
                val safeAssets = assets.filter { asset ->
                    asset.path.startsWith("images/") && !asset.path.contains("..") &&
                        asset.path.none { it == '\\' } && asset.bytes.isNotEmpty()
                }.distinctBy { it.path }
                safeAssets.forEach { asset ->
                    zos.putNextEntry(ZipEntry("OEBPS/${asset.path}"))
                    zos.write(asset.bytes)
                    zos.closeEntry()
                }

                // 5. Chapter XHTML files
                safeChapters.forEachIndexed { idx, ch ->
                    val fileName = "chapter_${String.format("%03d", idx + 1)}.xhtml"
                    val chTitle = escapeXml(ch.title.ifBlank { "Capítulo ${idx + 1}" })
                    val bodyBuilder = StringBuilder()
                    bodyBuilder.append("<h1>").append(chTitle).append("</h1>\n")
                    if (!ch.xhtmlBody.isNullOrBlank()) bodyBuilder.append(ch.xhtmlBody).append("\n")
                    else ch.paragraphs.forEachIndexed { pIdx, p ->
                        val heading = ch.headings[pIdx]
                        if (!heading.isNullOrBlank()) {
                            bodyBuilder.append("<h2>").append(escapeXml(heading)).append("</h2>\n")
                        }
                        val trimmed = p.trim()
                        if (trimmed.isNotEmpty()) {
                            if (trimmed == "---" || trimmed == "* * *") {
                                bodyBuilder.append("<hr/>\n")
                            } else if (trimmed.startsWith("> ")) {
                                bodyBuilder.append("<blockquote>").append(escapeXml(trimmed.removePrefix("> "))).append("</blockquote>\n")
                            } else {
                                bodyBuilder.append("<p>").append(escapeXml(trimmed)).append("</p>\n")
                            }
                        }
                    }

                    val xhtml = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="$language">
<head>
  <meta charset="UTF-8"/>
  <title>$chTitle</title>
  <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
$bodyBuilder
</body>
</html>"""
                    writeTextEntry(zos, "OEBPS/$fileName", xhtml)
                }

                // 6. OEBPS/nav.xhtml (EPUB 3 navigation)
                val navItems = safeChapters.mapIndexed { idx, ch ->
                    val fileName = "chapter_${String.format("%03d", idx + 1)}.xhtml"
                    """      <li><a href="$fileName">${escapeXml(ch.title.ifBlank { "Capítulo ${idx + 1}" })}</a></li>"""
                }.joinToString("\n")

                writeTextEntry(
                    zos,
                    "OEBPS/nav.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="$language">
<head>
  <meta charset="UTF-8"/>
  <title>Índice</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Contenido</h1>
    <ol>
$navItems
    </ol>
  </nav>
</body>
</html>"""
                )

                // 7. OEBPS/toc.ncx (EPUB 2 backward compatibility)
                val ncxPoints = safeChapters.mapIndexed { idx, ch ->
                    val fileName = "chapter_${String.format("%03d", idx + 1)}.xhtml"
                    """    <navPoint id="navPoint-${idx + 1}" playOrder="${idx + 1}">
      <navLabel><text>${escapeXml(ch.title.ifBlank { "Capítulo ${idx + 1}" })}</text></navLabel>
      <content src="$fileName"/>
    </navPoint>"""
                }.joinToString("\n")

                writeTextEntry(
                    zos,
                    "OEBPS/toc.ncx",
                    """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="$bookUuid"/>
    <meta name="dtb:depth" content="1"/>
  </head>
  <docTitle><text>$safeTitle</text></docTitle>
  <navMap>
$ncxPoints
  </navMap>
</ncx>"""
                )

                // 8. OEBPS/content.opf
                val manifestChapters = safeChapters.indices.joinToString("\n") { idx ->
                    val fileName = "chapter_${String.format("%03d", idx + 1)}.xhtml"
                    """    <item id="ch${idx + 1}" href="$fileName" media-type="application/xhtml+xml"/>"""
                }
                val spineItems = safeChapters.indices.joinToString("\n") { idx ->
                    """    <itemref idref="ch${idx + 1}"/>"""
                }
                val assetManifest = safeAssets.mapIndexed { idx, asset ->
                    """    <item id="asset-${idx + 1}" href="${asset.path}" media-type="${escapeXml(asset.mediaType)}"/>"""
                }.joinToString("\n")
                val coverManifest = if (hasCover) {
                    """    <item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""
                } else ""
                val coverMeta = if (hasCover) {
                    """    <meta name="cover" content="cover-image"/>"""
                } else ""

                writeTextEntry(
                    zos,
                    "OEBPS/content.opf",
                    """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="BookId">$bookUuid</dc:identifier>
    <dc:title>$safeTitle</dc:title>
    <dc:creator>$safeAuthor</dc:creator>
    <dc:language>$language</dc:language>
$coverMeta
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="css" href="style.css" media-type="text/css"/>
$coverManifest
$assetManifest
$manifestChapters
  </manifest>
  <spine toc="ncx">
$spineItems
  </spine>
</package>"""
                )
            }
        }
    }

    private fun writeTextEntry(zos: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
