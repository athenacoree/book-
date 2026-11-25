package com.example.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.engine.ConversionProgress
import com.example.engine.ConvertedBookResult
import com.example.engine.EpubParser
import com.example.engine.ParsedEpub
import com.example.engine.UniversalBookConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

class BookRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = BookDatabase.getInstance(appContext).bookDao()
    private val converter = UniversalBookConverter(appContext)
    private val onlineCatalog = OnlineCatalogService()
    val preferencesRepository = ReaderPreferencesRepository(appContext)
    val networkMonitor = NetworkMonitor(appContext)

    val allBooks: Flow<List<BookEntity>> = dao.observeAllBooks()
    val convertedBooks: Flow<List<BookEntity>> = dao.observeConvertedBooks()

    suspend fun importAndConvertBook(
        uri: Uri,
        onProgress: (ConversionProgress) -> Unit = {}
    ): BookEntity {
        val result = converter.importAndConvert(uri, onProgress)
        val entity = result.toEntity()
        dao.insertBook(entity)
        return entity
    }

    suspend fun getBookById(bookId: String): BookEntity? = dao.getBookById(bookId)

    suspend fun saveReaderSettings(bookId: String, settingsJson: String) = dao.updateReaderSettings(bookId, settingsJson)

    suspend fun loadParsedEpub(book: BookEntity): ParsedEpub = withContext(Dispatchers.IO) {
        val sourceFile = File(book.epubPath)
        require(sourceFile.isFile && sourceFile.canRead()) { "El archivo del libro no está disponible" }
        val imageCacheDir = File(appContext.cacheDir, "epub_images/${book.id}")
        EpubParser.parse(sourceFile, imageCacheDir)
    }

    suspend fun updateReadingProgress(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int,
        progressPercent: Int
    ) {
        dao.updateProgress(
            bookId = bookId,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            progressPercent = progressPercent.coerceIn(0, 100),
            lastRead = System.currentTimeMillis()
        )
    }

    suspend fun toggleFavorite(book: BookEntity) {
        dao.setFavorite(book.id, !book.isFavorite)
    }

    suspend fun updateBookGenre(bookId: String, newGenre: String) {
        val cleaned = newGenre.trim().ifBlank { "Lectura general" }.take(36)
        dao.updateGenre(bookId, cleaned)
    }

    suspend fun saveHtmlGameState(
        bookId: String,
        rawSaveJson: String,
        explicitRecord: String? = null
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val validJson = runCatching { JSONObject(rawSaveJson).toString() }.getOrElse { "{}" }
        val existing = dao.getBookById(bookId)
        val derivedRecord = explicitRecord?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractBestRecordFromJson(validJson).ifBlank { existing?.gameBestRecord.orEmpty() }
        dao.updateGameSaveData(
            bookId = bookId,
            saveJson = validJson,
            bestRecord = derivedRecord.take(40),
            lastRead = System.currentTimeMillis()
        )
        validJson to derivedRecord.take(40)
    }

    suspend fun clearHtmlGameSaveData(bookId: String) = withContext(Dispatchers.IO) {
        dao.updateGameSaveData(
            bookId = bookId,
            saveJson = "{}",
            bestRecord = "",
            lastRead = System.currentTimeMillis()
        )
    }

    private fun extractBestRecordFromJson(jsonStr: String): String {
        val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return ""
        val keys = obj.keys().asSequence().toList()
        val priorityMatchers = listOf(
            "best", "record", "high_score", "highscore", "maxscore", "récord", "pts", "score", "puntos"
        )
        for (matcher in priorityMatchers) {
            val matchKey = keys.firstOrNull { it.lowercase().contains(matcher) } ?: continue
            val value = obj.optString(matchKey, "").trim()
            if (value.isNotEmpty() && value != "0" && value != "null") {
                return if (value.all { it.isDigit() }) "$value pts" else value.take(28)
            }
        }
        val levelKey = keys.firstOrNull {
            val lower = it.lowercase()
            lower.contains("level") || lower.contains("nivel") || lower.contains("stage")
        }
        if (levelKey != null) {
            val value = obj.optString(levelKey, "").trim()
            if (value.isNotEmpty() && value != "null") {
                return if (value.all { it.isDigit() }) "Nivel $value" else value.take(28)
            }
        }
        return ""
    }

    suspend fun syncBookMetadataIfNeeded(book: BookEntity, parsed: ParsedEpub) = withContext(Dispatchers.IO) {
        if (book.isHtmlGame) return@withContext
        if (book.wordCount == 0 && parsed.wordCount > 0) {
            dao.updateBookMetadata(
                bookId = book.id,
                language = parsed.language,
                genre = if (book.genre == "Lectura general" || book.genre.isBlank()) parsed.genre else book.genre,
                publisher = book.publisher.ifBlank { parsed.publisher },
                publicationYear = book.publicationYear.ifBlank { parsed.publicationYear },
                description = book.description.ifBlank { parsed.description },
                wordCount = parsed.wordCount,
                estimatedPages = parsed.estimatedPages,
                totalImages = parsed.totalImages
            )
        }
    }

    suspend fun deleteBook(book: BookEntity) = withContext(Dispatchers.IO) {
        dao.deleteBookmarksForBook(book.id)
        dao.deleteBookById(book.id)
        File(appContext.cacheDir, "epub_images/" + book.id).deleteRecursively()
        converter.deleteBookFiles(book.epubPath, book.originalPath, book.coverPath)
    }

    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>> = dao.observeBookmarks(bookId)

    suspend fun addBookmark(
        bookId: String,
        chapterIndex: Int,
        chapterTitle: String,
        pageIndex: Int,
        excerpt: String
    ) {
        dao.insertBookmark(
            BookmarkEntity(
                bookId = bookId,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                pageIndex = pageIndex,
                excerpt = excerpt.take(120)
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        dao.deleteBookmark(bookmarkId)
    }

    suspend fun searchOnlineCatalog(
        query: String?,
        languageCode: String?,
        topic: String?
    ): List<GutendexBook> {
        return onlineCatalog.fetchBooks(query, languageCode, topic)
    }

    suspend fun searchOnlineGames(query: String? = null): List<SupabaseCatalogItem> {
        return onlineCatalog.fetchGames(query).filter {
            it.fileFormat.equals("html", ignoreCase = true)
        }
    }

    suspend fun downloadCatalogBook(item: SupabaseCatalogItem): BookEntity = withContext(Dispatchers.IO) {
        require(item.itemType.equals("book", ignoreCase = true)) { "El elemento seleccionado no es un libro" }
        val extension = item.fileFormat.lowercase()
        require(extension in setOf("epub", "pdf", "mobi", "fb2", "txt", "html")) {
            "Formato de libro no compatible: $extension"
        }
        val bytes = onlineCatalog.downloadCatalogAsset(item.storagePath)
        val downloadDir = File(appContext.cacheDir, "catalog_downloads").apply { mkdirs() }
        val downloadedFile = File(downloadDir, "${item.id}.$extension")
        try {
            downloadedFile.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                downloadedFile
            )
            val converted = converter.importAndConvert(uri)
            val entity = converted.toEntity()
            dao.insertBook(entity)
            entity
        } finally {
            downloadedFile.delete()
        }
    }

    suspend fun downloadOnlineGame(item: SupabaseCatalogItem): BookEntity = withContext(Dispatchers.IO) {
        require(item.itemType.equals("game", ignoreCase = true)) {
            "El elemento seleccionado no es un juego"
        }
        require(item.fileFormat.lowercase() in setOf("html", "zip")) {
            "Formato de juego no compatible: ${item.fileFormat}"
        }
        require(isVersionAtLeast(BuildConfig.VERSION_NAME, item.minimumAppVersion)) {
            "Este juego necesita una versión más reciente de GITBOOK."
        }
        val localId = "lbg-${item.id}"
        val installed = dao.getBookById(localId)
        if (installed != null && installed.gameVersion == item.gameVersion) return@withContext installed

        val bytes = onlineCatalog.downloadCatalogAsset(item.storagePath)
        item.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            require(actual.equals(expected.trim(), ignoreCase = true)) {
                "La comprobación de integridad del paquete no coincide. No se instaló."
            }
        }
        val result = converter.saveDownloadedGamePackage(
            bookId = localId,
            title = item.title,
            author = item.author,
            packageBytes = bytes,
            fileFormat = item.fileFormat,
            entryFile = item.packageEntry,
            language = item.languageCode,
            subjects = item.subjects
        )
        val entity = result.toEntity().copy(
            gameSaveJson = installed?.gameSaveJson ?: "{}",
            gameBestRecord = installed?.gameBestRecord ?: "",
            gameVersion = item.gameVersion,
            catalogGameId = item.id,
            gamePackageEntry = "index.html",
            addedTimestamp = installed?.addedTimestamp ?: System.currentTimeMillis(),
            isFavorite = installed?.isFavorite ?: false,
            currentChapterIndex = installed?.currentChapterIndex ?: 0,
            currentPageInChapter = installed?.currentPageInChapter ?: 0,
            progressPercent = installed?.progressPercent ?: 0
        )
        dao.insertBook(entity)
        entity
    }

    private fun isVersionAtLeast(current: String, minimum: String): Boolean {
        fun parts(value: String) = value.split(".", "-", "+").take(4)
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(current)
        val b = parts(minimum)
        for (index in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return true
    }

    suspend fun downloadOnlineBook(book: GutendexBook): BookEntity {
        val localId = "gut-${book.id}"
        dao.getBookById(localId)?.let { return it }
        val epubUrl = book.epubDownloadUrl
            ?: throw IllegalStateException("No hay descarga EPUB disponible")
        val epubBytes = onlineCatalog.downloadBytes(epubUrl)
        val coverBytes = book.coverImageUrl?.let { url ->
            try {
                onlineCatalog.downloadBytes(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        val result = converter.saveDownloadedEpub(
            bookId = localId,
            title = book.title,
            author = book.authorDisplay,
            epubBytes = epubBytes,
            coverBytes = coverBytes,
            hintLanguage = book.languages.firstOrNull(),
            hintSubjects = book.subjects
        )
        val entity = result.toEntity()
        dao.insertBook(entity)
        return entity
    }

    private fun ConvertedBookResult.toEntity(): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = author,
            originalFormat = originalFormat.label,
            epubPath = epubPath,
            originalPath = originalPath,
            coverPath = coverPath,
            totalChapters = totalChapters,
            isConvertedFromOtherFormat = isConvertedFromOtherFormat,
            fileSizeBytes = fileSizeBytes,
            language = language,
            genre = genre,
            publisher = publisher,
            publicationYear = publicationYear,
            description = description,
            wordCount = wordCount,
            estimatedPages = estimatedPages,
            totalImages = totalImages
        )
    }
}
