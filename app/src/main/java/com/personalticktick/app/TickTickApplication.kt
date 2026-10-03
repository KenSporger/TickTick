package com.personalticktick.app

import android.app.Application
import com.personalticktick.app.data.RoomTaskRepository
import com.personalticktick.app.data.SyncClient
import com.personalticktick.app.data.TaskDatabase
import com.personalticktick.app.domain.TaskRepository

class TickTickApplication : Application() {
    private val syncApi by lazy { SyncClient.create(BuildConfig.API_BASE_URL) }

    fun repository(): TaskRepository {
        val enabled = getSharedPreferences("ticktick", MODE_PRIVATE).getBoolean("sync_enabled", true)
        return RoomTaskRepository(TaskDatabase.get(this).taskDao(), syncApi, syncEnabled = enabled)
    }
}
