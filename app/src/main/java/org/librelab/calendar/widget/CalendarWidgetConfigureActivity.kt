package org.librelab.calendar.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.librelab.calendar.ui.theme.CalendarTheme

/**
 * 小组件配置页: 添加小组件时选择视图 (月/周/日)。
 * 选择结果保存到 prefs (widget_prefs: view_<widgetId>), 随后立即刷新小组件。
 */
class CalendarWidgetConfigureActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var resultValue = RESULT_CANCELED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 从启动 Intent 取 widgetId (系统添加小组件时传入)
        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // 立即回包 (配置页可以取消; 取消后小组件不会被添加)
        setResult(resultValue, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        setContent {
            CalendarTheme {
                // 按 provider 判断尺寸: CalendarWidgetProvider=2x1, CalendarWidgetProvider4x2=4x2
                val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)
                val is2x1 = info?.provider?.className?.contains("4x2") != true
                WidgetConfigureUi(
                    is2x1 = is2x1,
                    onSave = { view ->
                        saveView(view)
                        // 更新小组件为所选视图
                        CalendarWidgetProvider.refresh(this, widgetId)
                        resultValue = RESULT_OK
                        setResult(resultValue, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    },
                )
            }
        }
    }

    private fun saveView(view: String) {
        getSharedPreferences("widget_prefs", MODE_PRIVATE)
            .edit()
            .putString("view_$widgetId", view)
            .apply()
    }
}

@Composable
private fun WidgetConfigureUi(is2x1: Boolean, onSave: (String) -> Unit) {
    var selected by remember { mutableStateOf(if (is2x1) "day" else "month") }
    // 2x1 仅日视图 (空间不足, 不提供月/周); 4x2 支持月/周/日
    val options = if (is2x1) {
        listOf("day" to "日视图" to "星期 + 今天日期 + 农历/休班 + 今日事件")
    } else {
        listOf(
            "month" to "月视图" to "当月网格, 有事件的日期带标记, 今天高亮",
            "week" to "周视图" to "本周 7 天, 今天高亮",
            "day" to "日视图" to "今天日期 + 农历/节日 + 当天事件",
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "日历小组件",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (is2x1) "2x1 小组件固定为日视图:" else "选择小组件显示的视图:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        options.forEach { (pair, desc) ->
            val (value, label) = pair
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        2.dp,
                        if (selected == value) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { selected = value }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == value, onClick = { selected = value })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onSave(selected) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("添加小组件") }
    }
}
