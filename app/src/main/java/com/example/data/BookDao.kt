package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY CASE WHEN lastReadTimestamp > 0 THEN lastReadTimestamp ELSE addedTimestamp END DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isConvertedFromOtherFormat = 1 ORDER BY addedTimestamp DESC")
    fun observeConvertedBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Query(
        "UPDATE books SET currentChapterIndex = :chapterIndex, currentPageInChapter = :pageIndex, " +
            "progressPercent = :progressPercent, lastReadTimestamp = :lastRead WHERE id = :bookId"
    )
    suspend fun updateProgress(
        bookId: String,
        chapterIndex: Int,
        pageIndex: Int,
        progressPercent: Int,
        lastRead: Long
    )

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :bookId")
    suspend fun setFavorite(bookId: String, favorite: Boolean)

    @Query("UPDATE books SET genre = :genre WHERE id = :bookId")
    suspend fun updateGenre(bookId: String, genre: String)

    @Query(
        "UPDATE books SET language = :language, genre = :genre, publisher = :publisher, " +
            "publicationYear = :publicationYear, description = :description, " +
            "wordCount = :wordCount, estimatedPages = :estimatedPages, totalImages = :totalImages " +
            "WHERE id = :bookId"
    )
    suspend fun updateBookMetadata(
        bookId: String,
        language: String,
        genre: String,
        publisher: String,
        publicationYear: String,
        description: String,
        wordCount: Int,
        estimatedPages: Int,
        totalImages: Int
    )

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksForBook(bookId: String)
}
