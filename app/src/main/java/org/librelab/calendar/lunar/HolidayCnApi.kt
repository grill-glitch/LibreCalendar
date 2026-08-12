package org.librelab.calendar.lunar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * holiday-cn 节假日数据源 (github.com/NateScarlet/holiday-cn, MIT)。
 * 每年一个 JSON: {"year":Y, "days":[{"name":"元旦","date":"2026-01-01","isOffDay":true}, ...]}
 *  - isOffDay=true  → 放假 (freeday)
 *  - isOffDay=false → 调休补班 (workday)
 * 通过镜像 URL 拉取: {mirror}/{year}.json, 支持自定义镜像 (默认 jsDelivr CDN)。
 * 数据覆盖 2004 至今, 每年由社区维护, 开放可审计。
 */
object HolidayCnApi {

    /** 默认镜像: 官方 GitHub raw (NateScarlet/holiday-cn master 分支) */
    const val DEFAULT_MIRROR = "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master"

    /** 备用镜像: jsDelivr CDN (官方 GitHub 不可用时) */
    const val FALLBACK_MIRROR = "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master"

    private const val TIMEOUT_MS = 8000

    /** 拉取 [fromYear, toYear] 区间数据 (每年一个请求, 单个年份失败不中断) */
    fun fetchYears(fromYear: Int, toYear: Int, mirror: String): List<MiuiHolidayApi.YearData> {
        val result = mutableListOf<MiuiHolidayApi.YearData>()
        for (year in fromYear..toYear) {
            val data = fetchYear(year, mirror) ?: continue
            result += data
        }
        return result
    }

    fun fetchYear(year: Int, mirror: String): MiuiHolidayApi.YearData? {
        val urls = listOf(
            "$mirror/$year.json",
            "$FALLBACK_MIRROR/$year.json",
        ).distinct()
        for (url in urls) {
            try {
                val text = httpGet(url) ?: continue
                val parsed = parseYear(text, year) ?: continue
                return parsed
            } catch (_: Exception) {
                // 尝试下一个镜像
            }
        }
        return null
    }

    /** 解析 holiday-cn JSON → YearData (day-of-year 1-based); 格式错误返回 null */
    fun parseYear(text: String, expectedYear: Int): MiuiHolidayApi.YearData? {
        return try {
            val root = JSONObject(text)
            val year = root.optInt("year", expectedYear)
            val days = root.optJSONArray("days") ?: return null
            val freeday = mutableSetOf<Int>()
            val workday = mutableSetOf<Int>()
            for (i in 0 until days.length()) {
                val d = days.getJSONObject(i)
                val date = LocalDate.parse(d.optString("date")) ?: continue
                if (date.year != year) continue
                if (d.optBoolean("isOffDay", false)) freeday += date.dayOfYear
                else workday += date.dayOfYear
            }
            if (freeday.isEmpty() && workday.isEmpty()) return null
            MiuiHolidayApi.YearData(year = year, freeday = freeday, workday = workday)
        } catch (_: Exception) {
            null
        }
    }

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        val cm = org.librelab.calendar.CalendarApp.appContext
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        try {
            val network = runCatching {
                cm.allNetworks.firstOrNull { n ->
                    val caps = cm.getNetworkCapabilities(n)
                    caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            }.getOrNull()
            if (network != null) cm.bindProcessToNetwork(network)

            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "LibreLab-Calendar/1.2")
            val code = conn.responseCode
            if (code != 200) return null
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                runCatching { cm.bindProcessToNetwork(null) }
            }
            conn?.disconnect()
        }
    }
}
