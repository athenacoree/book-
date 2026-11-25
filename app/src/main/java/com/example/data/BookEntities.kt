package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val originalFormat: String,
    val epubPath: String,
    val originalPath: String? = null,
    val coverPath: String? = null,
    val totalChapters: Int = 1,
    val currentChapterIndex: Int = 0,
    val currentPageInChapter: Int = 0,
    val progressPercent: Int = 0,
    val isFavorite: Boolean = false,
    val isConvertedFromOtherFormat: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val lastReadTimestamp: Long = 0L,
    val language: String = "Español",
    val genre: String = "Lectura general",
    val publisher: String = "",
    val publicationYear: String = "",
    val description: String = "",
    val wordCount: Int = 0,
    val estimatedPages: Int = 1,
    val totalImages: Int = 0,
    val gameSaveJson: String = "{}",
    val gameBestRecord: String = "",
    @ColumnInfo(defaultValue = "''") val gameVersion: String = "",
    val catalogGameId: Long? = null,
    @ColumnInfo(defaultValue = "'index.html'") val gamePackageEntry: String = "index.html",
    val readerSettingsJson: String = ""
)

@Entity(tableName = "bookmarks", indices = [Index(value = ["bookId", "createdAt"])])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int,
    val excerpt: String,
    val createdAt: Long = System.currentTimeMillis()
)

val BookEntity.isHtmlGame: Boolean
    get() = originalFormat.equals("HTML", ignoreCase = true)

