package com.personalticktick.app.reminder

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personalticktick.app.data.TaskDatabase
import com.personalticktick.app.data.toDomain
import com.personalticktick.app.domain.ReminderCapability
import com.personalticktick.app.domain.ReminderScheduler
import com.personalticktick.app.domain.Task
import com.personalticktick.app.domain.TaskStatus
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.ZoneId

const val REMINDER_CHANNEL_ID = "task_reminders"
private const val EXTRA_TASK_ID = "task_id"
private const val EXTRA_TASK_TITLE = "task_title"

object ReminderPolicy {
    fun capability(notificationPermission: Boolean, channelEnabled: Boolean, exactAllowed: Boolean) = when {
        !notificationPermission || !channelEnabled -> ReminderCapability.UNAVAILABLE
        exactAllowed -> ReminderCapability.EXACT
        else -> ReminderCapability.MAY_DELAY
    }

    fun capability(context: Context): ReminderCapability {
        val notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelEnabled = Build.VERSION.SDK_INT < 26 ||
            manager.getNotificationChannel(REMINDER_CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
        val alarms = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        return capability(notificationsAllowed, channelEnabled, exactAllowed)
    }
}

interface AlarmGateway {
    fun scheduleExact(taskId: String, at: LocalDateTime)
    fun scheduleInexact(taskId: String, at: LocalDateTime)
    fun cancel(taskId: String)
}

class AndroidAlarmGateway(private val context: Context) : AlarmGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun scheduleExact(taskId: String, at: LocalDateTime) {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), intent(taskId))
    }

    override fun scheduleInexact(taskId: String, at: LocalDateTime) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMillis(), intent(taskId))
    }

    override fun cancel(taskId: String) = alarms.cancel(intent(taskId))

    private fun intent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("personalticktick://reminder/${Uri.encode(taskId)}")
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun LocalDateTime.toEpochMillis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class AlarmManagerReminderScheduler(
    private val gateway: AlarmGateway,
    private val capabilityProvider: () -> ReminderCapability
) : ReminderScheduler {
    constructor(context: Context) : this(AndroidAlarmGateway(context), { ReminderPolicy.capability(context) })

    override fun schedule(task: Task): ReminderCapability {
        // The stable task id is always cancelled first, so edits, completion and deletion cannot leave stale alarms.
        gateway.cancel(task.id)
        val capability = capabilityProvider()
        val at = task.reminderAt
        if (task.status != TaskStatus.ACTIVE || task.isProjection || at == null || capability == ReminderCapability.UNAVAILABLE)
            return capability
        when (capability) {
            ReminderCapability.EXACT -> gateway.scheduleExact(task.id, at)
            ReminderCapability.MAY_DELAY -> gateway.scheduleInexact(task.id, at)
            ReminderCapability.UNAVAILABLE -> Unit
        }
        return capability
    }

    override fun cancel(taskId: String) = gateway.cancel(taskId)

    override fun rebuild(tasks: List<Task>) {
        tasks.forEach { task -> if (task.status == TaskStatus.ACTIVE) schedule(task) else cancel(task.id) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "该做任务了"
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText("点击查看任务")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(REMINDER_CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val tasks = TaskDatabase.get(context).taskDao().all().map { it.toDomain() }
                AlarmManagerReminderScheduler(context).rebuild(tasks)
            } finally { pending.finish() }
        }
    }
}
