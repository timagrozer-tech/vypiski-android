package ru.scanner291.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Индекс документов. v1 — этап 5 (документы и страницы), v2 — этап 6 (углы листа). Папки, теги, FTS OCR, корзина —
 * этапы 10 и 14 через миграции (схемы — в core/database/schemas, сравниваются тестом миграций).
 */
@Database(entities = [DocumentEntity::class, PageEntity::class], version = 2, exportSchema = true)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    companion object {
        /** v1 → v2 (этап 6): углы листа у страницы. Только ADD COLUMN — данные не трогаются. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN corners TEXT")
                db.execSQL("ALTER TABLE pages ADD COLUMN cornersSource TEXT")
            }
        }
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
