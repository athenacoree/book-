package com.example.engine

data class ExtractedBookStats(
    val wordCount: Int,
    val estimatedPages: Int,
    val totalImages: Int
)

/**
 * Offline language detection, genre normalization, and structural stats extraction for books.
 */
object BookMetadataDetector {

    private val WORD_REGEX = Regex("\\p{L}+")

    private val SPANISH_STOPWORDS = setOf(
        "el", "la", "los", "las", "de", "del", "que", "y", "en", "un", "una", "por",
        "con", "para", "su", "es", "al", "se", "lo", "no", "como", "más", "pero",
        "sus", "le", "ya", "o", "este", "sí", "porque", "esta", "entre", "cuando",
        "muy", "sin", "sobre", "también", "me", "hasta", "hay", "donde", "quien",
        "desde", "todo", "nos", "durante", "había", "era", "fueron", "dijo"
    )

    private val ENGLISH_STOPWORDS = setOf(
        "the", "and", "of", "to", "in", "is", "that", "with", "for", "as", "was",
        "it", "on", "are", "be", "by", "at", "from", "this", "had", "not", "but",
        "have", "or", "which", "an", "they", "you", "were", "their", "one", "all",
        "would", "there", "her", "his", "she", "he", "said", "what", "when", "into"
    )

    private val FRENCH_STOPWORDS = setOf(
        "le", "la", "les", "de", "des", "du", "et", "est", "un", "une", "dans",
        "pour", "pas", "qui", "que", "il", "elle", "nous", "vous", "sur", "avec",
        "au", "aux", "ce", "cette", "mais", "ou", "par", "plus", "son", "sa", "ses"
    )

    private val PORTUGUESE_STOPWORDS = setOf(
        "o", "a", "os", "as", "de", "do", "da", "dos", "das", "que", "e", "em",
        "um", "uma", "não", "para", "com", "por", "se", "na", "no", "mas", "como",
        "mais", "foi", "ele", "ela", "seu", "sua", "dos", "entre", "quando", "muito"
    )

    private val ITALIAN_STOPWORDS = setOf(
        "il", "lo", "la", "i", "gli", "le", "di", "che", "e", "un", "una", "in",
        "per", "con", "non", "sono", "da", "su", "del", "della", "dei", "come",
        "ma", "più", "anche", "questo", "quello", "era", "aveva"
    )

    private val GERMAN_STOPWORDS = setOf(
        "der", "die", "das", "und", "in", "zu", "den", "von", "mit", "ist", "ein",
        "eine", "nicht", "auf", "für", "sich", "als", "auch", "es", "an", "dem",
        "des", "war", "werden", "aus", "er", "sie", "ich", "noch"
    )

    fun extractSampleText(chapters: List<EpubChapter>, maxChars: Int = 8000): String {
        val sb = StringBuilder()
        for (ch in chapters) {
            for (b in ch.blocks) {
                val t = when (b) {
                    is ReaderBlock.Heading -> b.text
                    is ReaderBlock.Paragraph -> b.text
                    is ReaderBlock.Quote -> b.text
                    else -> ""
                }
                if (t.isNotBlank()) {
                    sb.append(t).append(' ')
                    if (sb.length >= maxChars) return sb.toString()
                }
            }
        }
        return sb.toString()
    }

    fun detectLanguage(declaredCode: String?, chapters: List<EpubChapter>): String {
        val sample = extractSampleText(chapters)
        val tokens = WORD_REGEX.findAll(sample.lowercase())
            .take(600)
            .map { it.value }
            .toList()

        if (tokens.size >= 15) {
            val esScore = tokens.count { it in SPANISH_STOPWORDS } +
                sample.count { it in "ñáéíóú¿¡" } * 2
            val enScore = tokens.count { it in ENGLISH_STOPWORDS }
            val frScore = tokens.count { it in FRENCH_STOPWORDS } +
                sample.count { it in "àâçèêëîïôùûüœ" }
            val ptScore = tokens.count { it in PORTUGUESE_STOPWORDS } +
                sample.count { it in "ãõçâêô" } * 2
            val itScore = tokens.count { it in ITALIAN_STOPWORDS } +
                sample.count { it in "àèìòù" }
            val deScore = tokens.count { it in GERMAN_STOPWORDS } +
                sample.count { it in "äöüß" } * 2

            val scores = listOf(
                "Español" to esScore,
                "Inglés" to enScore,
                "Francés" to frScore,
                "Portugués" to ptScore,
                "Italiano" to itScore,
                "Alemán" to deScore
            )
            val best = scores.maxByOrNull { it.second }
            if (best != null && best.second >= 6) {
                return best.first
            }
        }

        return mapLanguageCode(declaredCode)
    }

    fun mapLanguageCode(rawCode: String?): String {
        val code = rawCode.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')
        return when (code) {
            "es", "spa" -> "Español"
            "en", "eng" -> "Inglés"
            "fr", "fra", "fre" -> "Francés"
            "pt", "por" -> "Portugués"
            "it", "ita" -> "Italiano"
            "de", "deu", "ger" -> "Alemán"
            "ca", "cat" -> "Catalán"
            "la", "lat" -> "Latín"
            else -> if (code.isNotBlank()) code.uppercase() else "Español"
        }
    }

    fun resolveGenre(declaredSubjects: List<String>, title: String, chapters: List<EpubChapter>): String {
        val combined = (declaredSubjects + title).joinToString(" ").lowercase()
        return when {
            combined.containsAny("science fiction", "sci-fi", "ciencia ficción") -> "Ciencia ficción"
            combined.containsAny("fantasy", "fantasía", "magic", "dragon") -> "Fantasía"
            combined.containsAny("mystery", "detective", "thriller", "misterio", "crime") -> "Misterio"
            combined.containsAny("romance", "love", "amor") -> "Romance"
            combined.containsAny("adventure", "aventura", "voyage", "viaje") -> "Aventura"
            combined.containsAny("history", "historia", "biograph", "biografía", "war") -> "Historia"
            combined.containsAny("philosophy", "filosofía", "essay", "ensayo", "ethics") -> "Filosofía y ensayo"
            combined.containsAny("poetry", "poesía", "poems", "verso") -> "Poesía"
            combined.containsAny("science", "physics", "math", "ciencia", "tecnología", "programming", "software") -> "Ciencia y técnica"
            combined.containsAny("horror", "terror", "gothic") -> "Terror"
            declaredSubjects.firstOrNull()?.isNotBlank() == true -> {
                declaredSubjects.first()
                    .substringBefore("--")
                    .trim()
                    .replaceFirstChar { it.uppercase() }
                    .take(28)
            }
            else -> {
                val sample = extractSampleText(chapters, 2000).lowercase()
                when {
                    sample.containsAny("capítulo", "chapter") -> "Novela y ficción"
                    else -> "Lectura general"
                }
            }
        }
    }

    fun computeStats(chapters: List<EpubChapter>): ExtractedBookStats {
        var words = 0
        var images = 0
        chapters.forEach { ch ->
            ch.blocks.forEach { block ->
                when (block) {
                    is ReaderBlock.Heading -> words += WORD_REGEX.findAll(block.text).count()
                    is ReaderBlock.Paragraph -> words += WORD_REGEX.findAll(block.text).count()
                    is ReaderBlock.Quote -> words += WORD_REGEX.findAll(block.text).count()
                    is ReaderBlock.ImageBlock -> images++
                    is ReaderBlock.InteractiveDemo, is ReaderBlock.YouTubeVideo, ReaderBlock.Divider -> Unit
                }
            }
        }
        val estPages = ((words / 240.0) + images * 0.6).toInt().coerceAtLeast(1)
        return ExtractedBookStats(
            wordCount = words,
            estimatedPages = estPages,
            totalImages = images
        )
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it, ignoreCase = true) }
}
