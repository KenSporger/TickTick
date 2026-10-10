package com.personalticktick.app.ui

import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskRepository

class RepositoryUiGateway(private val repository: TaskRepository) : TaskUiGateway {
    override fun observeAll() = repository.observeAll()
    override suspend fun save(task: Task) { repository.upsert(task); repository.syncPending() }
    override suspend fun complete(id: String) { repository.complete(id); repository.syncPending() }
    override suspend fun restore(id: String) { repository.restore(id); repository.syncPending() }
    override suspend fun delete(id: String, futureSeries: Boolean) { repository.delete(id, futureSeries); repository.syncPending() }
}
