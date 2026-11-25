package com.example.engine

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
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
