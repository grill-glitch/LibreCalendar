package org.librelab.calendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.librelab.calendar.data.EventColors
import org.librelab.calendar.data.EventRepository
import org.librelab.calendar.data.Label
import org.librelab.calendar.data.LabelIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 标签管理界面 (全屏覆盖层):
 * 新增/编辑标签 (名称 + 图标 + 颜色), 删除标签。
 */
@Composable
fun LabelsScreen(
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember {
        val app = context.applicationContext as org.librelab.calendar.CalendarApp
        EventRepository(app.database.eventDao(), app.database.labelDao())
    }
    val labels by repository.observeLabels().collectAsState(initial = emptyList())
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    // 编辑弹窗状态
    var editingLabel by remember { mutableStateOf<Label?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingLabel by remember { mutableStateOf<Label?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "标签",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("完成") }
        }

        if (labels.isEmpty()) {
            Text(
                text = "还没有标签\n新建标签后, 可在事件编辑中为事件添加标签, 事件颜色将使用标签颜色",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp),
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(labels, key = { it.id }) { l ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingLabel = l; showEditor = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 颜色圆点
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(
                                Color(l.color ?: 0xFF9E9E9E.toInt()),
                                CircleShape,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    // 图标
                    if (l.icon != null) {
                        Text(
                            text = LabelIcons.displayName(l.icon),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(l.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { deletingLabel = l }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "删除标签",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { editingLabel = null; showEditor = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("新建标签", modifier = Modifier.padding(start = 4.dp))
        }
    }

    // 编辑/新建弹窗
    if (showEditor) {
        LabelEditDialog(
            label = editingLabel,
            onDismiss = { showEditor = false },
            onSave = { name, color, icon ->
                scope.launch {
                    val existing = editingLabel
                    if (existing == null) {
                        repository.saveLabel(Label(name = name, color = color, icon = icon))
                    } else {
                        repository.updateLabel(existing.copy(name = name, color = color, icon = icon))
                    }
                }
                showEditor = false
            },
        )
    }

    // 删除确认
    deletingLabel?.let { l ->
        AlertDialog(
            onDismissRequest = { deletingLabel = null },
            title = { Text("删除标签") },
            text = { Text("确定要删除标签\"${l.name}\"吗? 已关联的事件不会删除, 但会恢复为动态颜色。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteLabel(l.id) }
                    deletingLabel = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingLabel = null }) { Text("取消") }
            },
        )
    }
}

/** 标签编辑弹窗: 名称 + 图标 + 颜色 */
@Composable
private fun LabelEditDialog(
    label: Label?,
    onDismiss: () -> Unit,
    onSave: (String, Int?, String?) -> Unit,
) {
    var name by remember { mutableStateOf(label?.name ?: "") }
    var color by remember { mutableStateOf(label?.color) }
    var icon by remember { mutableStateOf(label?.icon) }
    var iconQuery by remember { mutableStateOf("") }

    // 按中文名/英文名过滤图标 (fonts.google.com/icons 同源 Material Symbols)
    val filteredIcons = remember(iconQuery) {
        val q = iconQuery.trim().lowercase()
        if (q.isEmpty()) LabelIcons.NAMES
        else LabelIcons.NAMES.filter { n ->
            n.lowercase().contains(q) || LabelIcons.displayName(n).contains(iconQuery.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (label == null) "新建标签" else "编辑标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 图标选择 (可搜索, 来源 Material Symbols / fonts.google.com/icons)
                Text(
                    text = "图标 (可搜索, 如 \"工作\"/\"work\")",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = iconQuery,
                    onValueChange = { iconQuery = it },
                    placeholder = { Text("搜索图标…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(
                                    2.dp,
                                    if (icon == null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { icon = null },
                            contentAlignment = Alignment.Center,
                        ) { Text("无", fontSize = 11.sp) }
                    }
                    items(filteredIcons) { n ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .border(
                                    2.dp,
                                    if (icon == n) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { icon = n },
                            contentAlignment = Alignment.Center,
                        ) { Text(LabelIcons.displayName(n), fontSize = 10.sp) }
                    }
                }
                if (filteredIcons.isEmpty()) {
                    Text(
                        text = "没有匹配的图标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 颜色选择
                Text(
                    text = "颜色 (事件继承此颜色)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            Modifier
                                .size(32.dp)
                                .border(
                                    2.dp,
                                    if (color == null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { color = null },
                            contentAlignment = Alignment.Center,
                        ) { Text("无", fontSize = 11.sp) }
                    }
                    items(EventColors.LABEL_PALETTE.size) { i ->
                        val c = EventColors.LABEL_PALETTE[i]
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    2.dp,
                                    if (color == c) MaterialTheme.colorScheme.primary
                                    else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(name.trim(), color, icon)
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
