package com.ohav.tvhome.data

import com.ohav.tvhome.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

enum class TileStyle { Color, Mono, Banner }

data class City(val id: String, val he: String, val en: String, val lat: Double, val lon: Double) {
    val name: String get() = if (Locale.getDefault().language in setOf("iw", "he")) he else en
}

/** Picked from a list so the remote never needs a keyboard. */
val CITIES = listOf(
    City("tlv", "תל אביב", "Tel Aviv", 32.0853, 34.7818),
    City("jlm", "ירושלים", "Jerusalem", 31.7683, 35.2137),
    City("hfa", "חיפה", "Haifa", 32.7940, 34.9896),
    City("rlz", "ראשון לציון", "Rishon LeZion", 31.9730, 34.7925),
    City("ptk", "פתח תקווה", "Petah Tikva", 32.0840, 34.8878),
    City("ash", "אשדוד", "Ashdod", 31.8044, 34.6553),
    City("ntn", "נתניה", "Netanya", 32.3215, 34.8532),
    City("bsh", "באר שבע", "Beersheba", 31.2518, 34.7913),
    City("hln", "חולון", "Holon", 32.0158, 34.7874),
    City("rgn", "רמת גן", "Ramat Gan", 32.0684, 34.8248),
    City("rhv", "רחובות", "Rehovot", 31.8928, 34.8113),
    City("hrz", "הרצליה", "Herzliya", 32.1624, 34.8447),
    City("kfs", "כפר סבא", "Kfar Saba", 32.1782, 34.9076),
    City("raa", "רעננה", "Ra'anana", 32.1848, 34.8713),
    City("mod", "מודיעין", "Modi'in", 31.8981, 35.0104),
    City("ask", "אשקלון", "Ashkelon", 31.6688, 34.5743),
    City("tib", "טבריה", "Tiberias", 32.7922, 35.5312),
    City("eil", "אילת", "Eilat", 29.5577, 34.9519),
)

fun cityOf(id: String) = CITIES.firstOrNull { it.id == id } ?: CITIES.first()

enum class Sky { Clear, Partly, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

data class WeatherNow(val cityId: String, val temp: Int, val code: Int, val day: Boolean, val hi: Int, val lo: Int, val at: Long) {
    val sky: Sky
        get() = when (code) {
            0 -> Sky.Clear
            1, 2 -> Sky.Partly
            45, 48 -> Sky.Fog
            in 51..57 -> Sky.Drizzle
            in 61..67, in 80..82 -> Sky.Rain
            in 71..77, 85, 86 -> Sky.Snow
            in 95..99 -> Sky.Storm
            else -> Sky.Cloudy
        }

    fun toJson(): String = JSONObject()
        .put("city", cityId).put("t", temp).put("c", code).put("d", day).put("hi", hi).put("lo", lo).put("at", at)
        .toString()

    companion object {
        fun fromJson(s: String): WeatherNow? = runCatching {
            val o = JSONObject(s)
            WeatherNow(o.getString("city"), o.getInt("t"), o.getInt("c"), o.getBoolean("d"), o.getInt("hi"), o.getInt("lo"), o.getLong("at"))
        }.getOrNull()
    }
}

/** Open-Meteo: free, no key. Fetched only when the home screen is shown and the cache is old. */
object WeatherApi {
    const val MAX_AGE_MS = 30 * 60_000L

    suspend fun fetch(city: City): WeatherNow = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("User-Agent", "TVHome/${BuildConfig.VERSION_NAME}")
        }
        try {
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            WeatherNow(
                cityId = city.id,
                temp = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                day = cur.getInt("is_day") == 1,
                hi = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lo = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                at = System.currentTimeMillis(),
            )
        } finally {
            conn.disconnect()
        }
    }
}
