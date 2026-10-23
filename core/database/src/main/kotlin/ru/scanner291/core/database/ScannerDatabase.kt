package ru.scanner291.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Индекс документов. Версия 1 — этап 5 (документы и страницы). Папки, теги, FTS OCR, корзина —
 * этапы 10 и 14 через миграции (схемы — в core/database/schemas, сравниваются тестом миграций).
 */
@Database(entities = [DocumentEntity::class, PageEntity::class], version = 1, exportSchema = true)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
