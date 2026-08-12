package org.librelab.calendar.lunar

import java.time.LocalDate

/**
 * 中国节假日引擎 — 规则移植自 date-holidays 的 CN.yaml (commenthol/date-holidays, ISC/CC-BY-3.0)。
 * 完全实时计算: 固定日期 + 农历日期 + 节气, 不依赖任何静态 JSON。
 *
 * 规则来源: https://github.com/commenthol/date-holidays/blob/master/data/countries/CN.yaml
 *  - 固定日期: 01-01 元旦, 03-08 妇女节(半天), 05-01 劳动节, 05-04 青年节(半天),
 *    06-01 儿童节, 08-01 建军节(半天), 10-01~03 国庆节
 *  - 农历: 除夕(正月初一前夜), 春节(正月初一/初二), 端午(五月初五), 中秋(八月十五)
 *  - 节气: 清明(第 5 个节气)
 */
object ChineseHolidays {

    data class Holiday(
        val date: LocalDate,
        val name: String,
        val type: String = "public", // public = 法定节假日
    )

    /** 某年所有中国节假日 (已按日期排序) */
    fun holidaysOfYear(year: Int): List<Holiday> {
        val result = mutableListOf<Holiday>()

        // ---- 固定日期 ----
        result += Holiday(LocalDate.of(year, 1, 1), "元旦")
        result += Holiday(LocalDate.of(year, 3, 8), "国际妇女节")
        result += Holiday(LocalDate.of(year, 5, 1), "劳动节")
        result += Holiday(LocalDate.of(year, 5, 4), "青年节")
        result += Holiday(LocalDate.of(year, 6, 1), "六一儿童节")
        result += Holiday(LocalDate.of(year, 8, 1), "建军节")
        result += Holiday(LocalDate.of(year, 10, 1), "国庆节")
        result += Holiday(LocalDate.of(year, 10, 2), "国庆节")
        result += Holiday(LocalDate.of(year, 10, 3), "国庆节")

        // ---- 农历节日 ----
        val spring = LunarCalendar.lunarToSolar(year, 1, false, 1) // 正月初一
        result += Holiday(spring.minusDays(1), "春节") // 除夕
        result += Holiday(spring, "春节")
        result += Holiday(spring.plusDays(1), "春节") // 正月初二
        result += Holiday(LunarCalendar.lunarToSolar(year, 5, false, 5), "端午节")  // 五月初五
        result += Holiday(LunarCalendar.lunarToSolar(year, 8, false, 15), "中秋节") // 八月十五

        // ---- 节气: 清明 (第 7 个节气, index 6) ----
        result += Holiday(SolarTerms.termDates(year)[6], "清明节")

        // ---- 官方调休安排 (date-holidays CN.yaml 硬编码的年度特殊日期) ----
        // 2021 年国务院放假安排: 元旦/春节/清明/劳动节/端午/中秋/国庆 多天连休
        if (year == 2021) {
            result += Holiday(LocalDate.of(2021, 1, 2), "元旦")
            result += Holiday(LocalDate.of(2021, 1, 3), "元旦")
            result += Holiday(LocalDate.of(2021, 2, 14), "春节")
            result += Holiday(LocalDate.of(2021, 2, 15), "春节")
            result += Holiday(LocalDate.of(2021, 2, 16), "春节")
            result += Holiday(LocalDate.of(2021, 2, 17), "春节")
            result += Holiday(LocalDate.of(2021, 4, 3), "清明节")
            result += Holiday(LocalDate.of(2021, 4, 5), "清明节")
            result += Holiday(LocalDate.of(2021, 5, 2), "劳动节")
            result += Holiday(LocalDate.of(2021, 5, 3), "劳动节")
            result += Holiday(LocalDate.of(2021, 5, 4), "劳动节")
            result += Holiday(LocalDate.of(2021, 5, 5), "劳动节")
            result += Holiday(LocalDate.of(2021, 6, 12), "端午节")
            result += Holiday(LocalDate.of(2021, 6, 13), "端午节")
            result += Holiday(LocalDate.of(2021, 9, 19), "中秋节")
            result += Holiday(LocalDate.of(2021, 9, 20), "中秋节")
            result += Holiday(LocalDate.of(2021, 10, 4), "国庆节")
            result += Holiday(LocalDate.of(2021, 10, 5), "国庆节")
            result += Holiday(LocalDate.of(2021, 10, 6), "国庆节")
            result += Holiday(LocalDate.of(2021, 10, 7), "国庆节")
        }

        return result.distinctBy { it.date to it.name }.sortedBy { it.date }
    }

    /** 某天的节假日 (可能多个, 如国庆连休每天都是"国庆节") */
    fun holidaysOn(date: LocalDate): List<Holiday> =
        holidaysOfYear(date.year).filter { it.date == date }

    /** 某天是否是法定节假日 */
    fun isHoliday(date: LocalDate): Boolean = holidaysOnCached(date).isNotEmpty()

    /** 某天的节假日名称 (多个时用顿号连接), 无则返回农历日期文字 */
    fun holidayLabel(date: LocalDate): String {
        val h = holidaysOnCached(date)
        return if (h.isNotEmpty()) h.joinToString(" ") { it.name }
        else ""
    }

    /** 缓存: 每年只算一次 (线程安全) */
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, List<Holiday>>()

    fun holidaysOfYearCached(year: Int): List<Holiday> =
        cache.getOrPut(year) { holidaysOfYear(year) }

    fun holidaysOnCached(date: LocalDate): List<Holiday> =
        holidaysOfYearCached(date.year).filter { it.date == date }
}
