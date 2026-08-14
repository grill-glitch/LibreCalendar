package org.librelab.calendar.lunar

import android.content.Context

/** 节假日数据源提供商 */
enum class DataProvider(val label: String, val desc: String) {
    MIUI("小米日历", "小米日历在线节假日接口 (覆盖 2011-2026)"),
    HOLIDAY_CN("holiday-cn", "开放数据源 github.com/NateScarlet/holiday-cn, 支持自定义镜像"),
}

/**
 * 节假日数据源设置 (SharedPreferences):
 *  - 提供商 (standard 默认小米日历, libre 默认 holiday-cn)
 *  - 镜像 URL (仅 holiday-cn 生效; null = 官方 GitHub raw)
 *  - 下载状态: 最近成功来源 + 更新时间 (持久化, 重启保留)
 */
object HolidaySettings {

    private const val PREFS = "holiday_settings"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_MIRROR = "mirror"
    private const val KEY_LAST_SOURCE = "last_source"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 当前提供商; 未设置时按 flavor 默认 (standard=小米, libre=holiday-cn)。
     *  libre 版强制 holiday-cn (不提供小米选项) */
    fun getProvider(context: Context): DataProvider {
        if (org.librelab.calendar.BuildConfig.LIBRE) return DataProvider.HOLIDAY_CN
        val name = prefs(context).getString(KEY_PROVIDER, null)
        return DataProvider.entries.firstOrNull { it.name == name } ?: defaultProvider()
    }

    private fun defaultProvider(): DataProvider =
        if (org.librelab.calendar.BuildConfig.LIBRE) DataProvider.HOLIDAY_CN else DataProvider.MIUI

    fun setProvider(context: Context, provider: DataProvider) {
        prefs(context).edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    /** 当前镜像 URL (null = 默认 HolidayCnApi.DEFAULT_MIRROR) */
    fun getMirror(context: Context): String? {
        val v = prefs(context).getString(KEY_MIRROR, null)
        return v?.takeIf { it.isNotBlank() }
    }

    /** 设置镜像 URL (null/空 = 恢复默认) */
    fun setMirror(context: Context, mirror: String?) {
        val p = prefs(context).edit()
        if (mirror.isNullOrBlank()) p.remove(KEY_MIRROR) else p.putString(KEY_MIRROR, mirror.trim())
        p.apply()
    }

    /** 生效中的镜像 (含默认值) */
    fun effectiveMirror(context: Context): String =
        getMirror(context) ?: HolidayCnApi.DEFAULT_MIRROR

    // ---- 下载状态 ----

    /** 记录最近一次成功同步 (来源描述 + 时间戳) */
    fun recordSync(context: Context, source: String) {
        prefs(context).edit()
            .putString(KEY_LAST_SOURCE, source)
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    /** 最近成功同步来源 (无则 null) */
    fun lastSource(context: Context): String? =
        prefs(context).getString(KEY_LAST_SOURCE, null)

    /** 最近成功同步时间 (epoch millis, 0 = 从未成功) */
    fun lastSyncAt(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC_AT, 0L)
}
