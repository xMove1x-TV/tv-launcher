package dev.tvlauncher.home

import android.content.Context

/** Saves the launcher's choices in SharedPreferences: the ordered favorites and the hidden apps. */
class LauncherStore(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun saveFavorites(packages: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, packages.joinToString("\n")).apply()
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
