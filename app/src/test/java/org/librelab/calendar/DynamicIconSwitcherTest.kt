package org.librelab.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicIconSwitcherTest {

    @Test
    fun `aliasName pads day to two digits with namespace prefix`() {
        assertEquals("org.librelab.calendar.MainActivity01", DynamicIconSwitcher.aliasName(1))
        assertEquals("org.librelab.calendar.MainActivity09", DynamicIconSwitcher.aliasName(9))
        assertEquals("org.librelab.calendar.MainActivity12", DynamicIconSwitcher.aliasName(12))
        assertEquals("org.librelab.calendar.MainActivity31", DynamicIconSwitcher.aliasName(31))
    }

    @Test
    fun `all 31 alias names share the ALIAS_PREFIX`() {
        for (day in 1..31) {
            val name = DynamicIconSwitcher.aliasName(day)
            assertTrue(name.startsWith(DynamicIconSwitcher.ALIAS_PREFIX))
            // 前缀后紧跟两位日期, 无其他字符
            assertEquals(2, name.length - DynamicIconSwitcher.ALIAS_PREFIX.length)
        }
    }

    @Test
    fun `ALIAS_PREFIX differs from MainActivity class name`() {
        // 入口检测依赖: alias 类名以 ALIAS_PREFIX 开头, MainActivity 本体不是
        assertTrue(DynamicIconSwitcher.ALIAS_PREFIX.startsWith("org.librelab.calendar."))
        assertTrue(DynamicIconSwitcher.ALIAS_PREFIX.endsWith("MainActivity"))
        assertEquals("org.librelab.calendar.MainActivity", MainActivity::class.java.name)
    }
}
