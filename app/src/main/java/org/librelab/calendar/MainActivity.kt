package org.librelab.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.librelab.calendar.ui.CalendarRoot
import org.librelab.calendar.ui.theme.CalendarTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 动态日期图标: 若用户通过旧日期 alias 图标进入 (关闭数天后日期已变),
        // 切换图标会禁用该 alias → 系统关闭其任务 (打开即闪退)。
        // 处理: 先保留入口 alias 切换 (不闪退), 再重启为显式 MainActivity (脱离 alias),
        // 最后补禁遗留 alias — 用户几乎无感 (一闪后正常显示)。
        val entryClass = intent.component?.className
        val isAliasEntry = entryClass?.startsWith(DynamicIconSwitcher.ALIAS_PREFIX) == true &&
            entryClass != MainActivity::class.java.name
        // alias 入口 (旧日期图标): 保留入口 alias 防止任务被关闭; 其他入口正常全量切换
        val switched = if (isAliasEntry) {
            DynamicIconSwitcher.apply(this, skipAlias = entryClass)
        } else {
            DynamicIconSwitcher.apply(this)
        }
        if (switched) {
            // 重启为显式 MainActivity (任务脱离 alias, 避免被禁用关闭)
            val restart = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restart)
            // 补禁遗留 alias (此时旧任务已被 CLEAR_TASK 替换, 禁用不再闪退)
            DynamicIconSwitcher.finishSwitch(applicationContext)
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            CalendarTheme {
                CalendarRoot()
            }
        }
    }
}
