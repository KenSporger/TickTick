package com.personalticktick.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.personalticktick.app.domain.*
import com.personalticktick.app.reminder.AlarmGateway
import com.personalticktick.app.reminder.AlarmManagerReminderScheduler
import com.personalticktick.app.reminder.ReminderPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class DataReminderTest {
    private lateinit var database: TaskDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), TaskDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun roomRoundTripPreservesEveryTaskFieldAndOfflineWriteIsPending() = runTest {
        val repository = RoomTaskRepository(database.taskDao(), SyncApi { error("offline") })
        val original = completeTask()
        repository.upsert(original)
        assertEquals(original.copy(syncState = SyncState.PENDING), repository.observeAll().first().single())
        assertEquals(SyncState.PENDING, repository.find(original.id)?.syncState)
        assertNull(repository.find(original.id)?.completedAt)
    }

    @Test fun syncSuccessMarksLocalTruthSyncedAndFailurePreservesPendingLocalTruth() = runTest {
        val successful = RoomTaskRepository(database.taskDao(), SyncApi { request ->
            SyncResponse(request.tasks, "2026-10-03T08:01:00")
        })
        successful.upsert(completeTask().copy(id = "success", title = "local success"))
        assertEquals(1, successful.syncPending())
        assertEquals(SyncState.SYNCED, successful.find("success")?.syncState)

        val failing = RoomTaskRepository(database.taskDao(), SyncApi { error("network down") })
        failing.upsert(completeTask().copy(id = "failure", title = "keep this local value"))
        assertEquals(0, failing.syncPending())
        assertEquals("keep this local value", failing.find("failure")?.title)
        assertEquals(SyncState.PENDING, failing.find("failure")?.syncState)
    }

    @Test fun startupSyncWithNoPendingRowsPullsAndAppliesCompleteServerSnapshot() = runTest {
        val remote = completeTask().copy(id = "remote-at-startup", title = "服务端已有任务").toDto()
        val repository = RoomTaskRepository(database.taskDao(), SyncApi { request ->
            assertTrue(request.tasks.isEmpty())
            SyncResponse(listOf(remote), "2026-10-11T08:00:00Z")
        })

        assertEquals(0, database.taskDao().all().size)
        assertEquals(0, repository.syncPending())
        assertEquals("服务端已有任务", repository.find("remote-at-startup")?.title)
    }

    @Test fun startupSyncAcceptsLegacyAbandonedYearlyTask() = runTest {
        val remote = completeTask().copy(
            id = "legacy-abandoned",
            status = TaskStatus.ABANDONED,
            repeatRule = RepeatRule(
                kind = RepeatKind.YEARLY,
                interval = 2,
                skipHolidays = true,
                excludedDates = setOf(LocalDate.of(2026, 10, 1)),
                repeatUntil = LocalDate.of(2030, 12, 31)
            )
        ).toDto()
        val repository = RoomTaskRepository(database.taskDao(), SyncApi {
            SyncResponse(listOf(remote), "2026-10-11T08:00:00Z")
        })

        repository.syncPending()
        val restored = repository.find("legacy-abandoned")!!
        assertEquals(TaskStatus.ABANDONED, restored.status)
        assertEquals(remote.toDomain().repeatRule, restored.repeatRule)
    }

    @Test fun permissionChannelAndExactAlarmStateMapToUserVisibleCapability() {
        assertEquals(ReminderCapability.UNAVAILABLE, ReminderPolicy.capability(false, true, true))
        assertEquals(ReminderCapability.UNAVAILABLE, ReminderPolicy.capability(true, false, true))
        assertEquals(ReminderCapability.MAY_DELAY, ReminderPolicy.capability(true, true, false))
        assertEquals(ReminderCapability.EXACT, ReminderPolicy.capability(true, true, true))
    }

    @Test fun editCompleteAndDeleteCancelTheStableReminderIdentity() {
        val gateway = RecordingAlarmGateway()
        val scheduler = AlarmManagerReminderScheduler(gateway) { ReminderCapability.EXACT }
        val task = completeTask().copy(id = "stable-id", reminderAt = LocalDateTime.of(2026, 10, 4, 9, 0))
        scheduler.schedule(task)
        scheduler.schedule(task.copy(reminderAt = task.reminderAt!!.plusHours(2)))
        scheduler.schedule(task.copy(status = TaskStatus.COMPLETED))
        scheduler.cancel(task.id)
        assertEquals(listOf("stable-id", "stable-id", "stable-id", "stable-id"), gateway.cancelled)
        assertEquals(listOf("stable-id", "stable-id"), gateway.scheduled.map { it.first })
        assertEquals(task.reminderAt!!.plusHours(2), gateway.scheduled.last().second)
    }

    @Test fun deletingTaskLeavesTombstonePendingForServerSync() = runTest {
        val repository = RoomTaskRepository(database.taskDao(), SyncApi { error("offline") })
        repository.upsert(completeTask().copy(id = "delete-me"))
        repository.delete("delete-me", futureSeries = false)
        assertNull(repository.find("delete-me"))
        val tombstone = database.taskDao().findIncludingDeleted("delete-me")!!.toDomain()
        assertEquals(TaskStatus.DELETED, tombstone.status)
        assertEquals(SyncState.PENDING, tombstone.syncState)
    }

    @Test fun completeSetsCompletedAtAndRestoreClearsIt() = runTest {
        val repository = RoomTaskRepository(database.taskDao(), SyncApi { error("offline") })
        repository.upsert(completeTask().copy(id = "completion-time"))
        assertNotNull(repository.complete("completion-time")?.completedAt)
        assertNull(repository.restore("completion-time")?.completedAt)
    }

    private fun completeTask() = Task(
        id = "task-1", seriesId = "series-1", title = "跨天复习",
        startDate = LocalDate.of(2026, 10, 3), endDate = LocalDate.of(2026, 10, 5),
        time = LocalTime.of(9, 45), reminderAt = LocalDateTime.of(2026, 10, 3, 9, 30),
        repeatRule = RepeatRule(RepeatKind.WEEKLY, setOf(1, 3, 5), null, 8, 2),
        status = TaskStatus.ACTIVE, isProjection = false, syncState = SyncState.SYNCED,
        createdAt = LocalDateTime.of(2026, 10, 1, 12, 0),
        updatedAt = LocalDateTime.of(2026, 10, 3, 8, 0)
    )
}

private class RecordingAlarmGateway : AlarmGateway {
    val scheduled = mutableListOf<Pair<String, LocalDateTime>>()
    val cancelled = mutableListOf<String>()
    override fun scheduleExact(taskId: String, at: LocalDateTime) { scheduled += taskId to at }
    override fun scheduleInexact(taskId: String, at: LocalDateTime) { scheduled += taskId to at }
    override fun cancel(taskId: String) { cancelled += taskId }
}
