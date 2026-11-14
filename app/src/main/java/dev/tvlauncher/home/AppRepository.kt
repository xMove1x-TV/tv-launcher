package dev.tvlauncher.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** An app that can be opened from the launcher. [updateTime] changes when the app is updated. */
class LaunchableApp(
    val packageName: String,
    val label: String,
    val activity: ActivityInfo,
    val updateTime: Long,
)

/** Pictures for a tile: the TV banner when the app has one, otherwise its icon. */
class Artwork(val banner: ImageBitmap?, val icon: ImageBitmap?)

/** Keeps loaded pictures in memory per app version, so no screen loads the same picture twice. */
class ArtCache(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Artwork>()

    fun peek(app: LaunchableApp): Artwork? = cache[key(app)]

    /** Returns the pictures, loading them if needed. Call it off the main thread. */
    fun load(app: LaunchableApp): Artwork =
        cache.getOrPut(key(app)) { AppRepository.loadArtwork(context, app) }

    private fun key(app: LaunchableApp) = app.packageName + ":" + app.updateTime
}

object AppRepository {

    private const val ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS"

    // Banners are 16:9; capping their size keeps memory low on an old TV.
    private const val BANNER_MAX_W = 480
    private const val BANNER_MAX_H = 270
    private const val ICON_MAX = 176

    /** Every app with a TV (leanback) or normal launcher entry, sorted by name. */
    fun queryApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val self = context.packageName
        val byPackage = LinkedHashMap<String, LaunchableApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(intent, 0)) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                if (pkg == self || byPackage.containsKey(pkg)) continue
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                val updated = try {
                    pm.getPackageInfo(pkg, 0).lastUpdateTime
                } catch (e: PackageManager.NameNotFoundException) {
                    0L
                }
                byPackage[pkg] = LaunchableApp(pkg, label, activity, updated)
            }
        }
        val collator = Collator.getInstance()
        return byPackage.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun loadArtwork(context: Context, app: LaunchableApp): Artwork {
        val pm = context.packageManager
        val banner = runCatching { app.activity.loadBanner(pm) }.getOrNull()
            ?.toImageBitmap(BANNER_MAX_W, BANNER_MAX_H)
        val icon = if (banner == null) {
            runCatching { app.activity.loadIcon(pm) }.getOrNull()?.toImageBitmap(ICON_MAX, ICON_MAX)
        } else {
            null
        }
        return Artwork(banner, icon)
    }

    fun launch(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return start(context, intent)
    }

    fun openSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS))

    /** The standard Android TV "inputs" screen (HDMI and other sources). On this TCL it is TCL's source menu. */
    fun canOpenInputs(context: Context): Boolean =
        Intent(ACTION_VIEW_INPUTS).resolveActivity(context.packageManager) != null

    fun openInputs(context: Context): Boolean = start(context, Intent(ACTION_VIEW_INPUTS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}

private fun Drawable.toImageBitmap(maxWidth: Int, maxHeight: Int): ImageBitmap {
    val srcW = if (intrinsicWidth > 0) intrinsicWidth else maxWidth
    val srcH = if (intrinsicHeight > 0) intrinsicHeight else maxHeight
    val scale = minOf(1f, maxWidth.toFloat() / srcW, maxHeight.toFloat() / srcH)
    val w = (srcW * scale).toInt().coerceAtLeast(1)
    val h = (srcH * scale).toInt().coerceAtLeast(1)
    if (this is BitmapDrawable) {
        val source = bitmap
        if (source != null && source.width == w && source.height == h) return source.asImageBitmap()
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val oldBounds = copyBounds()
    setBounds(0, 0, w, h)
    draw(Canvas(out))
    bounds = oldBounds
    return out.asImageBitmap()
}
