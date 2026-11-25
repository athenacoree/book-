package com.example

import com.example.engine.BookFormat
import com.example.engine.BookMetadataDetector
import com.example.engine.ChapterInput
import com.example.engine.EpubChapter
import com.example.engine.EpubParser
import com.example.engine.EpubWriter
import com.example.engine.InteractiveDemoPackage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import com.example.engine.ReaderBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun formatDetection_mapsPdfMobiEpubCorrectly() {
        assertEquals(BookFormat.PDF, BookFormat.fromFileName("libro.pdf"))
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("novela.mobi"))
        assertEquals(BookFormat.MOBI, BookFormat.fromFileName("kindle.azw"))
        assertEquals(BookFormat.EPUB, BookFormat.fromFileName("apple_book.epub"))
        assertEquals(BookFormat.FB2, BookFormat.fromFileName("cuento.fb2"))
        assertEquals(BookFormat.TXT, BookFormat.fromFileName("notas.txt"))
    }

    @Test
    fun epubWriterAndParser_roundTripPreservesChaptersAndMetadata() {
        val outFile: File = tempFolder.newFile("test_book.epub")
        EpubWriter.writeEpub(
            outputFile = outFile,
            title = "El Viaje Estelar",
            author = "Elena Vargas",
            language = "es",
            chapters = listOf(
                ChapterInput(
                    title = "Capítulo 1: El Despegue",
                    paragraphs = listOf(
                        "La nave iluminó la pista de lanzamiento al amanecer.",
                        "> El horizonte esperaba más allá de las nubes."
                    )
                ),
                ChapterInput(
                    title = "Capítulo 2: Órbita",
                    paragraphs = listOf("Silencio absoluto en la cabina principal.")
                )
            )
        )

        ZipFile(outFile).use { zip ->
            val mimeEntry = zip.getEntry("mimetype")
            assertTrue(mimeEntry != null)
            assertEquals(ZipEntry.STORED, mimeEntry!!.method)
        }

        val parsed = EpubParser.parse(outFile)
        assertEquals("El Viaje Estelar", parsed.title)
        assertEquals("Elena Vargas", parsed.author)
        assertEquals(2, parsed.chapters.size)
        assertEquals("Capítulo 1: El Despegue", parsed.chapters[0].title)
        assertTrue(parsed.chapters[0].blocks.any { it is ReaderBlock.Quote })
        assertTrue(parsed.chapters[1].blocks.any { it is ReaderBlock.Paragraph && it.text.contains("Silencio") })
    }

    @Test
    fun epubParser_extractsInlineImagesInExactPosition() {
        val epubFile = tempFolder.newFile("illustrated.epub")
        val imageCache = tempFolder.newFolder("img_cache")
        val dummyPng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(
                """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Libro Ilustrado</dc:title>
                        <dc:creator>Autor</dc:creator>
                    </metadata>
                    <manifest>
                        <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="img1" href="images/mapa.png" media-type="image/png"/>
                    </manifest>
                    <spine><itemref idref="c1"/></spine>
                </package>""".toByteArray()
            )
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/images/mapa.png"))
            zos.write(dummyPng)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/ch1.xhtml"))
            zos.write(
                """<html xmlns="http://www.w3.org/1999/xhtml"><body>
                    <h1>Capítulo 1</h1>
                    <p>Antes de la imagen.</p>
                    <img src="../images/mapa.png" alt="Mapa del reino"/>
                    <p>Después de la imagen.</p>
                </body></html>""".toByteArray()
            )
            zos.closeEntry()
        }

        val parsed = EpubParser.parse(epubFile, imageCache)
        val blocks = parsed.chapters.first().blocks
        assertEquals(4, blocks.size)
        assertTrue(blocks[0] is ReaderBlock.Heading)
        assertTrue(blocks[1] is ReaderBlock.Paragraph)
        val imgBlock = blocks[2] as ReaderBlock.ImageBlock
        assertEquals("Mapa del reino", imgBlock.caption)
        assertTrue(File(imgBlock.imagePath).exists())
        assertTrue(blocks[3] is ReaderBlock.Paragraph)
        assertEquals(1, parsed.totalImages)
        assertTrue(parsed.wordCount > 0)
    }

    @Test
    fun epubParserRecognizesBundledInteractiveDemoWithoutExtractingIt() {
        val epubFile = tempFolder.newFile("interactive.epub")
        val imageCache = tempFolder.newFolder("interactive_cache")
        createInteractiveDemoEpub(epubFile)

        val parsed = EpubParser.parse(epubFile, imageCache)
        val demo = parsed.chapters.first().blocks.filterIsInstance<ReaderBlock.InteractiveDemo>().single()

        assertEquals("login", demo.id)
        assertEquals("Prueba el formulario", demo.title)
        assertEquals("OEBPS/interactive/login/index.html", demo.entryPath)
        assertEquals(epubFile.absolutePath, demo.sourceEpubPath)
        assertTrue("network" in demo.permissions)
        assertTrue("microphone" in demo.permissions)
        assertFalse(File(imageCache, "interactive_demos").exists())
    }

    @Test
    fun interactiveDemoPackageExtractsOnlyTheVisibleDemosFolder() = runTest {
        val epubFile = tempFolder.newFile("interactive_extract.epub")
        val cacheDir = tempFolder.newFolder("demo_extract_cache")
        createInteractiveDemoEpub(epubFile)

        val extracted = InteractiveDemoPackage.extract(
            sourceEpub = epubFile,
            entryPath = "OEBPS/interactive/login/index.html",
            cacheDir = cacheDir,
            demoId = "login"
        )

        assertNotNull(extracted)
        assertTrue(extracted!!.entryFile.isFile)
        assertTrue(File(extracted.rootDir, "app.js").isFile)
        assertTrue(File(extracted.rootDir, "styles.css").isFile)
        assertFalse(File(extracted.rootDir, "not-this-demo.txt").exists())
        assertTrue(extracted.entryFile.readText().contains("Content-Security-Policy"))
    }

    private fun createInteractiveDemoEpub(epubFile: File) {
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            fun add(path: String, content: String) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>"""
            )
            add(
                "OEBPS/content.opf",
                """<package xmlns="http://www.idpf.org/2007/opf" version="3.0"
                    prefix="gitbook: https://gitbook.app/ns/epub#">
                    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Manual interactivo</dc:title><dc:creator>GITBOOK</dc:creator>
                        <meta property="gitbook:permissions">network microphone</meta>
                    </metadata>
                    <manifest>
                        <item id="chapter1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="demo-login" href="interactive/login/index.html"
                            media-type="text/html" properties="gitbook:interactive"/>
                    </manifest>
                    <spine><itemref idref="chapter1"/></spine>
                </package>"""
            )
            add(
                "OEBPS/text/ch1.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml"><body>
                    <h1>Formulario de prueba</h1>
                    <p>Antes de probarlo.</p>
                    <div data-gitbook-demo="login" data-gitbook-title="Prueba el formulario"></div>
                    <p>Después de probarlo.</p>
                </body></html>"""
            )
            add(
                "OEBPS/interactive/login/index.html",
                """<!doctype html><html><head><title>Demo</title><link rel="stylesheet" href="styles.css"></head>
                    <body><button id="go">Probar</button><script src="app.js"></script></body></html>"""
            )
            add("OEBPS/interactive/login/styles.css", "body { padding: 12px; }")
            add("OEBPS/interactive/login/app.js", "document.getElementById('go').textContent = 'Funciona';")
            add("OEBPS/interactive/other/not-this-demo.txt", "No copiar este recurso")
        }
    }

    @Test
    fun metadataDetection_detectsSpanishEnglishAndGenreCorrectly() {
        val spanishChapters = listOf(
            EpubChapter(
                index = 0,
                title = "Capítulo Uno",
                blocks = listOf(
                    ReaderBlock.Paragraph(
                        "En un lugar de la Mancha, el viento soplaba sobre los caminos de piedra y las nubes cubrían la sierra desde la mañana hasta el atardecer, pero el viajero no se detuvo porque su esperanza era más fuerte que la fatiga."
                    )
                )
            )
        )
        val englishChapters = listOf(
            EpubChapter(
                index = 0,
                title = "Chapter One",
                blocks = listOf(
                    ReaderBlock.Paragraph(
                        "In the quiet village near the river, the traveler walked through the autumn mist with a lantern in his hand, and he thought about all the stories that were written before the storm arrived."
                    )
                )
            )
        )

        assertEquals("Español", BookMetadataDetector.detectLanguage(null, spanishChapters))
        assertEquals("Inglés", BookMetadataDetector.detectLanguage(null, englishChapters))
        assertEquals(
            "Ciencia ficción",
            BookMetadataDetector.resolveGenre(listOf("Science fiction -- Classics"), "Dune", englishChapters)
        )
        val stats = BookMetadataDetector.computeStats(spanishChapters)
        assertTrue(stats.wordCount >= 35)
        assertTrue(stats.estimatedPages >= 1)
    }
}
