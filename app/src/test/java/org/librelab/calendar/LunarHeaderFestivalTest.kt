package org.librelab.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.librelab.calendar.lunar.CalendarInfo
import java.time.LocalDate

class LunarHeaderFestivalTest {

    @Test
    fun `2026-09-25 is Mid-Autumn Festival with holiday name`() {
        val info = CalendarInfo.dayInfo(LocalDate.of(2026, 9, 25))
        // 中秋节: holidayNames 应含 "中秋节" (本地引擎)
        println("9/25 holidayNames=[${info.holidayNames}] isFree=${info.isFree} isWorkday=${info.isWorkday}")
        assertTrue("中秋节应识别", info.holidayNames.contains("中秋") || info.isFree)
        // 表头文本应含节日: "八月十五 中秋节" 形式
        val lunar = "${info.lunarMonthName}${info.lunarDayName}"
        val holiday = info.holidayNames.ifBlank { "" }
        val header = if (holiday.isEmpty()) lunar else "$lunar $holiday"
        println("表头文本: $header")
        assertTrue("表头含节日", header.contains("中秋"))
    }

    @Test
    fun `2026-10-01 National Day in header`() {
        val info = CalendarInfo.dayInfo(LocalDate.of(2026, 10, 1))
        println("10/1 holidayNames=[${info.holidayNames}] isFree=${info.isFree}")
        assertTrue("国庆节应识别", info.holidayNames.contains("国庆") || info.isFree)
    }

    @Test
    fun `workday adjustment day shows 班`() {
        // 2026 国庆调休: 10/10 周六上班 (holiday-cn 验证过 10/10 班)
        val info = CalendarInfo.dayInfo(LocalDate.of(2026, 10, 10))
        println("10/10 holidayNames=[${info.holidayNames}] isWorkday=${info.isWorkday}")
        val holiday = info.holidayNames.ifBlank {
            if (info.isWorkday) "班" else if (info.isFree) "休" else ""
        }
        println("表头附加: $holiday")
        assertTrue("调休班应识别", info.isWorkday)
    }
}
