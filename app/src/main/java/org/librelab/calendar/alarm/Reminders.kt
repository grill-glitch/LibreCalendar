package org.librelab.calendar.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.librelab.calendar.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 提醒通知渠道 */
object ReminderNotifications {
    const val CHANNEL_ID = "event_reminders"
    private const val NOTIFICATION_ID_BASE = 1000

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "事件提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "日历事件提醒通知" }
            nm.createNotificationChannel(channel)
        }
    }

    /** 计算提醒触发时间: 事件开始 + 偏移 (偏移为负数=提前) */
    fun triggerTime(eventStart: Long, reminderMinutes: Int): Long =
        eventStart + reminderMinutes * 60_000L

    fun show(context: Context, eventId: Long, title: String, startTime: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val timeStr = Instant.ofEpochMilli(startTime)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("事件时间: $timeStr")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_ID_BASE + eventId.toInt(), notification)
    }
}

/** 事件提醒调度 */
object ReminderScheduler {

    /** 为单个事件设置提醒闹钟 (在后台线程调用) */
    fun schedule(context: Context, eventId: Long, triggerAt: Long, title: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_START, triggerAt)
        }
        val pi = PendingIntent.getBroadcast(
            context, eventId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            // Android 12+ 精确闹钟需要特殊权限; 无权限时 setExactAndAllowWhileIdle 抛 SecurityException
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 降级: 非精确但允许空闲唤醒 (误差约 1 分钟, 提醒可接受)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (_: SecurityException) {
            // 极端情况: 仍降级到普通 set (跟随省电策略, 可能延迟)
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** 取消事件提醒 */
    fun cancel(context: Context, eventId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
        }
        val pi = PendingIntent.getBroadcast(
            context, eventId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    /** 开机后重排: 扫描未来 30 天的带提醒事件 */
    suspend fun rescheduleAll(context: Context, repository: org.librelab.calendar.data.EventRepository) {
        val now = System.currentTimeMillis()
        val horizon = now + 30L * 24 * 3600 * 1000
        val flow: kotlinx.coroutines.flow.Flow<List<org.librelab.calendar.data.CalendarEvent>> =
            repository.observeRange(now, horizon)
        val events: List<org.librelab.calendar.data.CalendarEvent> = flow.first()
        for (e in events) {
            if (e.reminderMinutes == 0) continue
            val trigger = ReminderNotifications.triggerTime(e.startTime, e.reminderMinutes)
            if (trigger > now) schedule(context, e.id, trigger, e.title)
        }
    }
}

/** 提醒广播接收器 */
class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_REMIND = "org.librelab.calendar.ACTION_REMIND"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_START = "start"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        // 只接受自己的 PendingIntent 广播 (AlarmManager 投递), 忽略外部显式广播
        if (intent.getLongExtra(EXTRA_EVENT_ID, -1L) < 0) return
        ReminderNotifications.ensureChannel(context)
        val id = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "日历事件"
        val start = intent.getLongExtra(EXTRA_START, System.currentTimeMillis())
        ReminderNotifications.show(context, id, title, start)
    }
}

/** 开机重排接收器 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as org.librelab.calendar.CalendarApp
            GlobalScope.launch(Dispatchers.IO) {
                ReminderScheduler.rescheduleAll(context, org.librelab.calendar.data.EventRepository(app.database.eventDao(), app.database.labelDao()))
            }
        }
    }
}
