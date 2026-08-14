package org.librelab.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.librelab.calendar.data.CalendarEvent
import org.librelab.calendar.data.ReminderOffset
import org.librelab.calendar.data.RepeatRule
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmtDate = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val fmtTime = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 事件编辑页。newEvent=true 时新建 (默认从 selectedDate 起), 否则编辑现有事件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    event: CalendarEvent?,
    defaultDate: LocalDate,
    defaultTime: LocalTime,
    defaultAllDay: Boolean = false,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var location by remember { mutableStateOf(event?.location ?: "") }
    var allDay by remember { mutableStateOf(event?.allDay ?: defaultAllDay) }
    var repeat by remember { mutableStateOf(RepeatRule.fromName(event?.repeat)) }
    var reminder by remember { mutableStateOf(ReminderOffset.fromMinutes(event?.reminderMinutes ?: 0)) }
    val zone = ZoneId.systemDefault()

    // 时间状态: 默认事件时间 = 选中日 + 选中时间 (时间轴点击), 时长 1 小时
    val defaultStart = event?.startTime ?: LocalDateTime.of(defaultDate, defaultTime)
        .atZone(zone).toInstant().toEpochMilli()
    var startTime by remember { mutableLongStateOf(defaultStart) }
    var endTime by remember {
        mutableLongStateOf(event?.endTime ?: (defaultStart + 3600_000L))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    // 标签 / 自定义颜色 (null = 未设置 → 动态取色)
    var labelId by remember { mutableStateOf(event?.labelId) }
    var customColor by remember { mutableStateOf(event?.customColor) }
    var showLabelMenu by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        org.librelab.calendar.data.EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val labels by repository.observeLabels().collectAsState(initial = emptyList())

    fun startLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), zone)
    fun endLocal(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), zone)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (event == null) "新建事件" else "编辑事件",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("取消") }
            Button(onClick = {
                val e = CalendarEvent(
                    id = event?.id ?: 0,
                    title = title.ifBlank { "无标题事件" },
                    description = description,
                    location = location,
                    startTime = startTime,
                    endTime = endTime,
                    allDay = allDay,
                    repeat = repeat.name,
                    reminderMinutes = reminder.minutes,
                    colorIndex = event?.colorIndex ?: 0,
                    customColor = customColor,
                    labelId = labelId,
                )
                onSave(e)
                onClose()
            }) { Text("保存") }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 全天开关
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("全天事件", modifier = Modifier.weight(1f))
            Switch(checked = allDay, onCheckedChange = {
                allDay = it
                if (it) {
                    val d = startLocal().toLocalDate()
                    startTime = d.atStartOfDay(zone).toInstant().toEpochMilli()
                    endTime = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                }
            })
        }

        // 开始日期 (点击弹 DatePicker)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("开始", modifier = Modifier.weight(1f))
            TextButton(onClick = { showDatePicker = true }) {
                Text(startLocal().format(fmtDate))
            }
            if (!allDay) {
                TextButton(onClick = { showStartTimePicker = true }) {
                    Text(startLocal().format(fmtTime))
                }
            }
        }

        // 结束日期 (编辑结束日期用同一天)
        if (!allDay) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("结束", modifier = Modifier.weight(1f))
                TextButton(onClick = { showEndTimePicker = true }) {
                    Text(endLocal().format(fmtDate) + " " + endLocal().format(fmtTime))
                }
            }
        }

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("地点") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("备注") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // 重复规则
        DropdownField(
            label = "重复",
            value = repeat.label,
            options = RepeatRule.entries.map { it.label to it.name },
            onSelect = { repeat = RepeatRule.fromName(it) },
        )

        // 提醒
        DropdownField(
            label = "提醒",
            value = reminder.label,
            options = ReminderOffset.entries.map { it.label to it.minutes.toString() },
            onSelect = { reminder = ReminderOffset.fromMinutes(it.toInt()) },
        )

        // 标签 (点击弹出选择; 标签有颜色时事件继承标签颜色)
        Box {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("标签", modifier = Modifier.weight(1f))
                val currentLabel = labels.find { it.id == labelId }
                TextButton(onClick = { showLabelMenu = true }) {
                    Text(currentLabel?.name ?: "无标签")
                }
            }
            DropdownMenu(
                expanded = showLabelMenu,
                onDismissRequest = { showLabelMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("无标签") },
                    onClick = { labelId = null; showLabelMenu = false },
                )
                labels.forEach { l ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                l.color?.let { c ->
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .background(androidx.compose.ui.graphics.Color(c), CircleShape),
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                                }
                                Text(l.name)
                            }
                        },
                        onClick = { labelId = l.id; showLabelMenu = false },
                    )
                }
            }
        }

        // 自定义颜色 (null = 动态取色; 有标签颜色时标签优先)
        Column {
            Text(
                text = "颜色 (标签颜色优先, 未选则按标题动态)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                // 自动 (动态取色)
                item {
                    Box(
                        Modifier
                            .size(32.dp)
                            .border(
                                2.dp,
                                if (customColor == null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                            .clickable { customColor = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("自动", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(org.librelab.calendar.data.EventColors.PALETTE.size) { i ->
                    val c = org.librelab.calendar.data.EventColors.PALETTE[i]
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(androidx.compose.ui.graphics.Color(c), CircleShape)
                            .border(
                                2.dp,
                                if (customColor == c) MaterialTheme.colorScheme.primary
                                else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape,
                            )
                            .clickable {
                                customColor = if (customColor == c) null else c
                            },
                    )
                }
            }
        }

        if (event != null) {
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    "删除事件",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    // ---- 删除确认对话框 ----
    if (showDeleteConfirm && event != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除事件") },
            text = { Text("确定要删除\"${event.title}\"吗?此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(event.id)
                        onClose()
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    // ---- 日期选择 ----
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startTime)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val date = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                        val old = startLocal()
                        val newStart = LocalDateTime.of(date, old.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        val dur = endTime - startTime
                        startTime = newStart
                        endTime = newStart + dur
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }

    // ---- 时间选择 ----
    if (showStartTimePicker) {
        val t = startLocal().toLocalTime()
        val picker = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = startLocal().toLocalDate()
                    val newStart = LocalDateTime.of(date, LocalTime.of(picker.hour, picker.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    val dur = endTime - startTime
                    startTime = newStart
                    endTime = newStart + dur
                    showStartTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("取消") } },
        ) { TimePicker(state = picker) }
    }

    if (showEndTimePicker) {
        val t = endLocal().toLocalTime()
        val picker = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        DatePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = endLocal().toDateOrNull() ?: startLocal().toLocalDate()
                    val newEnd = LocalDateTime.of(date, LocalTime.of(picker.hour, picker.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    if (newEnd > startTime) endTime = newEnd
                    showEndTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("取消") } },
        ) { TimePicker(state = picker) }
    }
}

/** 下拉选择字段 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>, // label to key
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (l, k) ->
                DropdownMenuItem(
                    text = { Text(l) },
                    onClick = { onSelect(k); expanded = false },
                )
            }
        }
    }
}

private fun LocalDateTime.toDateOrNull(): LocalDate? = toLocalDate()
