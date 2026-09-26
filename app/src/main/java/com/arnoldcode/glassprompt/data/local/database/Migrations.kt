package com.arnoldcode.glassprompt.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Hand-written migrations. SQL must match the exported schema of the target version exactly. */
object Migrations {

    /** v2: recorded takes. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `takes` (
                    `id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `filePath` TEXT NOT NULL,
                    `durationMs` INTEGER NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL,
                    `frameRate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_takes_projectId` ON `takes` (`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_takes_createdAt` ON `takes` (`createdAt`)")
        }
    }

    /** v3: caption tracks (style + origin) and their captions. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `caption_tracks` (
                    `takeId` TEXT NOT NULL, `styleJson` TEXT NOT NULL, `language` TEXT NOT NULL,
                    `engineId` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`takeId`),
                    FOREIGN KEY(`takeId`) REFERENCES `takes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `captions` (
                    `id` TEXT NOT NULL, `takeId` TEXT NOT NULL, `text` TEXT NOT NULL,
                    `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `wordsJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`takeId`) REFERENCES `caption_tracks`(`takeId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_captions_takeId` ON `captions` (`takeId`)")
        }
    }

    /** v4: exported videos. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exports` (
                    `id` TEXT NOT NULL, `takeId` TEXT NOT NULL, `filePath` TEXT NOT NULL,
                    `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `frameRate` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `withCaptions` INTEGER NOT NULL,
                    `galleryUri` TEXT, `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`takeId`) REFERENCES `takes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exports_takeId` ON `exports` (`takeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_exports_createdAt` ON `exports` (`createdAt`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
