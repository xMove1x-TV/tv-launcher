package dev.tvlauncher.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/**
 * TCL's system sends its staged boot signals (boot_t0, boot_t1, boot_t2) only when Google's home
 * screen (com.google.android.tvlauncher) shows. With Google's home screen disabled they never come,
 * so TCL's own services (remote, system updates, app states, assistant) would not start after a
 * restart. The launcher sends them once per restart instead, at about the same times the system did.
 *
 * Nothing is sent while Google's home screen is enabled: then the system sends them itself.
 */
object BootSignals {
    private const val GOOGLE_HOME = "com.google.android.tvlauncher"
    private const val PREFS_KEY = "boot_signals" // "<restart number>:<last stage sent>"

    // Like the system's own broadcast, it also reaches apps that are not running yet
    // (the hidden Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND).
    private const val FLAG_INCLUDE_BACKGROUND = 0x01000000

    // Each stage and when to send it, in milliseconds after the TV started.
    private val stages = listOf(
        "tcl.sys.intent.action.boot_t0" to 0L,
        "tcl.sys.intent.action.boot_t1" to 60_000L,
        "tcl.sys.intent.action.boot_t2" to 120_000L,
    )

    private var scheduled = false

    fun sendIfNeeded(context: Context) {
        if (scheduled) return
        val app = context.applicationContext
        if (isGoogleHomeEnabled(app)) return
        val restart = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (restart < 0) return
        val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        val saved = prefs.getString(PREFS_KEY, null)?.split(':')
        val alreadySent = if (saved?.getOrNull(0)?.toIntOrNull() == restart) {
            saved?.getOrNull(1)?.toIntOrNull() ?: -1
        } else {
            -1
        }
        if (alreadySent >= stages.lastIndex) return

        scheduled = true
        val handler = Handler(Looper.getMainLooper())
        val sinceStart = SystemClock.elapsedRealtime()
        stages.forEachIndexed { index, (action, at) ->
            if (index <= alreadySent) return@forEachIndexed
            // Keep the order even when several stages are already due.
            val delay = maxOf(0L, at - sinceStart) + index * 2_000L
            handler.postDelayed({
                app.sendBroadcast(Intent(action).addFlags(FLAG_INCLUDE_BACKGROUND))
                prefs.edit().putString(PREFS_KEY, "$restart:$index").apply()
            }, delay)
        }
    }

    private fun isGoogleHomeEnabled(context: Context): Boolean = try {
        when (context.packageManager.getApplicationEnabledSetting(GOOGLE_HOME)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else -> true
        }
    } catch (e: IllegalArgumentException) {
        false // not installed at all
    }
}
