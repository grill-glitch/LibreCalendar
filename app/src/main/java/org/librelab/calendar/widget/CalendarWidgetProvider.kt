package org.librelab.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import org.librelab.calendar.CalendarApp
import org.librelab.calendar.R
import org.librelab.calendar.data.EventRepository
import org.librelab.calendar.lunar.CalendarInfo
import org.librelab.calendar.lunar.HolidaySettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 桌面小组件: 2x1 与 4x2, 添加前经配置页选择月/周/日视图。
 * 渲染: 月视图网格(有事件的日期画圈)、周视图 7 天、日视图 + 当天事件。
 */
open class CalendarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 数据读取走 IO, 避免主线程 Room/农历计算
        val views = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            appWidgetIds.map { id -> id to buildViews(context, id) }
        }
        views.forEach { (id, rv) -> appWidgetManager.updateAppWidget(id, rv) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context)
    }

    /** 供外部 (配置页/日期变化) 刷新单个 widget */
    fun updateWidget(context: Context, widgetId: Int) {
        val rv = runBlocking(kotlinx.coroutines.Dispatchers.IO) { buildViews(context, widgetId) }
        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, rv)
    }

    private fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        update(context, mgr, mgr.getAppWidgetIds(ComponentName(context, CalendarWidgetProvider::class.java)))
    }

    private fun update(
        context: Context,
        mgr: AppWidgetManager,
        ids: IntArray,
    ) {
        onUpdate(context, mgr, ids)
    }

    /** 尺寸: 由 provider 的 widgetInfo 决定 (2x1 / 4x2) */
    private fun widgetSize(context: Context, widgetId: Int): String {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
        return if (info?.provider?.className?.contains("4x2") == true) "4x2" else "2x1"
    }

    /** 配置的视图类型 (默认月视图) */
    private fun viewType(context: Context, widgetId: Int): String {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        return prefs.getString("view_$widgetId", "month") ?: "month"
    }

    private suspend fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val size = widgetSize(context, widgetId)
        val view = viewType(context, widgetId)
        val layout = when (size to view) {
            "2x1" to "week" -> R.layout.widget_2x1_week
            "2x1" to "day" -> R.layout.widget_2x1_day
            "4x2" to "week" -> R.layout.widget_4x2_week
            "4x2" to "day" -> R.layout.widget_4x2_day
            "4x2" to "month" -> R.layout.widget_4x2_month
            else -> R.layout.widget_2x1_month
        }
        val views = RemoteViews(context.packageName, layout)
        val today = LocalDate.now()
        val app = context.applicationContext as CalendarApp
        val repo = EventRepository(app.database.eventDao(), app.database.labelDao())

        // 事件日期集合 (本月/本周/今天) + 今天事件 (日视图)
        val monthStart = today.withDayOfMonth(1)
        val rangeFrom = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rangeTo = monthStart.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = repo.getRange(rangeFrom, rangeTo)
        val eventDates = events.map {
            Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        val todayEvents = events
            .mapNotNull {
                val d = Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                if (d == today) it else null
            }
            .sortedBy { it.startTime }
            .take(3)

        val info = CalendarInfo.dayInfo(today)
        val holiday = info.holidayNames.ifBlank {
            if (info.isWorkday) "调休上班" else if (info.isFree) "放假" else ""
        }

        when (view) {
            "month" -> renderMonth(views, today, eventDates, size)
            "week" -> renderWeek(views, today, eventDates, size)
            "day" -> renderDay(views, today, info, holiday, todayEvents, size)
        }

        // 点击打开日历
        val open = Intent(context, org.librelab.calendar.MainActivity::class.java)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            android.app.PendingIntent.getActivity(
                context, widgetId, open,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        return views
    }

    // ---- 月视图 ----

    private fun renderMonth(views: RemoteViews, today: LocalDate, eventDates: Set<LocalDate>, size: String) {
        if (size == "2x1") {
            // 2x1: 月标题 + 今天 + 农历/节日
            views.setTextViewText(R.id.widget_title, "${today.monthValue}月")
            views.setTextViewText(R.id.widget_day, today.dayOfMonth.toString())
            views.setTextViewText(R.id.widget_lunar, CalendarInfo.dayInfo(today).lunarDayName)
            views.setTextViewText(R.id.widget_holiday, holidayShort(today))
            return
        }
        // 4x2: 完整 6x7 网格
        views.setTextViewText(R.id.widget_title, "${today.year}年${today.monthValue}月")
        views.setTextViewText(R.id.widget_lunar, "${CalendarInfo.dayInfo(today).lunarMonthName}${CalendarInfo.dayInfo(today).lunarDayName}")
        val first = today.withDayOfMonth(1)
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        for (r in 0..5) {
            for (c in 0..6) {
                val date = gridStart.plusDays((r * 7 + c).toLong())
                val id = cellId(r, c)
                views.setTextViewText(id, date.dayOfMonth.toString())
                val isToday = date == today
                val hasEvent = date in eventDates
                when {
                    isToday -> views.setInt(id, "setBackgroundResource", R.drawable.widget_day_today)
                    hasEvent -> views.setInt(id, "setBackgroundResource", R.drawable.widget_day_ring)
                    else -> views.setInt(id, "setBackgroundResource", 0)
                }
                // 非本月日期弱化
                val color = if (date.monthValue != today.monthValue) 0x66FFFFFF.toInt() else 0xE6FFFFFF.toInt()
                views.setTextColor(id, color)
                if (isToday) views.setTextColor(id, 0xFF1A1A1A.toInt())
            }
        }
    }

    // ---- 周视图 ----

    private fun renderWeek(views: RemoteViews, today: LocalDate, eventDates: Set<LocalDate>, size: String) {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        if (size == "2x1") {
            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                val id = weekDayId(i)
                views.setTextViewText(id, date.dayOfMonth.toString())
                applyDayStyle(views, id, date, today, eventDates)
            }
            return
        }
        views.setTextViewText(
            R.id.widget_title,
            "${monday.monthValue}月${monday.dayOfMonth}日 - ${monday.plusDays(6).monthValue}月${monday.plusDays(6).dayOfMonth}日",
        )
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            views.setTextViewText(weekNameId(i), "周${"一二三四五六日"[i]}")
            val id = weekDayId(i)
            views.setTextViewText(id, date.dayOfMonth.toString())
            applyDayStyle(views, id, date, today, eventDates)
        }
    }

    private fun applyDayStyle(views: RemoteViews, id: Int, date: LocalDate, today: LocalDate, eventDates: Set<LocalDate>) {
        when {
            date == today -> {
                views.setInt(id, "setBackgroundResource", R.drawable.widget_day_today)
                views.setTextColor(id, 0xFF1A1A1A.toInt())
            }
            date in eventDates -> {
                views.setInt(id, "setBackgroundResource", R.drawable.widget_day_ring)
                views.setTextColor(id, 0xFFFFFFFF.toInt())
            }
            else -> {
                views.setInt(id, "setBackgroundResource", 0)
                views.setTextColor(id, 0xFFFFFFFF.toInt())
            }
        }
    }

    // ---- 日视图 ----

    private fun renderDay(
        views: RemoteViews,
        today: LocalDate,
        info: org.librelab.calendar.lunar.DayInfo,
        holiday: String,
        todayEvents: List<org.librelab.calendar.data.CalendarEvent>,
        size: String,
    ) {
        val weekday = "周${"一二三四五六日"[today.dayOfWeek.value - 1]}"
        val lunar = "${info.lunarMonthName}${info.lunarDayName}"
        if (size == "2x1") {
            views.setTextViewText(R.id.widget_bigday, today.dayOfMonth.toString())
            views.setTextViewText(R.id.widget_lunar, lunar)
            views.setTextViewText(R.id.widget_holiday, holiday)
            return
        }
        views.setTextViewText(R.id.widget_bigday, today.dayOfMonth.toString())
        views.setTextViewText(R.id.widget_weekday, "$weekday · ${today.monthValue}月")
        views.setTextViewText(R.id.widget_lunar, lunar)
        views.setTextViewText(R.id.widget_holiday, holiday)
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        for (i in 0..2) {
            val id = eventId(i)
            if (i < todayEvents.size) {
                val e = todayEvents[i]
                val time = if (e.allDay) "全天" else Instant.ofEpochMilli(e.startTime)
                    .atZone(ZoneId.systemDefault()).format(fmt)
                views.setTextViewText(id, "$time  ${e.title}")
                views.setViewVisibility(id, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(id, android.view.View.GONE)
            }
        }
    }

    // ---- id 映射 ----

    private fun cellId(row: Int, col: Int): Int {
        val ids = arrayOf(
            R.id.cell00, R.id.cell01, R.id.cell02, R.id.cell03, R.id.cell04, R.id.cell05, R.id.cell06,
            R.id.cell10, R.id.cell11, R.id.cell12, R.id.cell13, R.id.cell14, R.id.cell15, R.id.cell16,
            R.id.cell20, R.id.cell21, R.id.cell22, R.id.cell23, R.id.cell24, R.id.cell25, R.id.cell26,
            R.id.cell30, R.id.cell31, R.id.cell32, R.id.cell33, R.id.cell34, R.id.cell35, R.id.cell36,
            R.id.cell40, R.id.cell41, R.id.cell42, R.id.cell43, R.id.cell44, R.id.cell45, R.id.cell46,
            R.id.cell50, R.id.cell51, R.id.cell52, R.id.cell53, R.id.cell54, R.id.cell55, R.id.cell56,
        )
        return ids[row * 7 + col]
    }

    private fun weekNameId(i: Int): Int = arrayOf(
        R.id.wn0, R.id.wn1, R.id.wn2, R.id.wn3, R.id.wn4, R.id.wn5, R.id.wn6,
    )[i]

    private fun weekDayId(i: Int): Int = arrayOf(
        R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6,
    )[i]

    private fun eventId(i: Int): Int = arrayOf(R.id.event0, R.id.event1, R.id.event2)[i]

    private fun holidayShort(date: LocalDate): String {
        val info = CalendarInfo.dayInfo(date)
        return when {
            info.holidayNames.isNotBlank() -> info.holidayNames
            info.isWorkday -> "班"
            info.isFree -> "休"
            else -> ""
        }
    }

    companion object {
        /** 供配置页/外部刷新 */
        fun refresh(context: Context, widgetId: Int) {
            CalendarWidgetProvider().updateWidget(context, widgetId)
        }
    }
}
