package com.example.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipFile

/**
 * Extracts exactly one publisher-supplied demo folder when the reader reaches it.
 * No HTML bundle is unpacked while the EPUB is initially parsed.
 */
object InteractiveDemoPackage {
    data class ExtractedBundle(
        val entryFile: File,
        val rootDir: File
    )

    suspend fun extract(
        sourceEpub: File,
        entryPath: String,
        cacheDir: File,
        demoId: String
    ): ExtractedBundle? = withContext(Dispatchers.IO) {
        if (!sourceEpub.isFile || !sourceEpub.canRead()) return@withContext null
        if (demoId.isBlank() || entryPath.isBlank() || entryPath.startsWith("/") ||
            entryPath.contains('\\') || entryPath.split('/').any { it.isBlank() || it == ".." }
        ) return@withContext null

        val safeId = demoId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        if (safeId.isBlank()) return@withContext null
        val folder = entryPath.substringBeforeLast('/', "")
        if (folder.isBlank()) return@withContext null
        val cacheKey = "${safeId}_${sourceEpub.lastModified()}_${sourceEpub.length()}"
        val root = File(cacheDir, cacheKey)
        val entryFile = File(root, entryPath.substringAfterLast('/'))

        if (entryFile.isFile) {
            return@withContext ExtractedBundle(entryFile, root)
        }

        root.deleteRecursively()
        root.mkdirs()
        try {
            ZipFile(sourceEpub).use { zip ->
                val prefix = "$folder/"
                var totalBytes = 0L
                var copiedFiles = 0

                val demoEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith(prefix) }
                    .toList()
                for (entry in demoEntries) {
                    currentCoroutineContext().ensureActive()
                    val relative = entry.name.removePrefix(prefix)
                    if (relative.isBlank() || relative.startsWith("/") ||
                        relative.split('/').any { it.isBlank() || it == ".." }
                    ) continue

                    copiedFiles++
                    require(copiedFiles <= MAX_FILES) { "La demostración contiene demasiados archivos" }

                    val outputFile = File(root, relative)
                    val canonicalRoot = root.canonicalFile
                    val canonicalOutput = outputFile.canonicalFile
                    require(canonicalOutput.path.startsWith(canonicalRoot.path + File.separator)) {
                        "La demostración contiene una ruta no permitida"
                    }
                    outputFile.parentFile?.mkdirs()

                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().buffered().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var entryBytes = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                entryBytes += count
                                totalBytes += count
                                require(entryBytes <= MAX_ENTRY_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                    "La demostración supera el tamaño permitido"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
            }

            if (!entryFile.isFile) throw IllegalArgumentException("No se encontró el HTML principal")
            currentCoroutineContext().ensureActive()

            val document = Jsoup.parse(entryFile, "UTF-8")
            val csp = "default-src 'self' data: blob:; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'self'"
            document.head().prependElement("meta")
                .attr("http-equiv", "Content-Security-Policy")
                .attr("content", csp)
            entryFile.writeText(document.outerHtml(), Charsets.UTF_8)

            ExtractedBundle(entryFile, root)
        } catch (cancelled: CancellationException) {
            root.deleteRecursively()
            throw cancelled
        } catch (_: Exception) {
            root.deleteRecursively()
            null
        }
    }

    private const val MAX_FILES = 180
    private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 48L * 1024L * 1024L
}
