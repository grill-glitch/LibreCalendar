package org.librelab.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventRepositoryTest {

    private fun event(
        id: Long, startMillis: Long, repeat: RepeatRule, reminderMinutes: Int = 0,
    ) = CalendarEvent(
        id = id,
        title = "测试",
        startTime = startMillis,
        endTime = startMillis + 3600_000L,
        repeat = repeat.name,
        reminderMinutes = reminderMinutes,
    )

    /** 2026-07-30 09:00 本地时间 */
    private fun at20260730_0900(): Long =
        LocalDate.of(2026, 7, 30).atTime(9, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `non repeating event only in range`() {
        val repo = EventRepository(dao = FakeDao(), labelDao = FakeLabelDao())
        val t = at20260730_0900()
        val e = event(1, t, RepeatRule.NONE)
        // 在范围内
        assertEquals(1, repo.expandEvent(e, t - 1000, t + 1000).size)
        // 在范围外 (结束早于 from)
        assertEquals(0, repo.expandEvent(e, t + 7200_000, t + 7200_000 + 1000).size)
    }

    @Test
    fun `daily repeat expands`() {
        val repo = EventRepository(dao = FakeDao(), labelDao = FakeLabelDao())
        val t = at20260730_0900()
        val e = event(1, t, RepeatRule.DAILY)
        // 3 天范围 → 3 个实例
        val from = t - 1000
        val to = t + 3 * 24 * 3600_000L
        val instances = repo.expandEvent(e, from, to)
        assertEquals(3, instances.size)
        assertEquals(t, instances[0].startTime)
        assertEquals(t + 24 * 3600_000L, instances[1].startTime)
        assertEquals(t + 2 * 24 * 3600_000L, instances[2].startTime)
    }

    @Test
    fun `weekly repeat expands`() {
        val repo = EventRepository(dao = FakeDao(), labelDao = FakeLabelDao())
        val t = at20260730_0900() // 周四
        val e = event(1, t, RepeatRule.WEEKLY)
        val to = t + 15 * 24 * 3600_000L
        val instances = repo.expandEvent(e, t - 1000, to)
        assertEquals(3, instances.size) // 第 0/7/14 天
    }

    @Test
    fun `monthly repeat expands`() {
        val repo = EventRepository(dao = FakeDao(), labelDao = FakeLabelDao())
        val t = at20260730_0900()
        val e = event(1, t, RepeatRule.MONTHLY)
        val to = t + 70 * 24 * 3600_000L // ~2.3 个月
        val instances = repo.expandEvent(e, t - 1000, to)
        // 7/30, 8/30, 9/30
        assertEquals(3, instances.size)
    }

    @Test
    fun `yearly repeat expands`() {
        val repo = EventRepository(dao = FakeDao(), labelDao = FakeLabelDao())
        val t = at20260730_0900()
        val e = event(1, t, RepeatRule.YEARLY)
        val to = t + 366L * 2 * 24 * 3600_000L
        val instances = repo.expandEvent(e, t - 1000, to)
        assertEquals(3, instances.size) // 2026/2027/2028
    }

    /** 最小 FakeDao: expandEvent 只依赖 DAO 接口但不调用 (纯内存展开) */
    private class FakeDao : EventDao {
        override suspend fun getById(id: Long) = null
        override fun observeRange(from: Long, to: Long): kotlinx.coroutines.flow.Flow<List<CalendarEvent>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<CalendarEvent>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getRange(from: Long, to: Long) = emptyList<CalendarEvent>()
        override suspend fun insert(event: CalendarEvent) = 0L
        override suspend fun update(
            id: Long, title: String, description: String, location: String,
            startTime: Long, endTime: Long, allDay: Boolean, repeat: String,
            reminderMinutes: Int, colorIndex: Int, customColor: Int?, labelId: Long?,
        ) {}
        override suspend fun delete(id: Long) {}
    }

    /** 最小 FakeLabelDao: 本测试不使用标签 */
    private class FakeLabelDao : LabelDao {
        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<Label>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getAll() = emptyList<Label>()
        override suspend fun insert(label: Label) = 0L
        override suspend fun update(id: Long, name: String, color: Int?, icon: String?, sortOrder: Int) {}
        override suspend fun delete(id: Long) {}
    }
}
