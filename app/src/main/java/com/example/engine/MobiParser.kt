package com.example.engine

import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

/**
 * Pure Kotlin PalmDB / PalmDOC / MOBI (.mobi, .prc, .azw) parser and PalmDOC LZ77 decompressor.
 * Extracts book title, author (EXTH), and HTML content, then splits it into clean chapters
 * ready to be converted into standard EPUB.
 */
object MobiParser {

    data class MobiContent(
        val title: String,
        val author: String,
        val chapters: List<ChapterInput>
    )

    fun parse(bytes: ByteArray, fallbackTitle: String): MobiContent {
        if (bytes.size < 84) throw IllegalArgumentException("Archivo MOBI demasiado pequeño o corrupto")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // PalmDB Name (0..31)
        val pdbTitle = String(bytes, 0, 32, Charsets.ISO_8859_1).trim('\u0000', ' ')
        val numRecords = buffer.getShort(76).toInt() and 0xFFFF
        if (numRecords < 2) throw IllegalArgumentException("El archivo MOBI no contiene registros válidos")

        val recordOffsets = IntArray(numRecords)
        for (i in 0 until numRecords) {
            recordOffsets[i] = buffer.getInt(78 + i * 8)
        }

        fun getRecordBytes(index: Int): ByteArray {
            val start = recordOffsets[index]
            val end = if (index + 1 < numRecords) recordOffsets[index + 1] else bytes.size
            if (start < 0 || end > bytes.size || start >= end) return ByteArray(0)
            return bytes.copyOfRange(start, end)
        }

        // Record 0: PalmDOC + MOBI Header
        val rec0 = getRecordBytes(0)
        if (rec0.size < 32) throw IllegalArgumentException("Cabecera PalmDOC inválida")
        val rec0Buf = ByteBuffer.wrap(rec0).order(ByteOrder.BIG_ENDIAN)

        val compression = rec0Buf.getShort(0).toInt() and 0xFFFF
        // 1 = no compression, 2 = PalmDOC LZ77, 17480 = HUFF/CDIC
        if (compression == 17480) {
            throw IllegalArgumentException("Este archivo MOBI usa compresión HUFF/CDIC protegida no compatible")
        }

        val textRecordCount = rec0Buf.getShort(8).toInt() and 0xFFFF
        var title = pdbTitle.ifBlank { fallbackTitle }
        var author = "Autor desconocido"
        var charset: Charset = Charset.forName("windows-1252")

        // Check for "MOBI" magic at offset 16
        if (rec0.size >= 132 && String(rec0, 16, 4, Charsets.US_ASCII) == "MOBI") {
            val mobiHeaderLen = rec0Buf.getInt(20)
            val textEncoding = rec0Buf.getInt(28)
            if (textEncoding == 65001) {
                charset = Charsets.UTF_8
            }

            val fullNameOffset = rec0Buf.getInt(84)
            val fullNameLength = rec0Buf.getInt(88)
            if (fullNameOffset > 0 && fullNameLength > 0 && fullNameOffset + fullNameLength <= rec0.size) {
                val extractedName = String(rec0, fullNameOffset, fullNameLength, charset).trim('\u0000', ' ')
                if (extractedName.isNotBlank()) title = extractedName
            }

            // Parse EXTH header if flag 0x40 is set at offset 128
            val exthFlags = rec0Buf.getInt(128)
            val exthStart = 16 + mobiHeaderLen
            if ((exthFlags and 0x40) != 0 && exthStart + 12 <= rec0.size) {
                if (String(rec0, exthStart, 4, Charsets.US_ASCII) == "EXTH") {
                    val exthCount = rec0Buf.getInt(exthStart + 8)
                    var pos = exthStart + 12
                    for (i in 0 until exthCount) {
                        if (pos + 8 > rec0.size) break
                        val recType = rec0Buf.getInt(pos)
                        val recLen = rec0Buf.getInt(pos + 4)
                        if (recLen < 8 || pos + recLen > rec0.size) break
                        val valBytes = rec0.copyOfRange(pos + 8, pos + recLen)
                        val strVal = String(valBytes, charset).trim('\u0000', ' ')
                        when (recType) {
                            100 -> if (strVal.isNotBlank()) author = strVal // Author
                            503 -> if (strVal.isNotBlank()) title = strVal  // Updated Title
                        }
                        pos += recLen
                    }
                }
            }
        }

        // Decompress text records (1 .. textRecordCount)
        val rawHtmlStream = ByteArrayOutputStream()
        val maxTextRec = minOf(textRecordCount, numRecords - 1)
        for (i in 1..maxTextRec) {
            val recBytes = getRecordBytes(i)
            val decompressed = when (compression) {
                1 -> recBytes
                2 -> decompressPalmDoc(recBytes)
                else -> recBytes
            }
            rawHtmlStream.write(decompressed)
        }

        val rawText = rawHtmlStream.toByteArray().toString(charset)
        val chapters = htmlToChapters(rawText, title)

        return MobiContent(
            title = title,
            author = author,
            chapters = chapters
        )
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 2)
        var i = 0
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            i++
            when {
                b == 0 -> out.write(0)
                b in 1..8 -> {
                    val count = minOf(b, data.size - i)
                    out.write(data, i, count)
                    i += count
                }
                b in 9..0x7F -> out.write(b)
                b in 0x80..0xBF -> {
                    if (i >= data.size) break
                    val next = data[i].toInt() and 0xFF
                    i++
                    val combined = (b shl 8) or next
                    val distance = (combined shr 3) and 0x7FF
                    val length = (combined and 0x07) + 3
                    val currentBytes = out.toByteArray()
                    val startPos = currentBytes.size - distance
                    if (distance > 0 && startPos >= 0) {
                        for (k in 0 until length) {
                            val idx = startPos + k
                            if (idx < currentBytes.size) {
                                out.write(currentBytes[idx].toInt())
                            } else {
                                // Overlapping copy inside newly written bytes
                                val temp = out.toByteArray()
                                val overlapIdx = temp.size - distance
                                if (overlapIdx in temp.indices) {
                                    out.write(temp[overlapIdx].toInt())
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 0xC0..0xFF: space followed by (b xor 0x80)
                    out.write(' '.code)
                    out.write(b xor 0x80)
                }
            }
        }
        return out.toByteArray()
    }

    private fun htmlToChapters(rawHtml: String, fallbackTitle: String): List<ChapterInput> {
        val normalized = rawHtml
            .replace(Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE), "\n<!--PAGEBREAK-->\n")

        val parts = if (normalized.contains("<!--PAGEBREAK-->")) {
            normalized.split("<!--PAGEBREAK-->")
        } else {
            listOf(normalized)
        }

        val chapters = mutableListOf<ChapterInput>()
        parts.forEachIndexed { idx, part ->
            val doc = Jsoup.parse(part)
            val headings = mutableMapOf<Int, String>()
            val paragraphs = mutableListOf<String>()
            var chapterTitle: String? = null

            doc.body().select("h1, h2, h3, p, blockquote, div").forEach { el ->
                val tag = el.tagName().lowercase()
                if (tag == "div" && el.children().any { it.tagName().lowercase() in setOf("p", "h1", "h2", "h3", "div") }) {
                    return@forEach
                }
                val text = el.text().trim()
                if (text.isEmpty()) return@forEach

                if (tag == "h1" || tag == "h2") {
                    if (chapterTitle == null && paragraphs.isEmpty()) {
                        chapterTitle = text
                    } else {
                        headings[paragraphs.size] = text
                    }
                } else if (tag == "blockquote") {
                    paragraphs.add("> $text")
                } else {
                    paragraphs.add(text)
                }
            }

            if (paragraphs.isEmpty()) {
                val plain = doc.body().text().trim()
                if (plain.isNotEmpty()) {
                    plain.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        paragraphs.add(it)
                    }
                }
            }

            if (paragraphs.isNotEmpty()) {
                chapters.add(
                    ChapterInput(
                        title = chapterTitle ?: if (chapters.isEmpty()) fallbackTitle else "Capítulo ${chapters.size + 1}",
                        paragraphs = paragraphs,
                        headings = headings
                    )
                )
            }
        }

        // If the whole book was a single massive section, split cleanly into readable chapters
        if (chapters.size == 1 && chapters[0].paragraphs.size > 60) {
            val single = chapters[0]
            val chunked = single.paragraphs.chunked(45)
            return chunked.mapIndexed { idx, chunk ->
                ChapterInput(
                    title = if (idx == 0) single.title else "Sección ${idx + 1}",
                    paragraphs = chunk
                )
            }
        }

        return chapters.ifEmpty {
            listOf(ChapterInput(fallbackTitle, listOf("No se encontró texto legible en el archivo MOBI.")))
        }
    }
}
