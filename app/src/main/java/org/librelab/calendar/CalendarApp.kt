package org.librelab.calendar

import android.app.Application
import android.content.Context
import androidx.room.Room
import org.librelab.calendar.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarApp : Application() {

    companion object {
        @Volatile
        lateinit var appContext: Context
            private set
    }

    // onCreate 中初始化 (构造器阶段 context 未 attach, Room 构建会 NPE)
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // 启动期构建 Room (而非滑动时首次访问): 避免主线程建库卡顿
        database = Room.databaseBuilder(this, AppDatabase::class.java, "md3-calendar.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        // 动态日期图标切换已移入 MainActivity (需感知 alias 入口, 避免禁用当前 alias 闪退)
        // 提醒通知渠道 (两个版本都需要)
        org.librelab.calendar.alarm.ReminderNotifications.ensureChannel(this)
        // 预取今年 + 前后各 2 年的节假日数据 (所有 flavor 均从 holiday-cn 镜像拉取)
        val currentYear = java.time.LocalDate.now().year
        CoroutineScope(Dispatchers.IO).launch {
            org.librelab.calendar.lunar.CalendarInfo.refreshOnline(currentYear - 1, currentYear + 2)
        }
        // 预热 Room: 首次查询初始化数据库, 避免滑动时首查卡顿
        CoroutineScope(Dispatchers.IO).launch {
            try {
                database.eventDao().getRange(0, 1)
            } catch (_: Exception) {
            }
        }
    }
}
