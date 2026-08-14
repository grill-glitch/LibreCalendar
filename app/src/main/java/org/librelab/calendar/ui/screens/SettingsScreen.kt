package org.librelab.calendar.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.librelab.calendar.lunar.CalendarInfo
import org.librelab.calendar.lunar.DataProvider
import org.librelab.calendar.lunar.HolidayCnApi
import org.librelab.calendar.lunar.HolidaySettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 设置页 (全屏覆盖层):
 *  - 数据源提供商选择 (小米日历 / holiday-cn)
 *  - holiday-cn 自定义镜像
 *  - 下载状态: 日历来源 + 更新时间
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(HolidaySettings.getProvider(context)) }
    var mirror by remember { mutableStateOf(HolidaySettings.getMirror(context) ?: "") }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 下载状态 (持久化; 保存后由拉取协程刷新)
    var lastSource by remember { mutableStateOf(HolidaySettings.lastSource(context)) }
    var lastSyncAt by remember { mutableStateOf(HolidaySettings.lastSyncAt(context)) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("完成") }
        }

        // ---- 数据源提供商 (libre 版仅 holiday-cn, 无小米选项) ----
        Text(
            text = "日历数据源",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        DataProvider.entries
            .filter { p ->
                if (org.librelab.calendar.BuildConfig.LIBRE) p == DataProvider.HOLIDAY_CN
                else true
            }
            .forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = provider == p,
                        onClick = { provider = p; saved = false },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(p.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            p.desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

        // ---- 镜像 (仅 holiday-cn) ----
        if (provider == DataProvider.HOLIDAY_CN) {
            Text(
                text = "镜像地址",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = mirror,
                onValueChange = { mirror = it; saved = false },
                label = { Text("镜像地址") },
                placeholder = { Text(HolidayCnApi.DEFAULT_MIRROR) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "当前生效: ${HolidaySettings.effectiveMirror(context)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (provider == DataProvider.HOLIDAY_CN) {
                TextButton(onClick = { mirror = ""; saved = false }) {
                    Text("恢复默认镜像")
                }
            }
            Button(
                onClick = {
                    HolidaySettings.setProvider(context, provider)
                    HolidaySettings.setMirror(context, mirror)
                    // 提供商/镜像变更: 清空缓存并立即重新拉取
                    CalendarInfo.resetOnlineData()
                    val currentYear = java.time.LocalDate.now().year
                    scope.launch(Dispatchers.IO) {
                        CalendarInfo.refreshOnline(currentYear - 1, currentYear + 2)
                        // 拉取完成后刷新下载状态显示
                        lastSource = HolidaySettings.lastSource(context)
                        lastSyncAt = HolidaySettings.lastSyncAt(context)
                    }
                    saved = true
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存并重新拉取") }
        }
        if (saved) {
            Text(
                text = "已保存, 数据正在后台重新拉取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // ---- 下载状态 ----
        Text(
            text = "下载状态",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        StatusRow(label = "日历来源", value = lastSource ?: "尚未同步")
        StatusRow(
            label = "更新时间",
            value = if (lastSyncAt > 0) {
                Instant.ofEpochMilli(lastSyncAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } else {
                "尚未同步"
            },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // 状态点: 已同步=绿, 未同步=灰
        val ok = value != "尚未同步"
        Box(
            Modifier
                .size(8.dp)
                .background(
                    if (ok) androidx.compose.ui.graphics.Color(0xFF66BB6A.toInt())
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
