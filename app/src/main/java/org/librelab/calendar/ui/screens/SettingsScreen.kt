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

        // ---- 权限与优化 (提醒可靠送达) ----
        Text(
            text = "权限与优化",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        // 通知权限: 提醒通知需要
        var notificationGranted by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
            )
        }
        var notifRequested by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("通知权限", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = when {
                        notificationGranted -> "已授予, 可接收事件提醒"
                        notifRequested -> "已跳转系统设置, 请允许通知后返回"
                        else -> "用于事件提醒通知"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (notificationGranted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (notificationGranted) {
                Text(
                    text = "✓ 已授予",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (notifRequested) {
                Text(
                    text = "授予成功",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                        ).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent)
                        // 返回时由 LaunchedEffect 重新检测权限状态
                        notifRequested = true
                    },
                ) { Text("授予") }
            }
        }

        // 忽略电池优化: 防止系统休眠杀掉提醒
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        var ignoreBattery by remember {
            mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("忽略电池优化", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (ignoreBattery) "已开启, 提醒不会被系统休眠拦截"
                    else "防止系统休眠时提醒不送达",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ignoreBattery) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ignoreBattery) {
                Text(
                    text = "✓ 已开启",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = {
                        // 直接请求 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 在部分 ROM 无效,
                        // 先尝试, 若无法打开则引导到电池优化设置列表
                        val direct = android.content.Intent(
                            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:${context.packageName}"),
                        )
                        val list = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        try {
                            context.startActivity(direct)
                        } catch (e: android.content.ActivityNotFoundException) {
                            context.startActivity(list)
                        }
                    },
                ) { Text("开启") }
            }
        }
        // 从系统设置返回后重新检测权限/优化状态
        androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted != notificationGranted) notificationGranted = granted
                val bat = pm.isIgnoringBatteryOptimizations(context.packageName)
                if (bat != ignoreBattery) ignoreBattery = bat
            }
        }
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
