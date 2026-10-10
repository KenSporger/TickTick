package com.personalticktick.app.binding

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalticktick.app.data.SyncApi
import com.personalticktick.app.data.SyncResponse
import com.personalticktick.app.data.TaskDatabase
import com.personalticktick.app.data.TaskDto
import com.personalticktick.app.data.toEntity
import com.personalticktick.app.domain.SyncState
import com.personalticktick.app.domain.Task
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class ServerBindingTest {
    private lateinit var database: TaskDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TaskDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun validQrPayloadIsNormalizedAndInvalidOrExpiredPayloadIsRejected() {
        val decoded = BindingPayload.parse(
            """{"version":1,"base_url":"https://tasks.example.com/ticktick","token":"once-123","expires_at":"2026-10-10T12:00:00Z"}""",
            nowEpochSeconds = 1_760_093_900
        )
        assertEquals("https://tasks.example.com/ticktick/", decoded.baseUrl)
        assertEquals("once-123", decoded.token)

        assertThrows(InvalidBindingCode::class.java) {
            BindingPayload.parse("https://tasks.example.com", 1_760_093_900)
        }
        assertThrows(ExpiredBindingCode::class.java) {
            BindingPayload.parse(
                """{"version":1,"base_url":"https://tasks.example.com/","token":"old","expires_at":"2020-01-01T00:00:00Z"}""",
                nowEpochSeconds = 1_760_093_900
            )
        }
    }

    @Test fun buildModeUsesConfiguredUrlWithoutBindingAndUnifiedBuildRequiresBinding() {
        assertEquals(
            StartupDestination.TASKS,
            StartupPolicy.destination("https://self-hosted.example.com/api", savedBinding = null)
        )
        assertEquals(StartupDestination.BIND, StartupPolicy.destination("", savedBinding = null))
        assertEquals(
            StartupDestination.TASKS,
            StartupPolicy.destination("", savedBinding = ServerBinding("https://saved.example.com/"))
        )
    }

    @Test fun bootstrapReplacesLocalTasksWithServerSnapshotAndMarksThemSynced() = runTest {
        database.taskDao().upsert(localTask("local-only").toEntity(SyncState.PENDING))
        val remote = taskDto("remote-only")
        val bootstrapper = ServerBootstrapper(database, SyncApi { request ->
            assertTrue("bootstrap must never upload local tasks", request.tasks.isEmpty())
            SyncResponse(listOf(remote), "2026-10-10T08:00:00Z")
        })

        val count = bootstrapper.replaceFromServer()

        assertEquals(1, count)
        assertNull(database.taskDao().findIncludingDeleted("local-only"))
        assertEquals("remote-only", database.taskDao().find("remote-only")?.id)
        assertEquals("SYNCED", database.taskDao().find("remote-only")?.syncState)
    }

    @Test fun failedBootstrapPreservesOriginalLocalTasks() = runTest {
        database.taskDao().upsert(localTask("keep-me").toEntity(SyncState.PENDING))
        val bootstrapper = ServerBootstrapper(database, SyncApi { error("network down") })

        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { bootstrapper.replaceFromServer() } }

        assertNotNull(database.taskDao().find("keep-me"))
        assertEquals(1, database.taskDao().all().size)
    }

    private fun localTask(id: String) = Task(
        id = id,
        title = id,
        createdAt = LocalDateTime.of(2026, 10, 10, 8, 0),
        updatedAt = LocalDateTime.of(2026, 10, 10, 8, 0)
    )

    private fun taskDto(id: String) = TaskDto(
        id = id,
        title = id,
        updatedAt = "2026-10-10T08:00:00+08:00"
    )
}

