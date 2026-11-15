package dev.tvlauncher.home

import android.icu.text.DateFormat
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** A place for the weather (and, later, prayer times). */
class City(val id: String, val nameAr: String, val nameEn: String, val lat: Double, val lon: Double)

object Cities {
    val Jeddah = City("jeddah", "جدة", "Jeddah", 21.5433, 39.1728)
    val Riyadh = City("riyadh", "الرياض", "Riyadh", 24.7136, 46.6753)

    // Until the city picker arrives (batch 2), the city comes from the TV's time zone.
    // Asia/Riyadh covers all of Saudi Arabia; the user lives in Jeddah.
    private val byZone = mapOf(
        "Asia/Riyadh" to Jeddah,
        "Asia/Dubai" to City("dubai", "دبي", "Dubai", 25.2048, 55.2708),
        "Asia/Kuwait" to City("kuwait", "الكويت", "Kuwait City", 29.3759, 47.9774),
        "Asia/Qatar" to City("doha", "الدوحة", "Doha", 25.2854, 51.5310),
        "Asia/Bahrain" to City("manama", "المنامة", "Manama", 26.2285, 50.5860),
        "Asia/Muscat" to City("muscat", "مسقط", "Muscat", 23.5880, 58.3829),
        "Africa/Cairo" to City("cairo", "القاهرة", "Cairo", 30.0444, 31.2357),
    )

    fun default(): City = byZone[TimeZone.getDefault().id] ?: Jeddah
}

enum class Sky { Clear, PartlyCloudy, Cloudy, Rain, Thunder, Dust, Fog }

enum class DayPart { Morning, Afternoon, Sunset, Night }

/** The weather as last fetched from Open-Meteo for the city with [cityId]. */
class WeatherNow(val tempC: Double, val sky: Sky, val fetchedAt: Long, val cityId: String)

/** What the weather background shows: the sky and the time of day. */
data class WeatherScene(val sky: Sky, val part: DayPart) {
    val id: String get() = sky.name + "_" + part.name
}

/**
 * Open-Meteo (free, no key) is the only thing in the launcher that uses the internet.
 * Without internet nothing is shown as an error: the background simply follows the time of day.
 */
object WeatherService {
    private const val REFRESH_MS = 30 * 60_000L
    private const val USABLE_MS = 3 * 60 * 60_000L

    // WMO weather codes have no dust. Open-Meteo's dust reading is always high over the desert (Riyadh's
    // median was over 3000 µg/m³ on clear days), so dust is told apart by low visibility instead:
    // normally about 17 km in Riyadh, below 5 km only a few hours a week.
    private const val DUSTY_VISIBILITY_M = 5_000.0

    /** True when there is no weather yet, it is 30 minutes old, or it belongs to another city. */
    fun needsRefresh(weather: WeatherNow?, now: Long, city: City): Boolean =
        weather == null || weather.cityId != city.id || now - weather.fetchedAt > REFRESH_MS

    /** The weather if it is for [city] and recent enough to show; anything else is ignored. */
    fun usable(weather: WeatherNow?, now: Long, city: City): WeatherNow? =
        weather?.takeIf { it.cityId == city.id && now - it.fetchedAt in 0..USABLE_MS }

    /** The current weather, or null when there is no internet. Call it off the main thread. */
    fun fetch(city: City): WeatherNow? = try {
        val json = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=${city.lat}&longitude=${city.lon}" +
                    "&current=temperature_2m,weather_code,visibility&timezone=auto",
            ),
        )
        val current = json.getJSONObject("current")
        var sky = skyFor(current.getInt("weather_code"))
        val visibility = current.optDouble("visibility", Double.NaN)
        if ((sky == Sky.Clear || sky == Sky.PartlyCloudy || sky == Sky.Cloudy) && visibility < DUSTY_VISIBILITY_M) {
            sky = Sky.Dust
        }
        WeatherNow(current.getDouble("temperature_2m"), sky, System.currentTimeMillis(), city.id)
    } catch (e: Exception) {
        null
    }

    private fun skyFor(code: Int): Sky = when (code) {
        0, 1 -> Sky.Clear
        2 -> Sky.PartlyCloudy
        45, 48 -> Sky.Fog
        in 51..67, in 80..82 -> Sky.Rain
        95, 96, 99 -> Sky.Thunder
        else -> Sky.Cloudy // 3 = overcast; snow is shown as clouds
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/** Sunrise and sunset, calculated offline (NOAA formulas), in minutes after local midnight. */
object SunTimes {
    fun today(city: City, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
        val date = LocalDate.now(zone)
        val offsetMinutes = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 60
        val g = 2 * PI / 365.0 * (date.dayOfYear - 1)
        val equationOfTime = 229.18 * (
            0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g)
            )
        val declination = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val lat = Math.toRadians(city.lat)
        val cosHourAngle = cos(Math.toRadians(90.833)) / (cos(lat) * cos(declination)) - tan(lat) * tan(declination)
        val hourAngle = Math.toDegrees(acos(cosHourAngle.coerceIn(-1.0, 1.0)))
        val sunrise = 720 - 4 * (city.lon + hourAngle) - equationOfTime + offsetMinutes
        val sunset = 720 - 4 * (city.lon - hourAngle) - equationOfTime + offsetMinutes
        return sunrise.roundToInt() to sunset.roundToInt()
    }
}

fun dayPart(minuteOfDay: Int, sunrise: Int, sunset: Int): DayPart = when {
    minuteOfDay < sunrise - 30 || minuteOfDay >= sunset + 40 -> DayPart.Night
    minuteOfDay < sunrise + 240 -> DayPart.Morning
    minuteOfDay < sunset - 90 -> DayPart.Afternoon
    else -> DayPart.Sunset
}

/** No weather data (no internet): a clear sky that follows the time of day. */
fun sceneFor(weather: WeatherNow?, part: DayPart): WeatherScene {
    val sky = weather?.sky ?: Sky.Clear
    return if (part == DayPart.Night) {
        WeatherScene(if (sky == Sky.Clear) Sky.Clear else Sky.Cloudy, DayPart.Night)
    } else {
        WeatherScene(sky, part)
    }
}

/** The Hijri date by the Umm al-Qura calendar, for example "٢٥ جمادى الأولى ١٤٤٨ هـ". */
object HijriDate {
    fun format(millis: Long, locale: Locale): String = try {
        val uLocale = ULocale.forLocale(locale).setKeywordValue("calendar", "islamic-umalqura")
        val calendar = IslamicCalendar(uLocale)
        calendar.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        val formatter = DateFormat.getInstanceForSkeleton("dMMMMy", uLocale)
        formatter.calendar = calendar
        formatter.format(Date(millis))
    } catch (e: Exception) {
        ""
    }
}
