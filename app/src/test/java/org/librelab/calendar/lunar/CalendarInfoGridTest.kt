package org.librelab.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarInfoGridTest {

    @Test
    fun `month grid starts on monday`() {
        // 2026-07-01 是周三 → 网格应从 6/29 周一开始
        val grid = CalendarInfo.monthGrid(2026, 7)
        assertEquals(LocalDate.of(2026, 6, 29), grid[0])
        assertEquals(1, grid[0].dayOfWeek.value) // 周一
        assertEquals(LocalDate.of(2026, 7, 5), grid[6]) // 第一行末尾是周日
        assertEquals(7, grid[6].dayOfWeek.value)
        // 42 格, 8/1 周六应在第 6 列 (index%7==5)
        val aug1 = grid.indexOf(LocalDate.of(2026, 8, 1))
        assertEquals(5, aug1 % 7)
    }

    @Test
    fun `month grid month starts on sunday`() {
        // 2026-01-01 是周四? 查: 2026-01-01 实为周四 → 网格从 12/29 周一开始
        val grid = CalendarInfo.monthGrid(2026, 1)
        assertEquals(1, grid[0].dayOfWeek.value)
        // 2026-11-01 是周日 → 网格第一格是 10/26 周一, 11/1 在 index 6
        val nov = CalendarInfo.monthGrid(2026, 11)
        assertEquals(LocalDate.of(2026, 10, 26), nov[0])
        assertEquals(1, nov[0].dayOfWeek.value)
        assertEquals(LocalDate.of(2026, 11, 1), nov[6])
        assertEquals(7, nov[6].dayOfWeek.value)
    }
}
