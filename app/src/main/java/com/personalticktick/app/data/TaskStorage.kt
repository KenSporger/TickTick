package com.personalticktick.app.data

import android.content.Context
import androidx.room.*
import com.google.gson.annotations.SerializedName
import com.personalticktick.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val AppZone: ZoneId = ZoneId.of("Asia/Shanghai")

private fun LocalDateTime.toWireTimestamp(): String = atZone(AppZone).toOffsetDateTime().toString()

private fun parseWireTimestamp(value: String): LocalDateTime =
    OffsetDateTime.parse(value).atZoneSameInstant(AppZone).toLocalDateTime()

@Entity(tableName = "tasks", indices = [Index("seriesId"), Index("syncState")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val seriesId: String?,
    val title: String,
    val startDate: String?,
    val endDate: String?,
    val time: String?,
    val reminderAt: String?,
    val repeatKind: String,
    val repeatWeekdays: String,
    val repeatMonthDay: Int?,
    val repeatTotalCount: Int?,
    val repeatOccurrenceIndex: Int,
    val repeatInterval: Int = 1,
    val repeatSkipHolidays: Boolean = false,
    val repeatExcludedDates: String = "",
    val repeatUntil: String? = null,
    val status: String,
    val isProjection: Boolean,
    val syncState: String,
    val createdAt: String,
    val updatedAt: String
)

fun Task.toEntity(state: SyncState = syncState) = TaskEntity(
    id, seriesId, title, startDate?.toString(), endDate?.toString(), time?.toString(),
    reminderAt?.toString(), repeatRule.kind.name, repeatRule.weekdays.sorted().joinToString(","),
    repeatRule.monthDay, repeatRule.totalCount, repeatRule.occurrenceIndex, repeatRule.interval,
    repeatRule.skipHolidays, repeatRule.excludedDates.sorted().joinToString(","), repeatRule.repeatUntil?.toString(),
    status.name,
    isProjection, state.name, createdAt.toString(), updatedAt.toString()
)

fun TaskEntity.toDomain() = Task(
    id, seriesId, title, startDate?.let(LocalDate::parse), endDate?.let(LocalDate::parse),
    time?.let(LocalTime::parse), reminderAt?.let(LocalDateTime::parse),
    RepeatRule(RepeatKind.valueOf(repeatKind), repeatWeekdays.split(',').mapNotNull(String::toIntOrNull).toSet(),
        repeatMonthDay, repeatTotalCount, repeatOccurrenceIndex, repeatInterval, repeatSkipHolidays,
        repeatExcludedDates.split(',').mapNotNull { value -> value.takeIf(String::isNotEmpty)?.let(LocalDate::parse) }.toSet(),
        repeatUntil?.let(LocalDate::parse)),
    TaskStatus.valueOf(status), isProjection, SyncState.valueOf(syncState),
    LocalDateTime.parse(createdAt), LocalDateTime.parse(updatedAt)
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status != 'DELETED' ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'DELETED'")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id AND status != 'DELETED'")
    suspend fun find(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findIncludingDeleted(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE syncState = 'PENDING'")
    suspend fun pending(): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("SELECT * FROM tasks WHERE seriesId = :seriesId AND (startDate IS NULL OR startDate >= :startDate)")
    suspend fun futureSeries(seriesId: String, startDate: String): List<TaskEntity>
}

@Database(entities = [TaskEntity::class], version = 3, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: TaskDatabase? = null
        fun get(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, "tasks.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }

        fun reset() {
            instance?.close()
            instance = null
        }
    }
}

data class RepeatRuleDto(
    val kind: String = "NONE", val weekdays: List<Int> = emptyList(),
    @SerializedName("month_day") val monthDay: Int? = null,
    @SerializedName("total_count") val totalCount: Int? = null,
    @SerializedName("occurrence_index") val occurrenceIndex: Int = 1,
    val interval: Int = 1,
    @SerializedName("skip_holidays") val skipHolidays: Boolean = false,
    @SerializedName("excluded_dates") val excludedDates: List<String> = emptyList(),
    @SerializedName("repeat_until") val repeatUntil: String? = null
)

data class TaskDto(
    val id: String,
    @SerializedName("series_id") val seriesId: String? = null,
    val title: String,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    val time: String? = null,
    @SerializedName("reminder_at") val reminderAt: String? = null,
    @SerializedName("repeat_rule") val repeatRule: RepeatRuleDto = RepeatRuleDto(),
    val status: String = "ACTIVE",
    @SerializedName("is_projection") val isProjection: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String
)

data class SyncRequest(val tasks: List<TaskDto>)
data class SyncResponse(val tasks: List<TaskDto>, @SerializedName("server_time") val serverTime: String)

fun interface SyncApi {
    @POST("api/sync")
    suspend fun sync(@Body request: SyncRequest): SyncResponse
}

object SyncClient {
    fun create(baseUrl: String): SyncApi {
        val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SyncApi::class.java)
    }
}

fun Task.toDto() = TaskDto(id, seriesId, title, startDate?.toString(), endDate?.toString(),
    time?.toString(), reminderAt?.toString(), RepeatRuleDto(repeatRule.kind.name,
        repeatRule.weekdays.sorted(), repeatRule.monthDay, repeatRule.totalCount, repeatRule.occurrenceIndex,
        repeatRule.interval, repeatRule.skipHolidays, repeatRule.excludedDates.map(LocalDate::toString).sorted(),
        repeatRule.repeatUntil?.toString()),
    status.name, isProjection, updatedAt.toWireTimestamp())

fun TaskDto.toDomain(createdAt: LocalDateTime = parseWireTimestamp(updatedAt)) = Task(
    id, seriesId, title, startDate?.let(LocalDate::parse), endDate?.let(LocalDate::parse),
    time?.let(LocalTime::parse), reminderAt?.let(LocalDateTime::parse),
    RepeatRule(RepeatKind.valueOf(repeatRule.kind), repeatRule.weekdays.toSet(), repeatRule.monthDay,
        repeatRule.totalCount, repeatRule.occurrenceIndex, repeatRule.interval, repeatRule.skipHolidays,
        repeatRule.excludedDates.map(LocalDate::parse).toSet(), repeatRule.repeatUntil?.let(LocalDate::parse)),
    TaskStatus.valueOf(status), isProjection,
    SyncState.SYNCED, createdAt, parseWireTimestamp(updatedAt)
)

class RoomTaskRepository(
    private val dao: TaskDao,
    private val api: SyncApi,
    private val reminders: ReminderScheduler? = null,
    private val syncEnabled: Boolean = true
) : TaskRepository {
    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { rows -> rows.map(TaskEntity::toDomain) }

    override suspend fun upsert(task: Task) {
        dao.upsert(task.toEntity(SyncState.PENDING))
        reminders?.schedule(task)
    }
    override suspend fun find(id: String): Task? = dao.find(id)?.toDomain()

    override suspend fun complete(id: String): Task? = changeStatus(id, TaskStatus.COMPLETED).also { completed ->
        completed?.also { reminders?.schedule(it) }
            ?.let(TaskLifecycle::nextOccurrence)?.let {
                dao.upsert(it.toEntity(SyncState.PENDING))
                reminders?.schedule(it)
            }
    }
    override suspend fun restore(id: String): Task? = changeStatus(id, TaskStatus.ACTIVE).also { it?.let { task -> reminders?.schedule(task) } }
    override suspend fun skip(id: String): Task? = changeStatus(id, TaskStatus.SKIPPED).also { skipped ->
        skipped?.also { reminders?.schedule(it) }
            ?.let(TaskLifecycle::nextOccurrence)?.let {
                dao.upsert(it.toEntity(SyncState.PENDING))
                reminders?.schedule(it)
            }
    }

    private suspend fun changeStatus(id: String, status: TaskStatus): Task? {
        val changed = dao.find(id)?.toDomain()?.copy(status = status, syncState = SyncState.PENDING,
            updatedAt = LocalDateTime.now()) ?: return null
        dao.upsert(changed.toEntity())
        return changed
    }

    override suspend fun delete(id: String, futureSeries: Boolean) {
        val task = dao.find(id)?.toDomain() ?: return
        val targets = if (futureSeries && task.seriesId != null)
            dao.futureSeries(task.seriesId, task.startDate?.toString() ?: "").map(TaskEntity::toDomain)
        else listOf(task)
        val now = LocalDateTime.now()
        dao.upsertAll(targets.map { it.copy(status = TaskStatus.DELETED, updatedAt = now).toEntity(SyncState.PENDING) })
        targets.forEach { reminders?.cancel(it.id) }
    }

    override suspend fun tasksForDate(date: LocalDate): List<Task> =
        dao.all().map(TaskEntity::toDomain).filter { TaskLifecycle.isVisibleOn(it, date) }

    override suspend fun overdue(today: LocalDate): List<Task> =
        dao.all().map(TaskEntity::toDomain).filter { TaskLifecycle.isOverdue(it, today) }

    override suspend fun search(query: String): List<Task> = TaskSearch.filter(dao.all().map(TaskEntity::toDomain), query)

    override suspend fun current(): List<Task> = dao.all().map(TaskEntity::toDomain)
    override suspend fun storedCount(): Int = dao.countAll()
    override fun cloudSyncEnabled(): Boolean = syncEnabled

    override suspend fun refreshFromCloud(): Boolean {
        if (!syncEnabled) return false
        return try {
            merge(api.sync(SyncRequest(dao.pending().map { it.toDomain().toDto() })))
            true
        } catch (_: Exception) { false }
    }

    override suspend fun syncPending(): Int {
        if (!syncEnabled) return 0
        val pending = dao.pending()
        return try {
            merge(api.sync(SyncRequest(pending.map { it.toDomain().toDto() })))
            pending.size
        } catch (_: Exception) { 0 }
    }

    private suspend fun merge(response: SyncResponse) {
        val current = dao.all().associateBy { it.id }
        dao.upsertAll(response.tasks.map { dto ->
            dto.toDomain(current[dto.id]?.createdAt?.let(LocalDateTime::parse)
                ?: parseWireTimestamp(dto.updatedAt)).toEntity(SyncState.SYNCED)
        })
    }
}
