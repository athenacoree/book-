package com.example.engine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveDemoPackageTest {

    @Test
    fun extractsOnlyTheRequestedDemoFolderAndAddsOfflinePolicy() = runBlocking {
        val temp = Files.createTempDirectory("gitbook-demo-test").toFile()
        try {
            val epub = File(temp, "sample.epub")
            writeZip(
                epub,
                mapOf(
                    "OEBPS/interactive/login/index.html" to
                        "<html><head></head><body><button>Try it</button><script src='app.js'></script></body></html>",
                    "OEBPS/interactive/login/app.js" to "document.body.dataset.ready = 'yes';",
                    "OEBPS/interactive/login/styles.css" to "body { color: black; }",
                    "OEBPS/interactive/other/private.txt" to "not part of this demo"
                )
            )

            val result = InteractiveDemoPackage.extract(
                sourceEpub = epub,
                entryPath = "OEBPS/interactive/login/index.html",
                cacheDir = File(temp, "cache"),
                demoId = "login"
            )

            assertNotNull(result)
            val bundle = requireNotNull(result)
            assertTrue(bundle.entryFile.isFile)
            assertTrue(File(bundle.rootDir, "app.js").isFile)
            assertTrue(File(bundle.rootDir, "styles.css").isFile)
            assertFalse(File(bundle.rootDir, "private.txt").exists())
            assertTrue(bundle.entryFile.readText().contains("Content-Security-Policy"))
            assertTrue(File(bundle.rootDir, ".gitbook-demo-complete").isFile)
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun rejectsTraversalPathsBeforeExtracting() = runBlocking {
        val temp = Files.createTempDirectory("gitbook-demo-path-test").toFile()
        try {
            val epub = File(temp, "sample.epub")
            writeZip(epub, mapOf("OEBPS/interactive/login/index.html" to "<html></html>"))

            val result = InteractiveDemoPackage.extract(
                sourceEpub = epub,
                entryPath = "OEBPS/interactive/../outside/index.html",
                cacheDir = File(temp, "cache"),
                demoId = "login"
            )

            assertNull(result)
            assertFalse(File(temp, "cache").exists())
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test
    fun epubParserPlacesDemoAtMarkerAndRecognizesNamespacedManifestProperty() {
        val temp = Files.createTempDirectory("gitbook-demo-parser-test").toFile()
        try {
            val epub = File(temp, "book.epub")
            writeZip(
                epub,
                linkedMapOf(
                    "mimetype" to "application/epub+zip",
                    "META-INF/container.xml" to """
                        <?xml version="1.0"?>
                        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                          <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
                        </container>
                    """.trimIndent(),
                    "OEBPS/content.opf" to """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf"
                            xmlns:dc="http://purl.org/dc/elements/1.1/"
                            version="3.0"
                            prefix="gitbook: https://gitbook.app/ns/epub#">
                          <metadata><dc:title>Libro de prueba</dc:title></metadata>
                          <manifest>
                            <item id="chapter-1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                            <item id="demo-login" href="interactive/login/index.html"
                                media-type="text/html" properties="gitbook:interactive"/>
                          </manifest>
                          <spine><itemref idref="chapter-1"/></spine>
                        </package>
                    """.trimIndent(),
                    "OEBPS/chapter.xhtml" to """
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>Capítulo de prueba</title></head>
                          <body>
                            <p>Texto anterior.</p>
                            <div data-gitbook-demo="login" data-gitbook-title="Prueba el formulario"></div>
                            <p>Texto posterior.</p>
                          </body>
                        </html>
                    """.trimIndent(),
                    "OEBPS/interactive/login/index.html" to "<html><body>Demo</body></html>"
                )
            )

            val parsed = EpubParser.parse(epub, File(temp, "book-cache"))
            val blocks = parsed.chapters.single().blocks

            assertEquals(3, blocks.size)
            assertEquals("Texto anterior.", (blocks[0] as ReaderBlock.Paragraph).text)
            val demo = blocks[1] as ReaderBlock.InteractiveDemo
            assertEquals("login", demo.id)
            assertEquals("Prueba el formulario", demo.title)
            assertEquals("OEBPS/interactive/login/index.html", demo.entryPath)
            assertEquals("Texto posterior.", (blocks[2] as ReaderBlock.Paragraph).text)
            assertTrue(demo.cacheDirPath.isNotBlank())
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun writeZip(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (path, value) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }
}
