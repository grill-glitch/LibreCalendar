package org.librelab.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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

    /** 尺寸: 由 provider 的 widgetInfo 决定 (2x1 / 4x1 / 4x2) */
    private fun widgetSize(context: Context, widgetId: Int): String {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)
        val cn = info?.provider?.className ?: ""
        return when {
            cn.contains("4x1") -> "4x1"
            cn.contains("4x2") -> "4x2"
            else -> "2x1"
        }
    }

    /** 视图类型: 各尺寸固定 — 2x1=日, 4x1=周, 4x2=月 (添加后不可切换) */
    private fun viewType(context: Context, widgetId: Int): String = when (widgetSize(context, widgetId)) {
        "2x1" -> "day"
        "4x1" -> "week"
        else -> "month"
    }

    private suspend fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val size = widgetSize(context, widgetId)
        val view = viewType(context, widgetId)
        val layout = when (size to view) {
            "2x1" to "day" -> R.layout.widget_2x1_day
            "4x1" to "week" -> R.layout.widget_4x1_week
            "4x2" to "week" -> R.layout.widget_4x2_week
            "4x2" to "day" -> R.layout.widget_4x2_day
            "4x2" to "month" -> R.layout.widget_4x2_month
            else -> R.layout.widget_2x1_day
        }
        val views = RemoteViews(context.packageName, layout)
        val today = LocalDate.now()
        val app = context.applicationContext as CalendarApp
        val repo = EventRepository(app.database.eventDao(), app.database.labelDao())

        // 事件日期集合 (本月) + 每日代表色 (应用内优先级链: 标签色 > customColor > hash)
        val monthStart = today.withDayOfMonth(1)
        val rangeFrom = monthStart.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rangeTo = monthStart.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = repo.getRange(rangeFrom, rangeTo)
        val labels = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            repo.observeLabels().first()
        }
        val labelMap = org.librelab.calendar.data.EventColors.labelMap(labels)
        val eventDates = events.map {
            Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSet()
        val eventDayColors = events.groupBy {
            Instant.ofEpochMilli(it.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, evs) ->
            org.librelab.calendar.data.EventColors.resolve(evs.first(), evs.first().labelId?.let(labelMap::get))
        }
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
            "month" -> renderMonth(views, today, eventDates, eventDayColors, size)
            "week" -> renderWeek(views, today, eventDates, eventDayColors, size)
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

    // ---- 月视图 (4x2): 与应用内一致 — 今天=primaryContainer 浅色圆, 事件日=事件色圆环, 假期红字 ----

    private fun renderMonth(
        views: RemoteViews,
        today: LocalDate,
        eventDates: Set<LocalDate>,
        eventDayColors: Map<LocalDate, Int>,
        size: String,
    ) {
        views.setTextViewText(R.id.widget_title, "${today.year}年${today.monthValue}月")
        views.setTextViewText(R.id.widget_lunar, "${CalendarInfo.dayInfo(today).lunarMonthName}${CalendarInfo.dayInfo(today).lunarDayName}")
        // 右上角星期
        views.setTextViewText(R.id.widget_weekday, "周${"一二三四五六日"[today.dayOfWeek.value - 1]}")
        val first = today.withDayOfMonth(1)
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        for (r in 0..5) {
            for (c in 0..6) {
                val date = gridStart.plusDays((r * 7 + c).toLong())
                val id = cellId(r, c)
                val ringId = ringId(r, c)
                views.setTextViewText(id, date.dayOfMonth.toString())
                val isToday = date == today
                val hasEvent = date in eventDates
                val isHoliday = !CalendarInfo.dayInfo(date).isWorkday && (
                    date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7 || CalendarInfo.dayInfo(date).isFree
                    )
                when {
                    isToday -> {
                        // 今天: primaryContainer 浅色圆 (应用内 isToday 样式) + 深色数字
                        views.setInt(ringId, "setImageResource", R.drawable.widget_circle_fill)
                        views.setInt(ringId, "setColorFilter", contextColor(R.color.widget_today_bg))
                        views.setTextColor(id, contextColor(R.color.widget_today_text))
                    }
                    hasEvent -> {
                        // 事件日: 事件色圆环 (应用内事件圈样式: 标签色 > customColor > hash)
                        views.setInt(ringId, "setImageResource", R.drawable.widget_circle_ring)
                        views.setInt(ringId, "setColorFilter", eventDayColors[date] ?: 0xFFFFFFFF.toInt())
                        views.setTextColor(id, contextColor(R.color.widget_text_primary))
                    }
                    else -> {
                        views.setInt(ringId, "setImageResource", 0)
                        views.setTextColor(
                            id,
                            when {
                                // 节假日/周末红字; 非本月补位格子淡红 (主页 error alpha 0.5 的红灰叠加)
                                isHoliday && date.monthValue == today.monthValue ->
                                    contextColor(R.color.widget_holiday)
                                isHoliday -> 0x80FFB4AB.toInt()
                                date.monthValue != today.monthValue -> contextColor(R.color.widget_text_dim)
                                else -> contextColor(R.color.widget_text_primary)
                            },
                        )
                    }
                }
            }
        }
    }

    // ---- 周视图 (4x1: 紧凑 7 天; 4x2: 标题 + 7 天) ----

    private fun renderWeek(
        views: RemoteViews,
        today: LocalDate,
        eventDates: Set<LocalDate>,
        eventDayColors: Map<LocalDate, Int>,
        size: String,
    ) {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        if (size == "4x1") {
            // 4x1: 标题行 + 与月视图一致的样式 — 今天浅圆, 事件色圆环, 假期红字 (仅本周 7 天)
            views.setTextViewText(R.id.widget_title, "${today.year}年${today.monthValue}月")
            views.setTextViewText(R.id.widget_lunar, "${CalendarInfo.dayInfo(today).lunarMonthName}${CalendarInfo.dayInfo(today).lunarDayName}")
            views.setTextViewText(R.id.widget_weekday, "周${"一二三四五六日"[today.dayOfWeek.value - 1]}")
            for (i in 0..6) {
                val date = monday.plusDays(i.toLong())
                val id = weekDayId(i)
                val ring = weekRingId(i)
                views.setTextViewText(id, date.dayOfMonth.toString())
                val isToday = date == today
                val hasEvent = date in eventDates
                val isHoliday = !CalendarInfo.dayInfo(date).isWorkday && (
                    date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7 || CalendarInfo.dayInfo(date).isFree
                    )
                when {
                    isToday -> {
                        views.setInt(ring, "setImageResource", R.drawable.widget_circle_fill)
                        views.setInt(ring, "setColorFilter", contextColor(R.color.widget_today_bg))
                        views.setTextColor(id, contextColor(R.color.widget_today_text))
                    }
                    hasEvent -> {
                        views.setInt(ring, "setImageResource", R.drawable.widget_circle_ring)
                        views.setInt(ring, "setColorFilter", eventDayColors[date] ?: 0xFFFFFFFF.toInt())
                        views.setTextColor(id, contextColor(R.color.widget_text_primary))
                    }
                    else -> {
                        views.setInt(ring, "setImageResource", 0)
                        views.setTextColor(
                            id,
                            if (isHoliday) contextColor(R.color.widget_holiday)
                            else contextColor(R.color.widget_text_primary),
                        )
                    }
                }
            }
            return
        }
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
                // 今天: 浅色圆 (primaryContainer) + 深色数字, 与月视图/主页一致
                views.setInt(id, "setBackgroundResource", R.drawable.widget_day_today)
                views.setTextColor(id, contextColor(R.color.widget_today_text))
            }
            date in eventDates -> {
                views.setInt(id, "setBackgroundResource", R.drawable.widget_day_ring)
                views.setTextColor(id, contextColor(R.color.widget_text_primary))
            }
            else -> {
                views.setInt(id, "setBackgroundResource", 0)
                views.setTextColor(id, contextColor(R.color.widget_text_primary))
            }
        }
    }

    // ---- 日视图 (2x1: 星期/日期圆/农历休班/今日事件; 4x2: 大日期 + 事件列表) ----

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
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        if (size == "2x1") {
            // 星期 + 日期 + 农历/休班 + 今日事件 (最多 2 条 + 共 x 个)
            views.setTextViewText(R.id.widget_weekday, weekday)
            views.setTextViewText(R.id.widget_day, today.dayOfMonth.toString())
            views.setTextViewText(R.id.widget_lunar, lunar)
            val workday = when {
                info.holidayNames.isNotBlank() -> info.holidayNames
                info.isWorkday -> "班"
                info.isFree -> "休"
                else -> ""
            }
            views.setTextViewText(R.id.widget_workday, workday)
            views.setViewVisibility(R.id.widget_workday, if (workday.isBlank()) android.view.View.GONE else android.view.View.VISIBLE)
            // 事件: 前 2 条 + "共 x 个事件"
            for (i in 0..1) {
                val id = if (i == 0) R.id.widget_event1 else R.id.widget_event2
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
            val more = todayEvents.size - 2
            if (more > 0) {
                views.setTextViewText(R.id.widget_event_more, "共 $more 个事件")
                views.setViewVisibility(R.id.widget_event_more, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_event_more, android.view.View.GONE)
            }
            return
        }
        views.setTextViewText(R.id.widget_bigday, today.dayOfMonth.toString())
        views.setTextViewText(R.id.widget_weekday, "$weekday · ${today.monthValue}月")
        views.setTextViewText(R.id.widget_lunar, lunar)
        views.setTextViewText(R.id.widget_holiday, holiday)
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

    /** 解析动态取色资源 (Android 12+ 系统动态色, 低版本静态色) */
    private fun contextColor(resId: Int): Int =
        org.librelab.calendar.CalendarApp.appContext.getColor(resId)

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


    private fun ringId(row: Int, col: Int): Int {
        val ids = arrayOf(
            R.id.ring00, R.id.ring01, R.id.ring02, R.id.ring03, R.id.ring04, R.id.ring05, R.id.ring06,
            R.id.ring10, R.id.ring11, R.id.ring12, R.id.ring13, R.id.ring14, R.id.ring15, R.id.ring16,
            R.id.ring20, R.id.ring21, R.id.ring22, R.id.ring23, R.id.ring24, R.id.ring25, R.id.ring26,
            R.id.ring30, R.id.ring31, R.id.ring32, R.id.ring33, R.id.ring34, R.id.ring35, R.id.ring36,
            R.id.ring40, R.id.ring41, R.id.ring42, R.id.ring43, R.id.ring44, R.id.ring45, R.id.ring46,
            R.id.ring50, R.id.ring51, R.id.ring52, R.id.ring53, R.id.ring54, R.id.ring55, R.id.ring56,
        )
        return ids[row * 7 + col]
    }

    private fun weekNameId(i: Int): Int = arrayOf(
        R.id.wn0, R.id.wn1, R.id.wn2, R.id.wn3, R.id.wn4, R.id.wn5, R.id.wn6,
    )[i]

    private fun weekDayId(i: Int): Int = arrayOf(
        R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6,
    )[i]

    private fun weekRingId(i: Int): Int = arrayOf(
        R.id.rg0, R.id.rg1, R.id.rg2, R.id.rg3, R.id.rg4, R.id.rg5, R.id.rg6,
    )[i]

    private fun eventId(i: Int): Int = arrayOf(R.id.event0, R.id.event1, R.id.event2)[i]

    companion object {
        /** 供配置页/外部刷新 */
        fun refresh(context: Context, widgetId: Int) {
            CalendarWidgetProvider().updateWidget(context, widgetId)
        }
    }
}
