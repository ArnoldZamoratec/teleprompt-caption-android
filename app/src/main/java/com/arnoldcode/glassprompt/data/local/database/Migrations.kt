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

    val ALL = arrayOf(MIGRATION_1_2)
}
