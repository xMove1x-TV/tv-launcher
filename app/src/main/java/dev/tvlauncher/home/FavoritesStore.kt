package dev.tvlauncher.home

import android.content.Context

/** Keeps the ordered list of favorite apps (package names) in SharedPreferences. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(): List<String>? =
        prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }

    fun save(packages: List<String>) {
        prefs.edit().putString(KEY, packages.joinToString("\n")).apply()
    }

    /**
     * Turns the saved list into installed apps. On the very first run it starts from [DEFAULTS]
     * (the apps kept on this TV) and tops up to [MIN] with other installed apps.
     */
    fun resolve(apps: List<LaunchableApp>): List<LaunchableApp> {
        val byPackage = apps.associateBy { it.packageName }
        val saved = load()
        var result = (saved ?: DEFAULTS).distinct().mapNotNull { byPackage[it] }.take(MAX)
        if (saved == null && apps.isNotEmpty()) {
            if (result.size < MIN) {
                val chosen = result.map { it.packageName }.toSet()
                result = result + apps.filter { it.packageName !in chosen }.take(MIN - result.size)
            }
            save(result.map { it.packageName })
        }
        return result
    }

    companion object {
        private const val KEY = "favorites"
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
