package dev.tvlauncher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The backgrounds the user can pick. [FromApp] follows the focused app; the others are drawn once. */
enum class BackgroundStyle(val id: String, val labelRes: Int) {
    Night("night", R.string.bg_night),
    Aurora("aurora", R.string.bg_aurora),
    Sunset("sunset", R.string.bg_sunset),
    Ocean("ocean", R.string.bg_ocean),
    Forest("forest", R.string.bg_forest),
    Graphite("graphite", R.string.bg_graphite),
    FromApp("app", R.string.bg_from_app),
    ;

    companion object {
        val Default = Night

        fun fromId(id: String?): BackgroundStyle = BackgroundStyle.entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * Android 9 has no live blur, so backgrounds are soft gradients that look like blurred photos.
 * Each one is drawn once into a small bitmap and stretched to the screen, which costs the GPU a single
 * picture per frame.
 */
object Backgrounds {
    private const val FULL_W = 512
    private const val FULL_H = 288
    private const val PREVIEW_W = 192
    private const val PREVIEW_H = 108
    private const val BACKDROP_W = 48
    private const val BACKDROP_H = 27

    // Only the last few full-size backgrounds stay in memory.
    private val fullCache = object : LinkedHashMap<BackgroundStyle, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<BackgroundStyle, ImageBitmap>?): Boolean =
            size > 3
    }
    private val previewCache = HashMap<BackgroundStyle, ImageBitmap>()

    /** The background for the whole screen. Call it off the main thread. */
    fun full(style: BackgroundStyle): ImageBitmap =
        synchronized(fullCache) { fullCache.getOrPut(style) { render(style, FULL_W, FULL_H) } }

    /** A small picture for the background picker. Call it off the main thread. */
    fun preview(style: BackgroundStyle): ImageBitmap =
        synchronized(previewCache) { previewCache.getOrPut(style) { render(style, PREVIEW_W, PREVIEW_H) } }

    /** Frees the picker pictures once the picker closes. */
    fun dropPreviews() {
        synchronized(previewCache) { previewCache.clear() }
    }

    private fun render(style: BackgroundStyle, width: Int, height: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        when (style) {
            BackgroundStyle.Night, BackgroundStyle.FromApp -> {
                linear(canvas, w, h, 0xFF1C2A3A, 0xFF0A0E15)
                blob(canvas, 0.15f * w, 0.10f * h, 0.70f * w, 0xFF2B4A6B, 0.35f)
            }
            BackgroundStyle.Aurora -> {
                canvas.drawColor(0xFF060A12.toInt())
                blob(canvas, 0.18f * w, 0.15f * h, 0.70f * w, 0xFF1B8A7A, 0.55f)
                blob(canvas, 0.80f * w, 0.25f * h, 0.60f * w, 0xFF5B3A9A, 0.50f)
                blob(canvas, 0.55f * w, 1.00f * h, 0.75f * w, 0xFF0F4C6E, 0.55f)
            }
            BackgroundStyle.Sunset -> {
                canvas.drawColor(0xFF120818.toInt())
                blob(canvas, 0.82f * w, 0.12f * h, 0.65f * w, 0xFFB24A5E, 0.55f)
                blob(canvas, 0.20f * w, 0.30f * h, 0.60f * w, 0xFF4A2470, 0.55f)
                blob(canvas, 0.50f * w, 1.05f * h, 0.80f * w, 0xFFD9792E, 0.40f)
            }
            BackgroundStyle.Ocean -> {
                canvas.drawColor(0xFF03101A.toInt())
                blob(canvas, 0.25f * w, 0.20f * h, 0.70f * w, 0xFF0F5C80, 0.55f)
                blob(canvas, 0.85f * w, 0.55f * h, 0.55f * w, 0xFF0E8A8A, 0.35f)
                blob(canvas, 0.50f * w, 1.00f * h, 0.80f * w, 0xFF062E48, 0.60f)
            }
            BackgroundStyle.Forest -> {
                canvas.drawColor(0xFF06120B.toInt())
                blob(canvas, 0.20f * w, 0.20f * h, 0.70f * w, 0xFF236B45, 0.50f)
                blob(canvas, 0.80f * w, 0.30f * h, 0.60f * w, 0xFF4F6B22, 0.40f)
                blob(canvas, 0.50f * w, 1.00f * h, 0.80f * w, 0xFF0B3322, 0.60f)
            }
            BackgroundStyle.Graphite -> {
                linear(canvas, w, h, 0xFF2B2F36, 0xFF101215)
                blob(canvas, 0.50f * w, 0.00f * h, 0.80f * w, 0xFF4A505C, 0.35f)
            }
        }
        // A soft darkening at the bottom keeps the tiles and their names easy to read.
        val shade = Paint(Paint.DITHER_FLAG)
        shade.shader = LinearGradient(0f, 0.45f * h, 0f, h, 0x00000000, 0x59000000, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, shade)
        return bitmap.asImageBitmap()
    }

    private fun linear(canvas: Canvas, w: Float, h: Float, top: Long, bottom: Long) {
        val paint = Paint(Paint.DITHER_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    /** A soft round glow of [color] that fades out towards [radius]. */
    private fun blob(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Long, strength: Float) {
        val rgb = (color and 0xFFFFFFL).toInt()
        val inner = ((strength * 255).toInt() shl 24) or rgb
        val middle = ((strength * 0.4f * 255).toInt() shl 24) or rgb
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(inner, middle, rgb),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }

    /**
     * A tiny, strongly blurred and darkened copy of an app's picture (48x27 pixels, about 5 KB).
     * The screen stretches it smoothly, which looks like a blurred photo of the app.
     */
    fun backdrop(art: Artwork): ImageBitmap? {
        val source = (art.banner ?: art.icon)?.asAndroidBitmap() ?: return null
        var small = source
        while (small.width > 4 * BACKDROP_W) {
            small = Bitmap.createScaledBitmap(small, small.width / 2, maxOf(1, small.height / 2), true)
        }
        val tiny = Bitmap.createBitmap(BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tiny)
        canvas.drawColor(0xFF0A0E15.toInt())
        canvas.drawBitmap(small, null, Rect(0, 0, BACKDROP_W, BACKDROP_H), Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(BACKDROP_W * BACKDROP_H)
        tiny.getPixels(pixels, 0, BACKDROP_W, 0, 0, BACKDROP_W, BACKDROP_H)
        repeat(2) { boxBlur(pixels, BACKDROP_W, BACKDROP_H, 3) }
        // Bright pictures (white banners like YouTube or Netflix) are darkened much more than dark ones,
        // so the white text on top always stays easy to read.
        var sum = 0f
        for (c in pixels) sum += luminance(c)
        val average = sum / pixels.size
        val factor = minOf(0.6f, 48f / maxOf(average, 1f))
        for (i in pixels.indices) pixels[i] = darken(pixels[i], factor)
        tiny.setPixels(pixels, 0, BACKDROP_W, 0, 0, BACKDROP_W, BACKDROP_H)
        return tiny.asImageBitmap()
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val n = 2 * r + 1
        val tmp = IntArray(px.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var red = 0
                var green = 0
                var blue = 0
                for (k in -r..r) {
                    val c = px[y * w + (x + k).coerceIn(0, w - 1)]
                    red += (c shr 16) and 0xFF
                    green += (c shr 8) and 0xFF
                    blue += c and 0xFF
                }
                tmp[y * w + x] = (0xFF shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
            }
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                var red = 0
                var green = 0
                var blue = 0
                for (k in -r..r) {
                    val c = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    red += (c shr 16) and 0xFF
                    green += (c shr 8) and 0xFF
                    blue += c and 0xFF
                }
                px[y * w + x] = (0xFF shl 24) or ((red / n) shl 16) or ((green / n) shl 8) or (blue / n)
            }
        }
    }

    private fun luminance(c: Int): Float =
        0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

    private fun darken(c: Int, factor: Float): Int {
        val red = (((c shr 16) and 0xFF) * factor).toInt()
        val green = (((c shr 8) and 0xFF) * factor).toInt()
        val blue = ((c and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}

private class ShownBackground(val image: ImageBitmap, val fromApp: Boolean)

// Keeps text readable over an app's colors.
private val AppScrim = Brush.verticalGradient(
    listOf(Color(0x66000000), Color(0x33000000), Color(0x99000000)),
)

/**
 * The full-screen background behind every screen. Changes cross-fade softly. With [BackgroundStyle.FromApp]
 * it follows [app], waiting a moment so quick moves along the tiles do not make it flicker.
 */
@Composable
fun LauncherBackground(style: BackgroundStyle, app: LaunchableApp?, artCache: ArtCache) {
    var shown by remember { mutableStateOf<ShownBackground?>(null) }
    val key = if (style == BackgroundStyle.FromApp) "app:" + app?.packageName else style.id
    LaunchedEffect(key) {
        if (style == BackgroundStyle.FromApp && shown != null) delay(250)
        shown = withContext(Dispatchers.IO) {
            val backdrop = if (style == BackgroundStyle.FromApp && app != null) artCache.loadBackdrop(app) else null
            if (backdrop != null) {
                ShownBackground(backdrop, fromApp = true)
            } else {
                val preset = if (style == BackgroundStyle.FromApp) BackgroundStyle.Night else style
                ShownBackground(Backgrounds.full(preset), fromApp = false)
            }
        }
    }
    Crossfade(targetState = shown, animationSpec = tween(durationMillis = 450), label = "background") { bg ->
        if (bg == null) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0E15)))
        } else {
            Image(
                bitmap = bg.image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
            if (bg.fromApp) Box(modifier = Modifier.fillMaxSize().background(AppScrim))
        }
    }
}
