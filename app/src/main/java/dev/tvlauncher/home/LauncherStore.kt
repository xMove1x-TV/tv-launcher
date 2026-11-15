package dev.tvlauncher.home

import android.content.Context

/** Saves the launcher's choices in SharedPreferences: the ordered favorites, the hidden apps and the background. */
class LauncherStore(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun saveFavorites(packages: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, packages.joinToString("\n")).apply()
    }

    fun loadBackground(): BackgroundStyle {
        // The redesign makes the weather the background once; after that the user's choice is kept.
        if (!prefs.getBoolean(KEY_REDESIGN_1, false)) {
            prefs.edit().putBoolean(KEY_REDESIGN_1, true).putString(KEY_BACKGROUND, BackgroundStyle.Weather.id).apply()
            return BackgroundStyle.Weather
        }
        return BackgroundStyle.fromId(prefs.getString(KEY_BACKGROUND, null))
    }

    /** The last weather fetched, so the background is right at once after a restart, even offline. */
    fun loadWeather(): WeatherNow? {
        val parts = prefs.getString(KEY_WEATHER, null)?.split('|') ?: return null
        if (parts.size != 3) return null
        val temp = parts[0].toDoubleOrNull() ?: return null
        val sky = Sky.entries.firstOrNull { it.name == parts[1] } ?: return null
        val at = parts[2].toLongOrNull() ?: return null
        return WeatherNow(temp, sky, at)
    }

    fun saveWeather(weather: WeatherNow) {
        prefs.edit().putString(KEY_WEATHER, "${weather.tempC}|${weather.sky.name}|${weather.fetchedAt}").apply()
    }

    fun saveBackground(style: BackgroundStyle) {
        prefs.edit().putString(KEY_BACKGROUND, style.id).apply()
    }

    fun loadHidden(): Set<String> = readList(KEY_HIDDEN)?.toSet().orEmpty()

    fun saveHidden(packages: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, packages.sorted().joinToString("\n")).apply()
    }

    /**
     * The favorites to show, as package names: installed, not hidden, at most [MAX].
     * On the very first run it starts from [DEFAULTS] (the apps kept on this TV) and tops up to [MIN].
     */
    fun resolveFavorites(apps: List<LaunchableApp>, hidden: Set<String>): List<String> {
        val installed = apps.map { it.packageName }.toSet()
        val saved = readList(KEY_FAVORITES)
        var result = (saved ?: DEFAULTS).distinct().filter { it in installed && it !in hidden }.take(MAX)
        if (saved == null && apps.isNotEmpty()) {
            if (result.size < MIN) {
                val extra = apps.map { it.packageName }.filter { it !in result && it !in hidden }
                result = result + extra.take(MIN - result.size)
            }
            saveFavorites(result)
        }
        return result
    }

    private fun readList(key: String): List<String>? =
        prefs.getString(key, null)?.split('\n')?.filter { it.isNotBlank() }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_REDESIGN_1 = "redesign_1"
        private const val KEY_WEATHER = "weather"
        const val MIN = 6
        const val MAX = 12

        val DEFAULTS = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "net.mbc.shahidTV",
            "com.amazon.amazonvideo.livingroom",
            "com.intigral.jawwytv",
            "com.thmanyah.shasha.tv",
            "com.crunchyroll.crunchyroid",
            "tv.twitch.android.app",
            "com.kick.mobile",
            "com.shadeed.leostore",
        )
    }
}
