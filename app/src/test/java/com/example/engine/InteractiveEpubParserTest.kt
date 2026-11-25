package com.example.engine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveEpubParserTest {
    @Test
    fun publisherManifestMarkerBecomesReaderBlockInReadingOrder() {
        val temp = Files.createTempDirectory("gitbook-epub-marker-test").toFile()
        try {
            val epub = File(temp, "interactive-book.epub")
            val entries = mapOf(
                "META-INF/container.xml" to """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="3.0" prefix="gitbook: https://gitbook.app/ns/epub#"><metadata><dc:title>Manual interactivo</dc:title><dc:creator>GITBOOK</dc:creator><dc:language>es</dc:language></metadata><manifest><item id="chapter-1" href="chapter-1.xhtml" media-type="application/xhtml+xml"/><item id="demo-login" href="interactive/login/index.html" media-type="text/html" properties="gitbook:interactive"/></manifest><spine><itemref idref="chapter-1"/></spine></package>""",
                "OEBPS/chapter-1.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Capítulo uno</title></head><body><p>Antes de la demostración.</p><div data-gitbook-demo="login" data-gitbook-title="Prueba el formulario"></div><p>Después de la demostración.</p></body></html>""",
                "OEBPS/interactive/login/index.html" to "<html><body>Demo</body></html>"
            )
            ZipOutputStream(epub.outputStream().buffered()).use { zip ->
                entries.forEach { (name, value) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(value.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }

            val parsed = EpubParser.parse(epub, File(temp, "image-cache"))
            val blocks = parsed.chapters.flatMap { it.blocks }
            val before = blocks.indexOfFirst { it is ReaderBlock.Paragraph && it.text.contains("Antes") }
            val demo = blocks.indexOfFirst { it is ReaderBlock.InteractiveDemo && it.id == "login" }
            val after = blocks.indexOfFirst { it is ReaderBlock.Paragraph && it.text.contains("Después") }

            assertTrue(before >= 0)
            assertTrue(demo > before)
            assertTrue(after > demo)
        } finally {
            temp.deleteRecursively()
        }
    }
}
