package org.librelab.calendar.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 事件仓库: 封装 DAO + 重复事件展开。
 * 所有时间以 epoch millis 存储 (本地时区)。
 */
class EventRepository(private val dao: EventDao, private val labelDao: LabelDao) {

    fun observeRange(from: Long, to: Long): Flow<List<CalendarEvent>> = dao.observeRange(from, to)

    suspend fun getRange(from: Long, to: Long): List<CalendarEvent> = dao.getRange(from, to)

    fun observeAll(): Flow<List<CalendarEvent>> = dao.observeAll()

    suspend fun getById(id: Long): CalendarEvent? = dao.getById(id)

    suspend fun insert(event: CalendarEvent): Long = dao.insert(event)

    suspend fun update(event: CalendarEvent) {
        dao.update(
            id = event.id,
            title = event.title,
            description = event.description,
            location = event.location,
            startTime = event.startTime,
            endTime = event.endTime,
            allDay = event.allDay,
            repeat = event.repeat,
            reminderMinutes = event.reminderMinutes,
            colorIndex = event.colorIndex,
            customColor = event.customColor,
            labelId = event.labelId,
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    // ---- 标签 ----
    fun observeLabels(): Flow<List<Label>> = labelDao.observeAll()

    suspend fun getLabels(): List<Label> = labelDao.getAll()

    suspend fun saveLabel(label: Label): Long = labelDao.insert(label)

    suspend fun updateLabel(label: Label) {
        labelDao.update(label.id, label.name, label.color, label.icon, label.sortOrder)
    }

    suspend fun deleteLabel(id: Long) = labelDao.delete(id)

    /**
     * 将某重复事件展开为 [from, to) 区间内的具体发生实例。
     * 返回的实例带 eventId 与实际的开始/结束时间。
     */
    fun expandEvent(event: CalendarEvent, from: Long, to: Long): List<CalendarEvent> {
        if (event.repeat == RepeatRule.NONE.name) {
            return if (event.startTime < to && event.endTime > from) listOf(event) else emptyList()
        }
        val rule = RepeatRule.fromName(event.repeat)
        val zone = ZoneId.systemDefault()
        val startLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startTime), zone)
        val duration = event.endTime - event.startTime
        val instances = mutableListOf<CalendarEvent>()
        var cursor = startLocal
        val endDate = LocalDate.from(LocalDateTime.ofInstant(Instant.ofEpochMilli(to), zone))
        var guard = 0
        while (cursor.toLocalDate() <= endDate && guard < 500) {
            val startMillis = cursor.atZone(zone).toInstant().toEpochMilli()
            if (startMillis >= event.startTime && startMillis < to) {
                instances += event.copy(
                    id = event.id,
                    startTime = startMillis,
                    endTime = startMillis + duration,
                )
            }
            cursor = when (rule) {
                RepeatRule.DAILY -> cursor.plusDays(1)
                RepeatRule.WEEKLY -> cursor.plusWeeks(1)
                RepeatRule.MONTHLY -> cursor.plusMonths(1)
                RepeatRule.YEARLY -> cursor.plusYears(1)
                else -> return instances
            }
            guard++
        }
        return instances
    }
}
