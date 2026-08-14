package org.librelab.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.librelab.calendar.ui.CalendarRoot
import org.librelab.calendar.ui.theme.CalendarTheme

class MainActivity : ComponentActivity() {

    private companion object {
        const val EXTRA_AFTER_SWITCH = "after_alias_switch"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 动态日期图标: 若用户通过旧日期 alias 图标进入 (关闭数天后日期已变),
        // 切换图标会禁用该 alias → 系统关闭其任务 (打开即闪退)。
        // 处理: 先保留入口 alias 切换 (不闪退), 再重启为显式 MainActivity (脱离 alias),
        // 重启后的新任务中补禁遗留 alias — 此时任务不再引用旧 alias, 禁用安全。
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
            // 重启为显式 MainActivity (CLEAR_TASK 脱离 alias 任务); 补禁放到重启后的 onCreate
            val restart = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_AFTER_SWITCH, true)
            }
            startActivity(restart)
            finish()
            return
        }
        // 重启后的新任务 (显式 MainActivity, 不引用任何 alias): 补禁遗留 alias。
        // 必须延迟 — 旧任务 (origActivity=旧 alias) 的销毁动画是异步的, 立即禁用
        // 仍会关闭正在收尾的任务 (实测 12:00:05 OPEN → 12:00:06 CLOSE 闪退)。
        if (intent.getBooleanExtra(EXTRA_AFTER_SWITCH, false)) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                DynamicIconSwitcher.finishSwitch(applicationContext)
            }, 1500)
        }

        enableEdgeToEdge()
        setContent {
            CalendarTheme {
                CalendarRoot()
            }
        }
    }
}
