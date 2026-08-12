package org.librelab.calendar.lunar

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 农历计算(1900-2100),基于经典农历数据表算法。
 * 数据表每个元素编码一个农历年:
 *  - bit0-3: 闰月月份(0=无闰月)
 *  - bit4-15: 每月天数(1=30天, 0=29天), bit4=正月 ... bit15=腊月
 *  - bit16: 闰月天数(1=30天, 0=29天)
 */
object LunarCalendar {

    // 1900-2100 农历年数据表
    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b5a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520                                                                                      // 2100
    )

    /** 公历 1900-01-31 = 农历 1900 正月初一, 偏移天数 */
    private const val BASE_JD = 2415021 // 1900-01-31 的儒略日(简化锚点)

    private val STEM = arrayOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    private val BRANCH = arrayOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪")
    private val MONTH_NAMES = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DAY_NAMES = arrayOf("初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十")

    data class LunarDate(
        val year: Int,       // 农历年
        val month: Int,      // 农历月 1-12
        val isLeap: Boolean, // 是否闰月
        val day: Int,        // 农历日 1-30
    ) {
        /** 农历月名, 如 "正月" "闰四月" */
        val monthName: String
            get() = (if (isLeap) "闰" else "") + MONTH_NAMES[month - 1] + "月"

        /** 农历日名, 如 "初一" "十五" */
        val dayName: String
            get() = DAY_NAMES[day - 1]

        /** 干支年, 如 "甲辰" */
        val yearGanzhi: String
            get() {
                // 1900 年为庚子年: 甲=0... 1900 → stemIndex=(1900-4)%10=6 庚, branch=(1900-4)%12=0 子
                val s = ((year - 4) % 10 + 10) % 10
                val b = ((year - 4) % 12 + 12) % 12
                return STEM[s] + BRANCH[b]
            }

        /** 生肖, 如 "龙" */
        val zodiac: String
            get() = ZODIAC[((year - 4) % 12 + 12) % 12]
    }

    /** 该农历年闰月月份, 0=无闰月 */
    private fun leapMonth(year: Int): Int = LUNAR_INFO[year - 1900] and 0xf

    /** 闰月天数 */
    private fun leapDays(year: Int): Int =
        if (leapMonth(year) == 0) 0
        else if ((LUNAR_INFO[year - 1900] and 0x10000) != 0) 30 else 29

    /** 农历年总天数 */
    private fun yearDays(year: Int): Int {
        var sum = 348 // 12 个月 × 29
        for (m in 1..12) if (monthDays(year, m) == 30) sum++
        return sum + leapDays(year)
    }

    /** 某农历月天数 */
    private fun monthDays(year: Int, month: Int): Int =
        if ((LUNAR_INFO[year - 1900] and (0x10000 shr month)) != 0) 30 else 29

    /** 农历 → 公历 */
    fun lunarToSolar(year: Int, month: Int, isLeap: Boolean, day: Int): LocalDate {
        var offset = 0
        for (y in 1900 until year) offset += yearDays(y)
        val lm = leapMonth(year)
        var i = 1
        while (i < month) {
            offset += monthDays(year, i)
            if (lm != 0 && i == lm) offset += leapDays(year) // 闰月紧随其月之后
            i++
        }
        if (isLeap) offset += monthDays(year, month) // 目标为闰月: 先过完该月
        offset += day - 1
        // 1900-01-31 + offset
        return LocalDate.of(1900, 1, 31).plusDays(offset.toLong())
    }

    /** 公历 → 农历 */
    fun solarToLunar(date: LocalDate): LunarDate {
        val offset = (date.toEpochDay() - LocalDate.of(1900, 1, 31).toEpochDay()).toInt()
        var year = 1900
        var days = yearDays(year)
        var remain = offset
        while (remain >= days) {
            remain -= days
            year++
            days = yearDays(year)
        }
        val lm = leapMonth(year)
        var month = 1
        var isLeap = false
        while (true) {
            val mDays = monthDays(year, month)
            if (remain < mDays) break
            remain -= mDays
            if (lm != 0 && month == lm) {
                // 闰月在此月之后
                val lDays = leapDays(year)
                if (remain < lDays) {
                    isLeap = true
                    break
                }
                remain -= lDays
            }
            month++
        }
        return LunarDate(year, month, isLeap, remain + 1)
    }
}
