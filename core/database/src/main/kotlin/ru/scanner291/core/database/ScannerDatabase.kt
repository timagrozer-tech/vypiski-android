package ru.scanner291.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Индекс документов. v1 — этап 5 (документы и страницы), v2 — этап 6 (углы листа), v3 — этап 7 (рецепт обработки), v4 — этап 10 (текстовый слой и FTS), v5 — этап 14 (папки, теги, избранное, корзина). Папки, теги, FTS OCR, корзина —
 * этапы 10 и 14 через миграции (схемы — в core/database/schemas, сравниваются тестом миграций).
 */
@Database(entities = [DocumentEntity::class, PageEntity::class, PageTextEntity::class, PageTextFts::class, FolderEntity::class, TagEntity::class, DocumentTag::class], version = 5, exportSchema = true)
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
        /** v2 → v3 (этап 7): рецепт обработки страницы. Только ADD COLUMN. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pages ADD COLUMN recipe TEXT")
            }
        }
        /** v3 → v4 (этап 10): текстовый слой страниц и полнотекстовый индекс. Только новые таблицы. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `page_text` (`pageId` TEXT NOT NULL, `docId` TEXT NOT NULL, `text` TEXT NOT NULL, `words` TEXT NOT NULL, `mode` TEXT NOT NULL, `recipeKey` TEXT NOT NULL, `edited` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, PRIMARY KEY(`pageId`), FOREIGN KEY(`pageId`) REFERENCES `pages`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_text_docId` ON `page_text` (`docId`)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `page_text_fts` USING FTS4(`pageId` TEXT NOT NULL, `documentId` TEXT NOT NULL, `text` TEXT NOT NULL, tokenize=unicode61, notindexed=`pageId`, notindexed=`documentId`)")
            }
        }
        /** v4 → v5 (этап 14): папки, теги, избранное, корзина документов. ADD COLUMN и новые таблицы. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN folderId TEXT")
                db.execSQL("ALTER TABLE documents ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN trashedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_folderId` ON `documents` (`folderId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `document_tags` (`docId` TEXT NOT NULL, `tagId` TEXT NOT NULL, PRIMARY KEY(`docId`, `tagId`), FOREIGN KEY(`docId`) REFERENCES `documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_tags_tagId` ON `document_tags` (`tagId`)")
            }
        }
        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    }
}
