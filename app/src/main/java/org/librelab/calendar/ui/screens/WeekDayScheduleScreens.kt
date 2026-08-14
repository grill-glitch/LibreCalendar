package org.librelab.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.librelab.calendar.data.CalendarEvent
import org.librelab.calendar.data.EventRepository
import org.librelab.calendar.lunar.CalendarInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** 某天事件 (展开重复实例后) 的时间文案 */
private fun eventTimeText(e: CalendarEvent): String =
    if (e.allDay) "全天"
    else Instant.ofEpochMilli(e.startTime).atZone(ZoneId.systemDefault()).format(TIME_FMT)

/** 周视图: 与月视图完全一致 (月份标题/星期表头/今日·选中高光/事件圆环/农历角标/事件卡片),
 *  仅把网格缩为本周 7 天。点击格子选中 → 下方复用月视图同款事件卡片。 */
@Composable
fun WeekScreen(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
) {
    // 当前显示周的锚点日期; 外部选中变化时跟随
    var weekAnchor by remember { mutableStateOf(selectedDate) }
    LaunchedEffect(selectedDate) { weekAnchor = selectedDate }
    val monday = weekAnchor.minusDays((weekAnchor.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val weekStart = monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val weekEnd = monday.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val eventsFlow = remember(monday) { repository.observeRange(weekStart, weekEnd) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val labelMap = remember(labels) { org.librelab.calendar.data.EventColors.labelMap(labels) }
    // 日期 -> 事件颜色 (与月视图 MonthPage 同逻辑: 同日多事件取第一个的颜色)
    val eventColors: Map<LocalDate, Int> = remember(events, labelMap) {
        events
            .map { e ->
                Instant.ofEpochMilli(e.startTime).atZone(ZoneId.systemDefault()).toLocalDate() to e
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) ->
                org.librelab.calendar.data.EventColors.resolve(list.first(), list.first().labelId?.let { labelMap[it] })
            }
    }

    val today = remember { LocalDate.now() }
    // 本周每日农历/节假日信息 (与月视图同源)
    val infos = remember(days) { days.associateWith { CalendarInfo.dayInfo(it) } }

    Column(Modifier.fillMaxSize()) {
        // 月份标题 + 切换 (月视图同款: "2026年8月", 左右箭头)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { weekAnchor = monday.minusDays(7) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一周")
            }
            Text(
                text = "${monday.year}年${monday.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            IconButton(onClick = { weekAnchor = monday.plusDays(7) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一周")
            }
        }

        // 星期表头 (月视图同款: 一~日)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            WEEKDAY_HEADERS.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 本周 7 天 (复用月视图 DayCell: 正方形格子/34dp 圆/1.5dp 事件环/今日·选中高光/农历角标/红字)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            days.forEach { d ->
                DayCell(
                    info = infos[d],
                    inMonth = d.monthValue == monday.monthValue,
                    isSelected = d == selectedDate,
                    isToday = d == today,
                    eventColor = eventColors[d],
                    onClick = { onSelectDate(d) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 选中日期的节假日/农历信息 (月视图同款)
        infos[selectedDate]?.let { info ->
            Text(
                text = buildString {
                    append("农历${info.lunarMonthName}${info.lunarDayName}")
                    if (info.solarTerm != null) append(" · ${info.solarTerm}")
                    if (info.holidayNames.isNotEmpty()) append(" · ${info.holidayNames}")
                    if (info.isWorkday) append(" · 调休上班")
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // 选中日当天的事件卡片 (复用月视图同款: Card + 4dp 色条, 点击编辑)
        DayEventCards(
            date = selectedDate,
            onEditEvent = onEditEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 日视图: 24 小时时间轴, 点击整点选中时间, 事件条按小时定位 (点击编辑) */
@Composable
fun DayScreen(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    selectedTime: LocalTime,
    onSelectTime: (LocalTime) -> Unit,
) {
    val dayStart = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEnd = selectedDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val eventsFlow = remember(selectedDate) { repository.observeRange(dayStart, dayEnd) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val labelMap = remember(labels) { org.librelab.calendar.data.EventColors.labelMap(labels) }
    val dayEvents = remember(events, selectedDate) {
        events.flatMap { repository.expandEvent(it, dayStart, dayEnd) }.sortedBy { it.startTime }
    }

    val info = remember(selectedDate) { CalendarInfo.dayInfo(selectedDate) }
    val weekday = "一二三四五六日"[selectedDate.dayOfWeek.value - 1]

    Column(Modifier.fillMaxSize()) {
        // 日期导航
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSelectDate(selectedDate.minusDays(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "前一天")
            }
            Text(
                text = "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 周$weekday",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = { onSelectDate(selectedDate.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "后一天")
            }
        }
        // 农历/节气/节日/调休
        Text(
            text = buildString {
                append("农历${info.lunarMonthName}${info.lunarDayName}")
                if (info.solarTerm != null) append(" · ${info.solarTerm}")
                if (info.holidayNames.isNotEmpty()) append(" · ${info.holidayNames}")
                if (info.isWorkday) append(" · 调休上班")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // 24 小时时间轴: 每行 1 小时, 点击选中该整点
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        ) {
            items(24) { hour ->
                val hStart = LocalDateTime.of(selectedDate, LocalTime.of(hour, 0))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val hEnd = hStart + 3_600_000L
                // 与该小时重叠的事件 (跨小时事件在多个时段行显示, 时间轴视觉连续)
                val hourEvents = dayEvents.filter { it.startTime < hEnd && it.endTime > hStart }
                val isSelected = selectedTime.hour == hour
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onSelectTime(LocalTime.of(hour, 0)) }
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else Color.Transparent,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${hour.toString().padStart(2, '0')}:00",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp).padding(start = 8.dp),
                    )
                    if (hourEvents.isEmpty()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        // Box 固定行高, 事件条填满 → 跨行事件相邻行紧贴成连续长条
                        Box(Modifier.weight(1f).height(64.dp)) {
                            Row(
                                Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                hourEvents.forEach { e ->
                                    EventChip(
                                        event = e,
                                        color = org.librelab.calendar.data.EventColors.resolve(
                                            e, e.labelId?.let { labelMap[it] },
                                        ),
                                        onClick = { onEditEvent(e) },
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴上的事件条: 撑满行高直角块, 跨行事件相邻行紧贴成连续长条 */
@Composable
private fun EventChip(
    event: CalendarEvent,
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 用 Box 而非 Surface(onClick): Surface 内部最小交互尺寸会吞掉 fillMaxHeight
    Box(
        modifier = modifier
            .background(Color(color))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = Color.White,
            )
            if (event.location.isNotEmpty()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** 日程视图: 从当前时间起的未来事件 (点击编辑) */
@Composable
fun ScheduleScreen(onEditEvent: (CalendarEvent) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // 每分钟刷新一次, 保证"未来事件"过滤正确
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val rangeEnd = now + 365L * 24 * 3600 * 1000

    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val eventsFlow = remember(now) { repository.observeRange(now, rangeEnd) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val upcoming = remember(events, now) {
        events.flatMap { repository.expandEvent(it, now, rangeEnd) }
            .filter { it.endTime > now }
            .sortedBy { it.startTime }
    }
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val labelMap = remember(labels) { org.librelab.calendar.data.EventColors.labelMap(labels) }

    LazyColumn(Modifier.fillMaxSize()) {
        if (upcoming.isEmpty()) {
            item {
                Text(
                    text = "暂无日程",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(upcoming, key = { "${it.id}-${it.startTime}" }) { e ->
                EventRow(
                    event = e,
                    showDate = true,
                    color = org.librelab.calendar.data.EventColors.resolve(
                        e, e.labelId?.let { labelMap[it] },
                    ),
                    onClick = { onEditEvent(e) },
                )
            }
        }
    }
}

/** 事件行: 色条 + 时间 + 标题 (+ 日期) */
@Composable
private fun EventRow(
    event: CalendarEvent,
    showDate: Boolean,
    color: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 事件颜色条
        Box(
            Modifier
                .width(4.dp)
                .height(32.dp)
                .background(Color(color), androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        // 时间列
        Column(Modifier.width(64.dp)) {
            Text(
                text = eventTimeText(event),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (showDate) {
                val d = Instant.ofEpochMilli(event.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    text = "${d.monthValue}月${d.dayOfMonth}日",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 事件内容
        Column(Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            if (event.location.isNotEmpty() || event.description.isNotEmpty()) {
                Text(
                    text = listOf(event.location, event.description).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // 重复标记
        if (event.repeat != org.librelab.calendar.data.RepeatRule.NONE.name) {
            Text(
                text = "重复",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
