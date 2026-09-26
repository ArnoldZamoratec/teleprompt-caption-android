package com.arnoldcode.glassprompt.testing

import com.arnoldcode.glassprompt.core.common.AppError
import com.arnoldcode.glassprompt.core.common.AppResult
import com.arnoldcode.glassprompt.domain.model.NewTake
import com.arnoldcode.glassprompt.domain.model.Take
import com.arnoldcode.glassprompt.domain.repository.MediaStorage
import com.arnoldcode.glassprompt.domain.repository.TakeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTakeRepository(private val storage: MediaStorage? = null) : TakeRepository {

    private val takes = MutableStateFlow<Map<String, Take>>(emptyMap())
    private var nextId = 1
    var now = 5_000L

    fun all(): List<Take> = takes.value.values.toList()

    override fun observeTakes(projectId: String): Flow<List<Take>> =
        takes.map { all -> all.values.filter { it.projectId == projectId }.sortedByDescending { it.createdAt } }

    override fun observeTake(id: String): Flow<Take?> = takes.map { it[id] }

    override suspend fun getTake(id: String): AppResult<Take> =
        takes.value[id]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.NotFound(id))

    override suspend fun addTake(take: NewTake): AppResult<String> {
        val id = "t${nextId++}"
        takes.update {
            it + (id to Take(id, take.projectId, take.filePath, take.durationMs, take.width, take.height, take.frameRate, now))
        }
        return AppResult.Success(id)
    }

    override suspend fun deleteTake(id: String): AppResult<Unit> {
        val take = takes.value[id] ?: return AppResult.Failure(AppError.NotFound(id))
        takes.update { it - id }
        storage?.delete(take.filePath)
        return AppResult.Success(Unit)
    }
}

/** Records created/deleted files instead of touching disk. */
class FakeMediaStorage(var freeBytes: Long = Long.MAX_VALUE) : MediaStorage {
    private var counter = 0
    val created = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    override fun newTakeFile(): String = "/takes/take_${++counter}.mp4".also { created += it }
    override fun newExportFile(): String = "/exports/export_${++counter}.mp4".also { created += it }
    override fun availableBytes(): Long = freeBytes
    override fun delete(path: String): Boolean {
        deleted += path
        return true
    }
}
