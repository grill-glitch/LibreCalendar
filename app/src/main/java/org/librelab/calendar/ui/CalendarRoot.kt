package org.librelab.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.semantics.semantics
import org.librelab.calendar.alarm.ReminderNotifications
import org.librelab.calendar.alarm.ReminderScheduler
import org.librelab.calendar.data.CalendarEvent
import org.librelab.calendar.data.EventRepository
import org.librelab.calendar.ui.screens.DayScreen
import org.librelab.calendar.ui.screens.EventEditScreen
import org.librelab.calendar.ui.screens.LabelsScreen
import org.librelab.calendar.ui.screens.MonthScreen
import org.librelab.calendar.ui.screens.ScheduleScreen
import org.librelab.calendar.ui.screens.SettingsScreen
import org.librelab.calendar.ui.screens.WeekScreen
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class CalendarTab(val label: String, val icon: ImageVector) {
    MONTH("月", Icons.Outlined.CalendarMonth),
    WEEK("周", Icons.Outlined.DateRange),
    DAY("日", Icons.Outlined.Today),
    AGENDA("日程", Icons.Outlined.List),
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CalendarRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var editingDefaultDate by remember { mutableStateOf(LocalDate.now()) }
    var showEditor by remember { mutableStateOf(false) }
    var showLabels by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // 月视图选中的日期 (提升到这里, 供 FAB 新建事件默认日期使用)
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    // 日视图时间轴选中的时间 (FAB 新建事件默认时间)
    var selectedTime by remember { mutableStateOf(java.time.LocalTime.of(9, 0)) }
    val context = LocalContext.current

    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        EventRepository(app.database.eventDao(), app.database.labelDao())
    }

    // 用应用级 scope: 保存后编辑器关闭触发重组, rememberCoroutineScope 的协程会被取消
    val appScope = remember {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun saveEvent(event: CalendarEvent) {
        appScope.launch {
            val id = if (event.id == 0L) repository.insert(event) else {
                repository.update(event); event.id
            }
            // 提醒调度: 取消旧的, 设置新的 (仅当有提醒)
            ReminderScheduler.cancel(context, id)
            if (event.reminderMinutes != 0) {
                val trigger = ReminderNotifications.triggerTime(event.startTime, event.reminderMinutes)
                if (trigger > System.currentTimeMillis()) {
                    ReminderScheduler.schedule(context, id, trigger, event.title)
                }
            }
        }
    }

    fun deleteEvent(id: Long) {
        appScope.launch {
            repository.delete(id)
            ReminderScheduler.cancel(context, id)
        }
    }

    // 编辑页打开时拦截系统返回键: 关闭编辑器回到日历主界面 (而非退出 app)
    if (showEditor) {
        BackHandler { showEditor = false }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingDefaultDate = selectedDate; editing = null; showEditor = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = "新建事件") },
                text = { Text("新建事件") },
                modifier = Modifier.semantics { contentDescription = "新建事件" },
            )
        },
        bottomBar = {
            NavigationBar {
                CalendarTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            // Scaffold innerPadding 已含状态栏 inset, 无需重复 statusBarsPadding
            Column(Modifier.fillMaxSize()) {
                // 顶部标题: 紧贴状态栏, 细字体; 右上角标签管理按钮
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "日历",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    // 标签管理按钮 (sell 价格标签图标)
                    IconButton(onClick = { showLabels = true }) {
                        Icon(
                            imageVector = Sell,
                            contentDescription = "标签",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // 设置按钮 (gear 齿轮图标)
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = SettingsIcon,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (CalendarTab.entries[selectedTab]) {
                        CalendarTab.MONTH -> MonthScreen(
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            onEditEvent = { editing = it; showEditor = true },
                        )
                        CalendarTab.WEEK -> WeekScreen(
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            onEditEvent = { editing = it; showEditor = true },
                        )
                        CalendarTab.DAY -> DayScreen(
                            selectedDate = selectedDate,
                            onSelectDate = { selectedDate = it },
                            onEditEvent = { editing = it; showEditor = true },
                            selectedTime = selectedTime,
                            onSelectTime = { selectedTime = it },
                        )
                        CalendarTab.AGENDA -> ScheduleScreen(
                            onEditEvent = { editing = it; showEditor = true },
                        )
                    }
                }
            }
        }
    }

    // 事件编辑页作为全屏覆盖层: 遮住底部导航栏与 FAB; 从底部滑入/滑出
    AnimatedVisibility(
        visible = showEditor,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Surface(Modifier.fillMaxSize()) {
            EventEditScreen(
                event = editing,
                defaultDate = editingDefaultDate,
                defaultTime = selectedTime,
                onSave = ::saveEvent,
                onDelete = ::deleteEvent,
                onClose = { showEditor = false },
            )
        }
    }

    // 标签管理页作为全屏覆盖层: 返回键带滑出动画回主页面
    BackHandler(enabled = showLabels) { showLabels = false }
    AnimatedVisibility(
        visible = showLabels,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Surface(Modifier.fillMaxSize()) {
            LabelsScreen(onClose = { showLabels = false })
        }
    }

    // 设置页作为全屏覆盖层: 返回键带滑出动画回主页面
    BackHandler(enabled = showSettings) { showSettings = false }
    AnimatedVisibility(
        visible = showSettings,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Surface(Modifier.fillMaxSize()) {
            SettingsScreen(onClose = { showSettings = false })
        }
    }
}
