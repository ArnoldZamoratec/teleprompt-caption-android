package com.arnoldcode.glassprompt.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arnoldcode.glassprompt.data.local.dao.ProjectDao
import com.arnoldcode.glassprompt.data.local.entities.ProjectEntity
import com.arnoldcode.glassprompt.data.local.entities.ScriptEntity

/**
 * App database. Schemas are exported to `app/schemas`; every version bump ships with a
 * migration and a migration test (takes, captions and caption styles arrive in later phases).
 */
@Database(
    entities = [ProjectEntity::class, ScriptEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GlassPromptDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        const val NAME = "glassprompt.db"
    }
}
