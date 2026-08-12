package org.librelab.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LunarCalendarTest {

    @Test
    fun `solar to lunar known dates`() {
        // 2024-02-10 = 甲辰年正月初一 (春节)
        val d1 = LunarCalendar.solarToLunar(LocalDate.of(2024, 2, 10))
        assertEquals(2024, d1.year)
        assertEquals(1, d1.month)
        assertEquals(false, d1.isLeap)
        assertEquals(1, d1.day)
        assertEquals("甲辰", d1.yearGanzhi)
        assertEquals("龙", d1.zodiac)

        // 2025-01-29 = 乙巳年正月初一 (春节)
        val d2 = LunarCalendar.solarToLunar(LocalDate.of(2025, 1, 29))
        assertEquals(2025, d2.year)
        assertEquals(1, d2.month)
        assertEquals(1, d2.day)
        assertEquals("乙巳", d2.yearGanzhi)
        assertEquals("蛇", d2.zodiac)

        // 2024-06-10 = 甲辰年五月初五 (端午节)
        val d3 = LunarCalendar.solarToLunar(LocalDate.of(2024, 6, 10))
        assertEquals(5, d3.month)
        assertEquals(5, d3.day)

        // 2024-09-17 = 甲辰年八月十五 (中秋节)
        val d4 = LunarCalendar.solarToLunar(LocalDate.of(2024, 9, 17))
        assertEquals(8, d4.month)
        assertEquals(15, d4.day)
    }

    @Test
    fun `solar to lunar leap month`() {
        // 2023 闰二月: 2023-03-22 = 闰二月初一
        val d = LunarCalendar.solarToLunar(LocalDate.of(2023, 3, 22))
        assertEquals(2, d.month)
        assertEquals(true, d.isLeap)
        assertEquals(1, d.day)
        assertEquals("闰二月", d.monthName)
    }

    @Test
    fun `lunar to solar roundtrip`() {
        // 2024 春节 正月初一 → 2024-02-10
        assertEquals(
            LocalDate.of(2024, 2, 10),
            LunarCalendar.lunarToSolar(2024, 1, false, 1)
        )
        // 2025 春节
        assertEquals(
            LocalDate.of(2025, 1, 29),
            LunarCalendar.lunarToSolar(2025, 1, false, 1)
        )
        // 2023 闰二月初一 → 2023-03-22
        assertEquals(
            LocalDate.of(2023, 3, 22),
            LunarCalendar.lunarToSolar(2023, 2, true, 1)
        )
    }

    @Test
    fun `month names`() {
        assertEquals("正月", LunarCalendar.solarToLunar(LocalDate.of(2024, 2, 10)).monthName)
        assertEquals("冬月", LunarCalendar.solarToLunar(LocalDate.of(2024, 1, 10)).monthName)
        assertEquals("腊月", LunarCalendar.solarToLunar(LocalDate.of(2025, 1, 10)).monthName)
        assertEquals("廿九", LunarCalendar.solarToLunar(LocalDate.of(2025, 1, 28)).dayName) // 2025 除夕(腊月廿九)
    }
}
