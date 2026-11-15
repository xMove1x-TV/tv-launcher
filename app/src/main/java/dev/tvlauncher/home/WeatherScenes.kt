package dev.tvlauncher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * The weather backgrounds, drawn in code as soft "blurred photo" gradients, so they add nothing to
 * the app's size. Each scene (sky x time of day) is drawn once into a small bitmap that the screen
 * stretches; only the last few stay in memory.
 */
object WeatherScenes {
    private const val FULL_W = 512
    private const val FULL_H = 288
    private const val PREVIEW_W = 192
    private const val PREVIEW_H = 108

    private val cache = object : LinkedHashMap<String, ImageBitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean = size > 3
    }

    /** The scene for the whole screen. Call it off the main thread. */
    fun full(scene: WeatherScene): ImageBitmap =
        synchronized(cache) { cache.getOrPut(scene.id) { render(scene, FULL_W, FULL_H) } }

    /** A small picture of the scene for the background picker. Call it off the main thread. */
    fun preview(scene: WeatherScene): ImageBitmap = render(scene, PREVIEW_W, PREVIEW_H)

    private class Sky2(val top: Long, val bottom: Long)

    private fun clearSky(part: DayPart) = when (part) {
        DayPart.Morning -> Sky2(0xFF3D6E9E, 0xFFB7CFE0)
        DayPart.Afternoon -> Sky2(0xFF2265B0, 0xFF7FB3DF)
        DayPart.Sunset -> Sky2(0xFF3B2B63, 0xFFE58A50)
        DayPart.Night -> Sky2(0xFF060A18, 0xFF18254A)
    }

    private fun greySky(part: DayPart) = when (part) {
        DayPart.Morning -> Sky2(0xFF55616E, 0xFF98A3AD)
        DayPart.Afternoon -> Sky2(0xFF4E5965, 0xFFA2ABB3)
        DayPart.Sunset -> Sky2(0xFF463F55, 0xFFA07F72)
        DayPart.Night -> Sky2(0xFF0C111C, 0xFF262D3D)
    }

    private fun render(scene: WeatherScene, width: Int, height: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val w = width.toFloat()
        val h = height.toFloat()
        val part = scene.part
        var dim = 0x40 // how much the whole picture is darkened at the end (0-255)

        if (part == DayPart.Night) {
            if (scene.sky == Sky.Clear) {
                linear(c, w, h, clearSky(part))
                blob(c, 0.78f * w, 0.20f * h, 0.35f * w, 0xFFCFD8FF, 0.28f) // moonlight
                stars(c, w, h)
            } else {
                linear(c, w, h, greySky(part))
                blob(c, 0.25f * w, 0.30f * h, 0.40f * w, 0xFF3B4457, 0.50f)
                blob(c, 0.70f * w, 0.20f * h, 0.45f * w, 0xFF323B4D, 0.50f)
                blob(c, 0.50f * w, 0.70f * h, 0.50f * w, 0xFF2C3446, 0.45f)
            }
        } else {
            when (scene.sky) {
                Sky.Clear -> {
                    linear(c, w, h, clearSky(part))
                    sun(c, w, h, part, 1f)
                }
                Sky.PartlyCloudy -> {
                    linear(c, w, h, clearSky(part))
                    sun(c, w, h, part, 0.7f)
                    clouds(c, w, h, 0xFFE6ECF2, 0.45f)
                }
                Sky.Cloudy -> {
                    linear(c, w, h, greySky(part))
                    clouds(c, w, h, 0xFFB4BCC4, 0.45f)
                    dim = 0x55
                }
                Sky.Rain -> {
                    linear(
                        c, w, h,
                        if (part == DayPart.Sunset) Sky2(0xFF2E2838, 0xFF6B5A5A) else Sky2(0xFF26313B, 0xFF4F5D69),
                    )
                    clouds(c, w, h, 0xFF3A4550, 0.55f)
                    blob(c, 0.65f * w, 0.75f * h, 0.55f * w, 0xFF3E6A8A, 0.30f)
                }
                Sky.Thunder -> {
                    linear(c, w, h, Sky2(0xFF17142A, 0xFF34324A))
                    clouds(c, w, h, 0xFF2A2840, 0.55f)
                    blob(c, 0.30f * w, 0.25f * h, 0.35f * w, 0xFFB9A8FF, 0.40f) // a soft flash
                }
                Sky.Dust -> {
                    linear(
                        c, w, h,
                        when (part) {
                            DayPart.Morning -> Sky2(0xFF9C7A50, 0xFFD2AE78)
                            DayPart.Sunset -> Sky2(0xFF7A4A2E, 0xFFC26A3A)
                            else -> Sky2(0xFF8C6A42, 0xFFC99E62)
                        },
                    )
                    blob(c, 0.70f * w, 0.30f * h, 0.25f * w, 0xFFF3E0B5, 0.35f) // a pale sun behind the dust
                    blob(c, 0.30f * w, 0.60f * h, 0.60f * w, 0xFFB98A55, 0.40f)
                    dim = 0x5C
                }
                Sky.Fog -> {
                    linear(
                        c, w, h,
                        when (part) {
                            DayPart.Morning -> Sky2(0xFF8E979F, 0xFFC4CACE)
                            DayPart.Sunset -> Sky2(0xFF8C8288, 0xFFD0B4A2)
                            else -> Sky2(0xFF9AA2A9, 0xFFCDD2D5)
                        },
                    )
                    blob(c, 0.30f * w, 0.40f * h, 0.60f * w, 0xFFE8ECEF, 0.40f)
                    blob(c, 0.75f * w, 0.65f * h, 0.55f * w, 0xFFDDE2E6, 0.40f)
                    dim = 0x66
                }
            }
        }

        // Darken a little everywhere and more at the bottom, so white text and the tiles stay easy to read.
        c.drawColor(dim shl 24)
        val shade = Paint(Paint.DITHER_FLAG)
        shade.shader = LinearGradient(0f, 0.45f * h, 0f, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, shade)
        return bitmap.asImageBitmap()
    }

    private fun sun(c: Canvas, w: Float, h: Float, part: DayPart, strength: Float) {
        when (part) {
            DayPart.Morning -> blob(c, 0.82f * w, 0.78f * h, 0.55f * w, 0xFFFFE2B0, 0.55f * strength)
            DayPart.Afternoon -> blob(c, 0.78f * w, 0.12f * h, 0.45f * w, 0xFFFFF6D0, 0.60f * strength)
            DayPart.Sunset -> {
                blob(c, 0.70f * w, 0.95f * h, 0.65f * w, 0xFFFFB066, 0.70f * strength)
                blob(c, 0.20f * w, 0.65f * h, 0.45f * w, 0xFFC2476E, 0.35f * strength)
            }
            DayPart.Night -> Unit
        }
    }

    private fun clouds(c: Canvas, w: Float, h: Float, color: Long, strength: Float) {
        blob(c, 0.22f * w, 0.30f * h, 0.35f * w, color, strength)
        blob(c, 0.55f * w, 0.18f * h, 0.30f * w, color, strength)
        blob(c, 0.82f * w, 0.42f * h, 0.38f * w, color, strength)
        blob(c, 0.40f * w, 0.62f * h, 0.42f * w, color, strength * 0.8f)
    }

    private fun stars(c: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var seed = 12345L
        fun next(): Float {
            seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFFL
            return (seed % 10_000L) / 10_000f
        }
        repeat(45) {
            val x = next() * w
            val y = next() * h * 0.6f
            val r = (0.4f + next() * 0.8f) * (w / 512f)
            paint.color = ((60 + (next() * 120).toInt()) shl 24) or 0xFFFFFF
            c.drawCircle(x, y, r, paint)
        }
    }

    private fun linear(c: Canvas, w: Float, h: Float, sky: Sky2) {
        val paint = Paint(Paint.DITHER_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, sky.top.toInt(), sky.bottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
    }

    /** A soft round glow of [color] that fades out towards [radius]. */
    private fun blob(c: Canvas, cx: Float, cy: Float, radius: Float, color: Long, strength: Float) {
        val rgb = (color and 0xFFFFFFL).toInt()
        val alpha = (strength.coerceIn(0f, 1f) * 255).toInt()
        val inner = (alpha shl 24) or rgb
        val middle = ((alpha * 0.4f).toInt() shl 24) or rgb
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(inner, middle, rgb),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)
    }
}
