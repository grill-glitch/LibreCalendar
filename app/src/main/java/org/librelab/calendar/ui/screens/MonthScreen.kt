package org.librelab.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librelab.calendar.data.CalendarEvent
import org.librelab.calendar.lunar.CalendarInfo
import org.librelab.calendar.lunar.DayInfo
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WEEKDAY_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 中间锚点页: 前后各留 ~2000 个月的可滑范围 */
private const val ANCHOR_PAGE = 2000

@Composable
fun MonthScreen(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
) {
    val initialMonth = YearMonth.now()
    // 按月缓存农历/节假日数据: 滑动相邻页无需等待
    val monthData = remember { mutableStateMapOf<YearMonth, Map<LocalDate, DayInfo>>() }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = ANCHOR_PAGE) { Int.MAX_VALUE }
    val currentMonth = initialMonth.plusMonths((pagerState.currentPage - ANCHOR_PAGE).toLong())

    // 预载当前月 + 相邻月 (纯本地), 再后台补拉在线数据; 仅当在线数据更新才重建
    LaunchedEffect(currentMonth) {
        val toLoad = listOf(
            currentMonth.minusMonths(2), currentMonth.minusMonths(1),
            currentMonth, currentMonth.plusMonths(1), currentMonth.plusMonths(2)
        ).filter { it !in monthData }
        if (toLoad.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                toLoad.associateWith { m ->
                    CalendarInfo.monthGrid(m.year, m.monthValue).associateWith { CalendarInfo.dayInfo(it) }
                }
            }
            monthData.putAll(loaded)
        }
        val updated = withContext(Dispatchers.IO) {
            CalendarInfo.refreshOnline(currentMonth.year - 1, currentMonth.year + 2)
        }
        if (updated) {
            val rebuilt = withContext(Dispatchers.IO) {
                CalendarInfo.monthGrid(currentMonth.year, currentMonth.monthValue)
                    .associateWith { CalendarInfo.dayInfo(it) }
            }
            monthData[currentMonth] = rebuilt
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 月份标题 + 切换 (按钮带动画滚动)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
            }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上一月")
            }
            Text(
                text = "${currentMonth.year}年${currentMonth.monthValue}月",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            )
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下一月")
            }
        }

        // 星期表头
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

        // 月网格: 横向滑动切换月份 (Pager 自带滑动 + 点击滚动动画)
        // 高度 = 内容高度 (6 行正方形格子), 表头紧贴网格, 无垂直居中空隙
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val month = initialMonth.plusMonths((page - ANCHOR_PAGE).toLong())
            MonthPage(
                month = month,
                infos = monthData[month] ?: emptyMap(),
                selectedDate = selectedDate,
                onSelectDate = onSelectDate,
            )
        }

        // 选中日期的节假日/农历信息
        selectedDate.let { d ->
            monthData[YearMonth.from(d)]?.get(d)?.let { info ->
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
        }

        // 选中日期当天的事件卡片 (撑满剩余空间可滚动, 点击编辑)
        DayEventCards(
            date = selectedDate,
            onEditEvent = onEditEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 当天事件卡片列表: 撑满剩余高度可上下滑动, 点击卡片进入编辑 */
@Composable
private fun DayEventCards(
    date: LocalDate,
    onEditEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        org.librelab.calendar.data.EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val dayStart = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dayEnd = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val eventsFlow = remember(date) { repository.observeRange(dayStart, dayEnd) }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val labelMap = remember(labels) { org.librelab.calendar.data.EventColors.labelMap(labels) }
    val dayEvents = remember(events, date) {
        events.flatMap { repository.expandEvent(it, dayStart, dayEnd) }.sortedBy { it.startTime }
    }

    if (dayEvents.isEmpty()) return

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp, // 底部留白防 FAB 遮挡
        ),
    ) {
        items(dayEvents, key = { "${it.id}-${it.startTime}" }) { e ->
            androidx.compose.material3.Card(
                onClick = { onEditEvent(e) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 事件颜色条
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .background(
                                Color(org.librelab.calendar.data.EventColors.resolve(e, e.labelId?.let { labelMap[it] })),
                                androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (e.allDay) "全天"
                        else java.time.Instant.ofEpochMilli(e.startTime)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(52.dp),
                    )
                    // 标题 + 地点/描述
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = e.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                        val sub = listOf(e.location, e.description)
                            .filter { it.isNotEmpty() }.joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (e.repeat != org.librelab.calendar.data.RepeatRule.NONE.name) {
                        Text(
                            text = "重复",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 单月网格页: 事件圆点按本页月份查询 */
@Composable
private fun MonthPage(
    month: YearMonth,
    infos: Map<LocalDate, DayInfo>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        org.librelab.calendar.data.EventRepository(app.database.eventDao(), app.database.labelDao())
    }

    val monthStart = month.atDay(1)
    val monthEnd = month.plusMonths(1).atDay(1)
    // remember Flow: 滑动重组时避免每次重新订阅 Room (重新查询 DB 会卡)
    val eventsFlow = remember(month) {
        val fromMillis = monthStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val toMillis = monthEnd.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.observeRange(fromMillis, toMillis)
    }
    val events by eventsFlow.collectAsState(initial = emptyList())
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val labelMap = remember(labels) { org.librelab.calendar.data.EventColors.labelMap(labels) }
    // 日期 -> 事件颜色 (同日多事件取第一个的颜色)
    val eventColors: Map<LocalDate, Int> = remember(events, labelMap) {
        events
            .map { e ->
                java.time.Instant.ofEpochMilli(e.startTime)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate() to e
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) ->
                val e = list.first()
                org.librelab.calendar.data.EventColors.resolve(e, e.labelId?.let { labelMap[it] })
            }
    }

    // 单次计算: 滑动重组时避免每帧重复 LocalDate.now() / monthGrid (主线程热点)
    val today = remember { LocalDate.now() }
    val grid = remember(month) { CalendarInfo.monthGrid(month.year, month.monthValue) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        info = infos[date],
                        inMonth = date.monthValue == month.monthValue,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        eventColor = eventColors[date],
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    info: DayInfo?,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    eventColor: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = info?.date
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 日期数字 (选中/今天背景 + 事件圆圈标记, 颜色按事件)
            val eventRing = if (eventColor != null && !isSelected) {
                Modifier.border(
                    1.5.dp,
                    Color(eventColor),
                    CircleShape,
                )
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .then(eventRing)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> Color.Transparent
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date?.dayOfMonth?.toString() ?: "",
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        // 周末或法定节假日标红; 调休补班日不红 (补位格子淡红以示区分)
                        info?.isWorkday != true && (date?.dayOfWeek?.value == 6 ||
                            date?.dayOfWeek?.value == 7 || info?.isFree == true) ->
                            if (inMonth) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            // 农历/节日角标
            Text(
                text = when {
                    info?.isWorkday == true -> "班"
                    info?.holidayNames?.isNotEmpty() == true -> info.holidayNames.substringBefore(" ")
                    info?.solarTerm != null -> info.solarTerm
                    date?.dayOfMonth == 1 -> info?.lunarMonthName ?: ""
                    else -> info?.lunarDayName ?: ""
                },
                fontSize = 9.sp,
                maxLines = 1,
                color = when {
                    info?.isWorkday == true -> MaterialTheme.colorScheme.tertiary
                    info?.isFree == true -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.widthIn(max = 40.dp),
            )
        }
    }
}
