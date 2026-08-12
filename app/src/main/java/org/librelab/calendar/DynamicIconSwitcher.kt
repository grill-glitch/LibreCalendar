package org.librelab.calendar

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import java.time.LocalDate

/**
 * 动态日期图标切换器。
 *
 * 机制: manifest 里声明了 31 个 activity-alias (MainActivity01..31),
 * 每个 alias 带当天日期的图标。应用启动时启用"今天的 alias"并禁用其余,
 * launcher 随即显示当天日期图标。仅在日期变化时执行, 避免频繁刷新。
 *
 * 兼容任何 launcher (不依赖 TeslaCoil/calendarIconArray 协议)。
 *
 * 闪退修复 (2026-08-12): 若用户通过旧日期 alias 图标进入 (关闭数天后日期已变),
 * 直接禁用该 alias 会导致系统关闭其所在任务 (用户感知为"打开即崩溃")。
 * 因此 MainActivity 先以 skipAlias=当前入口 切换 (入口 alias 保留启用),
 * 再自动重启为显式 MainActivity (脱离 alias), 最后 finishSwitch 补禁遗留 alias。
 */
object DynamicIconSwitcher {

    private const val PREFS = "dynamic_icon"
    private const val KEY_LAST_DAY = "last_day"

    /** alias 类名前缀 (按 manifest namespace 展开, 与 applicationId 无关) */
    const val ALIAS_PREFIX = "org.librelab.calendar.MainActivity"

    /** 生成某日的 alias 完整类名, 如 org.librelab.calendar.MainActivity12 */
    fun aliasName(day: Int): String = ALIAS_PREFIX + day.toString().padStart(2, '0')

    /**
     * 启用今天的 alias 并禁用其余。
     * @param skipAlias 保留启用的 alias 完整类名 (当前入口), 不参与禁用;
     *                  用于"通过旧日期图标进入"的场景, 避免任务被关闭闪退。
     * @return 是否发生了切换 (日期变化); false 表示无需切换。
     */
    fun apply(context: Context, skipAlias: String? = null): Boolean {
        return try {
            val day = LocalDate.now().dayOfMonth
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getInt(KEY_LAST_DAY, 0) == day) return false

            val pm = context.packageManager
            val pkg = context.packageName
            for (i in 1..31) {
                val alias = ComponentName(pkg, aliasName(i))
                val state = if (i == day) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else if (alias.className == skipAlias) {
                    // 当前入口 alias 暂不禁用, 防止任务被系统关闭 (闪退)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
            prefs.edit().putInt(KEY_LAST_DAY, day).apply()
            true
        } catch (t: Throwable) {
            android.util.Log.w("DynamicIcon", "icon switch failed (non-fatal): ${t.message}")
            false
        }
    }

    /**
     * 补禁遗留 alias (在 MainActivity 重启为显式入口后调用):
     * 禁用除今天外的所有 alias — 此时任务已脱离 alias, 禁用不会关闭任务。
     */
    fun finishSwitch(context: Context) {
        try {
            val day = LocalDate.now().dayOfMonth
            val pm = context.packageManager
            val pkg = context.packageName
            for (i in 1..31) {
                if (i == day) continue
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, aliasName(i)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        } catch (t: Throwable) {
            android.util.Log.w("DynamicIcon", "finishSwitch failed (non-fatal): ${t.message}")
        }
    }
}
