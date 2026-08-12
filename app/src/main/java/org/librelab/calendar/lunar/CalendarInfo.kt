package org.librelab.calendar.lunar

import org.librelab.calendar.BuildConfig
import java.time.LocalDate

/**
 * 单日完整信息: 公历 + 农历 + 节气 + 节假日(在线小米数据) + 调休。
 * UI 层唯一数据入口。
 */
data class DayInfo(
    val date: LocalDate,
    val lunar: LunarCalendar.LunarDate,
    /** 当天节气名 (无则 null) */
    val solarTerm: String?,
    /** 农历日名 (初一..三十) */
    val lunarDayName: String,
    /** 农历月名 (正月..腊月, 含闰) */
    val lunarMonthName: String,
    /** 节假日名 (多个顿号连接, 无则空) */
    val holidayNames: String,
    /** 是否放假 */
    val isFree: Boolean,
    /** 是否调休补班 */
    val isWorkday: Boolean,
)

/**
 * 日历信息聚合器: 农历(本地算法) + 节气(本地天文) + 节假日/调休(小米在线, 缓存)。
 * 在线数据带缓存, 离线时回退本地实时引擎 (ChineseHolidays)。
 */
object CalendarInfo {

    /** 在线节假日数据缓存: 年 -> YearData */
    @Volatile
    private var onlineCache: Map<Int, MiuiHolidayApi.YearData> = emptyMap()

    /** 已请求过但服务端没有的年份 (API 数据覆盖 2011-2026, 超出部分永远缺失, 避免无限重试) */
    private val attemptedYears = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    /** 同步拉取指定年份区间在线数据 (会阻塞网络, 放 IO 线程)。
     *  数据源按提供商选择: 小米日历 / holiday-cn(自定义镜像)。
     *  已缓存的年份跳过, 只拉缺失的 — 切月不重复请求。
     *  @return 是否更新了缓存 (true 时调用方可重建界面) */
    fun refreshOnline(fromYear: Int, toYear: Int): Boolean {
        val missing = (fromYear..toYear).filter { it !in onlineCache && it !in attemptedYears }
        if (missing.isEmpty()) return false
        val ctx = org.librelab.calendar.CalendarApp.appContext
        return try {
            val provider = HolidaySettings.getProvider(ctx)
            val data = when (provider) {
                DataProvider.MIUI -> MiuiHolidayApi.fetchYears(missing.first(), missing.last())
                DataProvider.HOLIDAY_CN -> HolidayCnApi.fetchYears(
                    missing.first(), missing.last(),
                    HolidaySettings.effectiveMirror(ctx),
                )
            }
            val updated = data.isNotEmpty()
            if (updated) {
                synchronized(this) {
                    onlineCache = onlineCache + data.associateBy { it.year }
                }
                // 记录下载状态: 来源 + 更新时间 (持久化, 设置页展示)
                val source = when (provider) {
                    DataProvider.MIUI -> DataProvider.MIUI.label
                    DataProvider.HOLIDAY_CN ->
                        "holiday-cn · ${HolidaySettings.effectiveMirror(ctx)}"
                }
                HolidaySettings.recordSync(ctx, source)
            }
            attemptedYears.addAll(missing) // 无论是否拿到都标记, 防止每次切月重复请求
            updated
        } catch (_: Exception) {
            // 网络失败不标记, 下次可重试
            false
        }
    }

    /** 某天节假日名 (在线数据只提供放假/补班标记, 名称统一来自本地引擎) */
    fun holidayName(date: LocalDate): String =
        ChineseHolidays.holidayLabel(date)

    /** 当天是否放假 (在线数据, 无则按本地引擎) */
    fun isFree(date: LocalDate): Boolean {
        val data = onlineCache[date.year]
        return if (data != null) data.isFree(date)
        else ChineseHolidays.isHoliday(date)
    }

    /** 当天是否调休补班 (仅在线数据, 无则 false) */
    fun isWorkday(date: LocalDate): Boolean {
        val data = onlineCache[date.year]
        return data?.isWorkday(date) ?: false
    }

    /** 清空在线缓存 (镜像源变更后调用, 使下次 refreshOnline 重新拉取) */
    fun resetOnlineData() {
        synchronized(this) {
            onlineCache = emptyMap()
        }
        attemptedYears.clear()
    }

    /** 完整单日信息 */
    fun dayInfo(date: LocalDate): DayInfo {
        val lunar = LunarCalendar.solarToLunar(date)
        val term = solarTermOf(date)
        return DayInfo(
            date = date,
            lunar = lunar,
            solarTerm = term,
            lunarDayName = lunar.dayName,
            lunarMonthName = lunar.monthName,
            holidayNames = holidayName(date),
            isFree = isFree(date),
            isWorkday = isWorkday(date),
        )
    }

    /** 当天是节气则返回名称 (用按年缓存, 不重复天文计算) */
    private fun solarTermOf(date: LocalDate): String? {
        val idx = SolarTerms.termDates(date.year).indexOf(date)
        return if (idx >= 0) SolarTerms.NAMES[idx] else null
    }

    /** 公历某月的日期列表 (含上月/下月补位, 每行周一~周日) */
    fun monthGrid(year: Int, month: Int): List<LocalDate> {
        val first = LocalDate.of(year, month, 1)
        // dayOfWeek.value: 周一=1...周日=7 → 转成 周一=0...周日=6
        val startDow = (first.dayOfWeek.value - 1) % 7
        val start = first.minusDays(startDow.toLong())
        return (0 until 42).map { start.plusDays(it.toLong()) }
    }
}
