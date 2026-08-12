package org.librelab.calendar.lunar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 与 date-chinese (astronomia 完整 VSOP87) 输出的节气日期对照 (北京时间 UTC+8)。
 * date-chinese term: 1=立春 4=春分 5=清明 22=冬至
 * 本实现 index (0-23): 0=小寒 2=立春 5=春分 6=清明 23=冬至
 */
class SolarTermsTest {

    @Test
    fun `qingming matches astronomia 2000-2032`() {
        val expected = mapOf(
            2000 to LocalDate.of(2000, 4, 4), 2001 to LocalDate.of(2001, 4, 5),
            2002 to LocalDate.of(2002, 4, 5), 2003 to LocalDate.of(2003, 4, 5),
            2004 to LocalDate.of(2004, 4, 4), 2005 to LocalDate.of(2005, 4, 5),
            2006 to LocalDate.of(2006, 4, 5), 2007 to LocalDate.of(2007, 4, 5),
            2008 to LocalDate.of(2008, 4, 4), 2009 to LocalDate.of(2009, 4, 4),
            2010 to LocalDate.of(2010, 4, 5), 2011 to LocalDate.of(2011, 4, 5),
            2012 to LocalDate.of(2012, 4, 4), 2013 to LocalDate.of(2013, 4, 4),
            2014 to LocalDate.of(2014, 4, 5), 2015 to LocalDate.of(2015, 4, 5),
            2016 to LocalDate.of(2016, 4, 4), 2017 to LocalDate.of(2017, 4, 4),
            2018 to LocalDate.of(2018, 4, 5), 2019 to LocalDate.of(2019, 4, 5),
            2020 to LocalDate.of(2020, 4, 4), 2021 to LocalDate.of(2021, 4, 4),
            2022 to LocalDate.of(2022, 4, 5), 2023 to LocalDate.of(2023, 4, 5),
            2024 to LocalDate.of(2024, 4, 4), 2025 to LocalDate.of(2025, 4, 4),
            2026 to LocalDate.of(2026, 4, 5), 2027 to LocalDate.of(2027, 4, 5),
            2028 to LocalDate.of(2028, 4, 4), 2029 to LocalDate.of(2029, 4, 4),
            2030 to LocalDate.of(2030, 4, 5), 2031 to LocalDate.of(2031, 4, 5),
            2032 to LocalDate.of(2032, 4, 4)
        )
        for ((y, d) in expected) {
            assertEquals("qingming $y", d, SolarTerms.solarTermDateCn(y, 6))
        }
    }

    @Test
    fun `chunfen matches astronomia`() {
        // 春分 index=5
        assertEquals(LocalDate.of(2024, 3, 20), SolarTerms.solarTermDateCn(2024, 5))
        assertEquals(LocalDate.of(2025, 3, 20), SolarTerms.solarTermDateCn(2025, 5))
        assertEquals(LocalDate.of(2026, 3, 20), SolarTerms.solarTermDateCn(2026, 5))
    }

    @Test
    fun `dongzhi matches astronomia`() {
        // 冬至 index=23
        assertEquals(LocalDate.of(2024, 12, 21), SolarTerms.solarTermDateCn(2024, 23))
        assertEquals(LocalDate.of(2025, 12, 21), SolarTerms.solarTermDateCn(2025, 23))
        assertEquals(LocalDate.of(2026, 12, 22), SolarTerms.solarTermDateCn(2026, 23))
    }

    @Test
    fun `lichun matches astronomia`() {
        // 立春 index=2
        assertEquals(LocalDate.of(2024, 2, 4), SolarTerms.solarTermDateCn(2024, 2))
        assertEquals(LocalDate.of(2025, 2, 3), SolarTerms.solarTermDateCn(2025, 2))
        assertEquals(LocalDate.of(2026, 2, 4), SolarTerms.solarTermDateCn(2026, 2))
    }

    @Test
    fun `names array`() {
        assertEquals("清明", SolarTerms.NAMES[6])
        assertEquals("冬至", SolarTerms.NAMES[23])
        assertEquals("小寒", SolarTerms.NAMES[0])
        assertEquals(24, SolarTerms.NAMES.size)
    }
}
