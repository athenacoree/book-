package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = false
)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN language TEXT NOT NULL DEFAULT 'Español'")
                db.execSQL("ALTER TABLE books ADD COLUMN genre TEXT NOT NULL DEFAULT 'Lectura general'")
                db.execSQL("ALTER TABLE books ADD COLUMN publisher TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN publicationYear TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE books ADD COLUMN wordCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE books ADD COLUMN estimatedPages INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN totalImages INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN gameSaveJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE books ADD COLUMN gameBestRecord TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "luminabook_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
