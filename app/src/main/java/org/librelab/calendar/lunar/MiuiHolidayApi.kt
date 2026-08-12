package org.librelab.calendar.lunar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

/**
 * 小米日历在线节假日 API (逆向自 com.miui.calendar, HyperOS 1.0.7.0).
 * 端点: GET https://api.comm.miui.com/holiday/holiday.jsp
 * 签名: sign = SHA1("calendar" + 排序后 key+value + secret).upper()
 * 响应明文 JSON: {"versioncode":N, "holiday":[{"year":Y,"workday":[doy...],"freeday":[doy...]}]}
 *  - freeday = 官方放假 (含春节/国庆 8 天连休)
 *  - workday = 调休补班日
 * 数据覆盖 2011-2026, 由小米服务器维护, 每年自动更新。
 */
object MiuiHolidayApi {

    private const val BASE = "https://api.comm.miui.com/holiday/holiday.jsp"
    private const val SECRET = "77eb2e8a5755abd016c0d69ba74b219c"
    private const val APPKEY = "calendar"

    data class YearData(
        val year: Int,
        /** 放假日期 (day-of-year, 1-based) */
        val freeday: Set<Int>,
        /** 调休补班日 (day-of-year, 1-based) */
        val workday: Set<Int>,
    ) {
        fun isFree(date: LocalDate): Boolean =
            date.year == year && freeday.contains(date.dayOfYear)

        fun isWorkday(date: LocalDate): Boolean =
            date.year == year && workday.contains(date.dayOfYear)
    }

    /** 拉取全年节假日数据 (仅指定年份) */
    fun fetchYear(year: Int): YearData? {
        val all = fetchYears(year, year)
        return all.firstOrNull { it.year == year }
    }

    /** 拉取 [fromYear, toYear] 区间数据 */
    fun fetchYears(fromYear: Int, toYear: Int): List<YearData> {
        val params = mutableMapOf(
            "dataVersion" to "0",
            "locale" to "zh_CN",
            "language" to "zh",
            "year" to fromYear.toString(),
            "month" to "1",
            "d" to UUID.randomUUID().toString(),
            "screenWidth" to "1080",
            "screenHeight" to "2400",
            "r" to "CN",
            "model" to "umi",
            "mv" to "13.0",
            "v" to "OS1.0.7.0.TJBCNXM",
            "n" to "MIUICalendar",
            "t" to "1",
            "timestamp" to System.currentTimeMillis().toString(),
            "versionCode" to "10000130",
            "m" to UUID.randomUUID().toString(),
            "u" to "1",
            "oaid" to UUID.randomUUID().toString(),
            "android_version" to "13",
            "ad_status" to "0",
            "restrictImei" to "0",
            "appkey" to APPKEY,
        )
        params["sign"] = sign(params)

        val query = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        // 显式绑定网络打开连接: getActiveNetwork() 在 app 启动早期可能为 null,
        // 用 getAllNetworks 取第一个可联网的网络, 确保连接/解析走真实网络
        var conn: HttpURLConnection? = null
        try {
            val cm = org.librelab.calendar.CalendarApp.appContext
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork
                ?: cm.allNetworks.firstOrNull { n ->
                    cm.getNetworkCapabilities(n)?.hasCapability(
                        android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) == true
                }
            conn = if (net != null) {
                net.openConnection(URL("$BASE?$query")) as HttpURLConnection
            } else {
                URL("$BASE?$query").openConnection() as HttpURLConnection
            }
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "okhttp/4.9.2")
            conn.setRequestProperty("Accept-Encoding", "identity")
            val code = conn.responseCode
            if (code != 200) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val arr = json.getJSONArray("holiday")
            val result = mutableListOf<YearData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val y = obj.getInt("year")
                if (y in fromYear..toYear) {
                    result += YearData(
                        year = y,
                        freeday = obj.optJSONArray("freeday")?.let { a ->
                            (0 until a.length()).map { a.getInt(it) }.toSet()
                        } ?: emptySet(),
                        workday = obj.optJSONArray("workday")?.let { a ->
                            (0 until a.length()).map { a.getInt(it) }.toSet()
                        } ?: emptySet(),
                    )
                }
            }
            return result
        } finally {
            conn?.disconnect()
        }
    }

    /** 签名: SHA1(appkey + 排序后 key+value 拼接 + secret).upper() */
    private fun sign(params: Map<String, String>): String {
        val sb = StringBuilder(APPKEY)
        params.keys.sorted().forEach { k ->
            sb.append(k).append(params[k])
        }
        sb.append(SECRET)
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(sb.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.uppercase()
    }
}
