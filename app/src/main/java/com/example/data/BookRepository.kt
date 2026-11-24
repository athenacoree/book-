package com.example.data

import android.content.Context
import android.net.Uri
import com.example.engine.ConversionProgress
import com.example.engine.ConvertedBookResult
import com.example.engine.EpubParser
import com.example.engine.ParsedEpub
import com.example.engine.UniversalBookConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = BookDatabase.getInstance(appContext).bookDao()
    private val converter = UniversalBookConverter(appContext)
    private val onlineCatalog = OnlineCatalogService()
    val preferencesRepository = ReaderPreferencesRepository(appContext)
    val networkMonitor = NetworkMonitor(appContext)

    val allBooks: Flow<List<BookEntity>> = dao.observeAllBooks()
    val convertedBooks: Flow<List<BookEntity>> = dao.observeConvertedBooks()

    suspend fun ensureStarterBooksSeeded() = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.preferences.first()
        if (!prefs.hasSeededLibrary && dao.getBookCount() == 0) {
            val seeded = converter.seedStarterBooks()
            dao.insertBooks(seeded.map { it.toEntity() })
            preferencesRepository.markSeeded()
        } else if (!prefs.hasSeededLibrary) {
            preferencesRepository.markSeeded()
        }
    }

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

    suspend fun loadParsedEpub(book: BookEntity): ParsedEpub = withContext(Dispatchers.IO) {
        val imageCacheDir = File(appContext.cacheDir, "epub_images/${book.id}")
        EpubParser.parse(File(book.epubPath), imageCacheDir)
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

    suspend fun syncBookMetadataIfNeeded(book: BookEntity, parsed: ParsedEpub) = withContext(Dispatchers.IO) {
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

    suspend fun downloadOnlineBook(book: GutendexBook): BookEntity {
        val epubUrl = book.epubDownloadUrl
            ?: throw IllegalStateException("No hay descarga EPUB disponible")
        val epubBytes = onlineCatalog.downloadBytes(epubUrl)
        val coverBytes = book.coverImageUrl?.let { url ->
            runCatching { onlineCatalog.downloadBytes(url) }.getOrNull()
        }
        val result = converter.saveDownloadedEpub(
            bookId = "gut-${book.id}",
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
