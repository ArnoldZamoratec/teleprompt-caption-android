package com.arnoldcode.glassprompt.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arnoldcode.glassprompt.data.local.database.GlassPromptDatabase
import com.arnoldcode.glassprompt.data.local.database.Migrations
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema version must upgrade to the latest without losing user data. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GlassPromptDatabase::class.java,
    )

    @Test
    fun migrate1To2_keepsProjectsAndAddsTakes() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO scripts (id, title, body, wordCount, updatedAt) VALUES ('s1', 'Demo', 'hola mundo', 2, 10)")
            db.execSQL(
                """
                INSERT INTO projects (id, name, scriptId, rec_lens, rec_orientation, rec_resolution, rec_frameRate,
                    tp_speed, tp_fontSizeSp, tp_lineSpacing, tp_letterSpacing, tp_horizontalMarginDp, tp_position,
                    tp_alignment, tp_mirror, tp_backgroundOpacity, tp_countdownSeconds, captionStyleId, createdAt, updatedAt)
                VALUES ('p1', 'Demo', 's1', 'FRONT', 'PORTRAIT', 'FHD_1080', 30, 1.0, 34.0, 1.5, 0.0, 24, 'TOP',
                    'CENTER', 0, 0.55, 3, NULL, 10, 10)
                """.trimIndent(),
            )
        }

        // Validates the migrated schema against the exported v2 schema.
        helper.runMigrationsAndValidate(dbName, 2, true, *Migrations.ALL).use { db ->
            db.execSQL("INSERT INTO takes (id, projectId, filePath, durationMs, width, height, frameRate, createdAt) VALUES ('t1', 'p1', '/x.mp4', 1000, 1080, 1920, 30, 20)")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL).close()

        val database = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            GlassPromptDatabase::class.java,
            dbName,
        ).addMigrations(*Migrations.ALL).build()
        try {
            runBlocking {
                val summary = database.projectDao().observeSummaries().first().single()
                assertThat(summary.name).isEqualTo("Demo")
                assertThat(summary.wordCount).isEqualTo(2)
                assertThat(database.takeDao().filePathsOf("p1")).containsExactly("/x.mp4")
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate2To3_addsCaptionTracksThatCascadeWithTheTake() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO scripts (id, title, body, wordCount, updatedAt) VALUES ('s1', 'Demo', 'hola', 1, 10)")
            db.execSQL(
                """
                INSERT INTO projects (id, name, scriptId, rec_lens, rec_orientation, rec_resolution, rec_frameRate,
                    tp_speed, tp_fontSizeSp, tp_lineSpacing, tp_letterSpacing, tp_horizontalMarginDp, tp_position,
                    tp_alignment, tp_mirror, tp_backgroundOpacity, tp_countdownSeconds, captionStyleId, createdAt, updatedAt)
                VALUES ('p1', 'Demo', 's1', 'FRONT', 'PORTRAIT', 'FHD_1080', 30, 1.0, 34.0, 1.5, 0.0, 24, 'TOP',
                    'CENTER', 0, 0.55, 3, NULL, 10, 10)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO takes (id, projectId, filePath, durationMs, width, height, frameRate, createdAt) VALUES ('t1', 'p1', '/x.mp4', 1000, 1080, 1920, 30, 20)")
        }

        helper.runMigrationsAndValidate(dbName, 3, true, *Migrations.ALL).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO caption_tracks (takeId, styleJson, language, engineId, updatedAt) VALUES ('t1', '{}', 'es-ES', 'x', 30)")
            db.execSQL("INSERT INTO captions (id, takeId, text, startMs, endMs, wordsJson) VALUES ('c1', 't1', 'hola', 0, 500, '[]')")
            db.execSQL("DELETE FROM takes WHERE id = 't1'")
            db.query("SELECT COUNT(*) FROM captions").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate3To4_addsExportsThatCascadeWithTheTake() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO scripts (id, title, body, wordCount, updatedAt) VALUES ('s1', 'Demo', 'hola', 1, 10)")
            db.execSQL(
                """
                INSERT INTO projects (id, name, scriptId, rec_lens, rec_orientation, rec_resolution, rec_frameRate,
                    tp_speed, tp_fontSizeSp, tp_lineSpacing, tp_letterSpacing, tp_horizontalMarginDp, tp_position,
                    tp_alignment, tp_mirror, tp_backgroundOpacity, tp_countdownSeconds, captionStyleId, createdAt, updatedAt)
                VALUES ('p1', 'Demo', 's1', 'FRONT', 'PORTRAIT', 'FHD_1080', 30, 1.0, 34.0, 1.5, 0.0, 24, 'TOP',
                    'CENTER', 0, 0.55, 3, NULL, 10, 10)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO takes (id, projectId, filePath, durationMs, width, height, frameRate, createdAt) VALUES ('t1', 'p1', '/x.mp4', 1000, 1080, 1920, 30, 20)")
        }

        helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL(
                "INSERT INTO exports (id, takeId, filePath, width, height, frameRate, durationMs, sizeBytes, withCaptions, galleryUri, createdAt) " +
                    "VALUES ('e1', 't1', '/e.mp4', 1080, 1920, 30, 1000, 500, 1, NULL, 40)",
            )
            db.execSQL("DELETE FROM takes WHERE id = 't1'")
            db.query("SELECT COUNT(*) FROM exports").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }
}
