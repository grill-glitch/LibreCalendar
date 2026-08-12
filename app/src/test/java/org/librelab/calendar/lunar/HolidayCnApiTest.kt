package org.librelab.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidayCnApiTest {

    private val sample = """
        {"year":2026,"days":[
          {"name":"元旦","date":"2026-01-01","isOffDay":true},
          {"name":"春节前调休","date":"2026-02-15","isOffDay":false},
          {"name":"春节","date":"2026-02-16","isOffDay":true},
          {"name":"劳动节","date":"2026-05-01","isOffDay":true}
        ]}
    """.trimIndent()

    @Test
    fun `parseYear converts isOffDay to freeday and workday`() {
        val data = HolidayCnApi.parseYear(sample, 2026)
        assertEquals(2026, data!!.year)
        // 放假: 1/1, 2/16, 5/1 → dayOfYear 1, 47, 121
        assertTrue(data.freeday.contains(1))
        assertTrue(data.freeday.contains(47))
        assertTrue(data.freeday.contains(121))
        assertEquals(3, data.freeday.size)
        // 调休: 2/15 → dayOfYear 46
        assertTrue(data.workday.contains(46))
        assertEquals(1, data.workday.size)
    }

    @Test
    fun `parseYear ignores dates of other years`() {
        val text = """
            {"year":2026,"days":[{"name":"跨年","date":"2025-12-31","isOffDay":true}]}
        """.trimIndent()
        assertNull(HolidayCnApi.parseYear(text, 2026))
    }

    @Test
    fun `parseYear returns null on empty or malformed`() {
        assertNull(HolidayCnApi.parseYear("{\"year\":2026,\"days\":[]}", 2026))
        assertNull(HolidayCnApi.parseYear("not json", 2026))
    }

    @Test
    fun `isFree and isWorkday check by date`() {
        val data = HolidayCnApi.parseYear(sample, 2026)!!
        val jan1 = java.time.LocalDate.of(2026, 1, 1)
        val feb15 = java.time.LocalDate.of(2026, 2, 15)
        val feb16 = java.time.LocalDate.of(2026, 2, 16)
        assertTrue(data.isFree(jan1))
        assertTrue(data.isFree(feb16))
        assertTrue(data.isWorkday(feb15))
        // 非节假日
        assertTrue(!data.isFree(feb15))
        assertTrue(!data.isWorkday(jan1))
    }
}
