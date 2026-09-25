package com.arnoldcode.glassprompt.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arnoldcode.glassprompt.data.local.dao.CaptionDao
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.dao.TakeDao
import com.arnoldcode.glassprompt.data.local.entities.CaptionEntity
import com.arnoldcode.glassprompt.data.local.entities.CaptionTrackEntity
import com.arnoldcode.glassprompt.data.local.entities.ProjectEntity
import com.arnoldcode.glassprompt.data.local.entities.ScriptEntity
import com.arnoldcode.glassprompt.data.local.entities.TakeEntity

/**
 * App database. Schemas are exported to `app/schemas`; every version bump ships with a
 * migration in [Migrations] and a migration test.
 */
@Database(
    entities = [
        ProjectEntity::class, ScriptEntity::class, TakeEntity::class,
        CaptionTrackEntity::class, CaptionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class GlassPromptDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun takeDao(): TakeDao
    abstract fun captionDao(): CaptionDao

    companion object {
        const val NAME = "glassprompt.db"
    }
}
