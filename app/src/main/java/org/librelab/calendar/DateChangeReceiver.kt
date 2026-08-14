package org.librelab.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 日期变化广播接收器: 跨天 / 改时间 / 改时区 / 开机 时自动切换动态日期图标。
 *
 * 与 Etar 不同 (Etar 的图标由 Launcher3/iconloaderlib 按 ROM overlay 配置的
 * calendar_component_name 渲染, 仅对系统日历生效), 我们的 app 用 activity-alias
 * 方案, 需要自己响应日期变化 — 这样图标始终跟随日期, 用户永远不会点到
 * "过期日期图标" (点旧 alias 触发切换禁用入口 alias 会闪退, 见 DynamicIconSwitcher)。
 *
 * 安全说明: app 未运行时收到广播 (最常见场景: 跨天/改时间后图标自动更新),
 * 无任何任务引用 alias, 全量切换禁用安全; app 前台运行时改时间属边缘场景,
 * 可能闪退一次, 重开后 MainActivity 的入口检测保证正常。
 */
class DateChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // 延迟一点执行, 等系统广播分发完成; 失败不影响 (有 try-catch)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // app 有任务在 (前台/后台运行时改时间): 禁用 alias 会关闭其任务 (闪退)。
                    // 跳过自动切换 — 交给 MainActivity 入口逻辑处理 (那里有 skipAlias 保护)。
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    if (am.appTasks.isNotEmpty()) {
                        Log.i("DateChangeReceiver", "app has tasks, skip auto-switch (MainActivity handles)")
                        return@postDelayed
                    }
                    val switched = DynamicIconSwitcher.apply(context)
                    if (switched) {
                        Log.i("DateChangeReceiver", "date changed, icon updated to day ${java.time.LocalDate.now().dayOfMonth}")
                    }
                }, 500)
            }
        }
    }
}
