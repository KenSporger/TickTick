package com.personalticktick.app

import com.personalticktick.app.data.toDomain
import com.personalticktick.app.data.toDto
import com.personalticktick.app.domain.RepeatKind
import com.personalticktick.app.domain.RepeatRule
import com.personalticktick.app.domain.SyncState
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class IntegrationContractTest {
    @Test fun taskRoundTripsThroughBackendDtoWithoutFieldLoss() {
        val original = Task(
            id = "task-1", seriesId = "series-1", title = "跨天复习",
            startDate = LocalDate.of(2026, 10, 3), endDate = LocalDate.of(2026, 10, 5),
            time = LocalTime.of(9, 45), reminderAt = LocalDateTime.of(2026, 10, 3, 9, 30),
            repeatRule = RepeatRule(RepeatKind.WEEKLY, setOf(1, 3, 5), null, 8, 2),
            status = TaskStatus.ACTIVE, isProjection = false, syncState = SyncState.PENDING,
            createdAt = LocalDateTime.of(2026, 10, 1, 12, 0),
            updatedAt = LocalDateTime.of(2026, 10, 3, 8, 0)
        )

        val wire = original.toDto()

        assertEquals("2026-10-03T08:00+08:00", wire.updatedAt)
        assertEquals(
            original.copy(syncState = SyncState.SYNCED),
            wire.toDomain(createdAt = original.createdAt)
        )
    }
}
