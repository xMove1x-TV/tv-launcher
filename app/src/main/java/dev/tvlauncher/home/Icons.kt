package dev.tvlauncher.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Tajawal (SIL Open Font License, bundled in res/font, license in licenses/Tajawal-OFL.txt). */
val Tajawal = FontFamily(
    Font(R.font.tajawal_extralight, FontWeight.ExtraLight),
    Font(R.font.tajawal_light, FontWeight.Light),
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
)

/** Small line icons for the top buttons (Material icon shapes, Apache License 2.0). */
object LauncherIcons {
    val AllApps: ImageVector by lazy {
        icon(
            "M4,8h4V4H4V8zM10,20h4v-4h-4V20zM4,20h4v-4H4V20zM4,14h4v-4H4V14zM10,14h4v-4h-4V14z" +
                "M16,4v4h4V4H16zM10,8h4V4h-4V8zM16,14h4v-4h-4V14zM16,20h4v-4h-4V20z",
        )
    }

    val Inputs: ImageVector by lazy {
        icon(
            "M21,3.01H3c-1.1,0 -2,0.9 -2,2V9h2V4.99h18v14.03H3V15H1v4.01c0,1.1 0.9,1.98 2,1.98h18" +
                "c1.1,0 2,-0.88 2,-1.98v-14c0,-1.11 -0.9,-2 -2,-2zM11,16l4,-4 -4,-4v3H1v2h10v3z",
        )
    }

    val Settings: ImageVector by lazy {
        icon(
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94c0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58" +
                "c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96" +
                "c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84" +
                "c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33" +
                "c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87C2.62,9.08 2.66,9.34 2.86,9.48l2.03,1.58" +
                "C4.84,11.36 4.8,11.69 4.8,12s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61" +
                "l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54" +
                "c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54" +
                "c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32" +
                "c0.12,-0.22 0.07,-0.47 -0.12,-0.61L19.14,12.94zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6" +
                "s1.62,-3.6 3.6,-3.6s3.6,1.62 3.6,3.6S13.98,15.6 12,15.6z",
        )
    }

    private fun icon(path: String): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.White)).build()
}

private val SunColor = Color(0xFFFFD66B)

/** A small weather symbol drawn in code: sun or moon, clouds, rain, lightning, fog or dust. */
@Composable
fun WeatherGlyph(sky: Sky, night: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        when (sky) {
            Sky.Clear -> if (night) moon(Offset(s * 0.5f, s * 0.5f), s * 0.34f) else sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f)
            Sky.PartlyCloudy -> {
                if (night) moon(Offset(s * 0.38f, s * 0.36f), s * 0.24f) else sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f)
                cloud(Offset(s * 0.58f, s * 0.66f), s * 0.62f, Color.White)
            }
            Sky.Cloudy -> {
                cloud(Offset(s * 0.38f, s * 0.42f), s * 0.5f, Color.White.copy(alpha = 0.6f))
                cloud(Offset(s * 0.56f, s * 0.6f), s * 0.7f, Color.White)
            }
            Sky.Rain -> {
                cloud(Offset(s * 0.5f, s * 0.42f), s * 0.74f, Color.White)
                for (i in 0..2) {
                    val x = s * (0.32f + i * 0.18f)
                    drawLine(Color(0xFF9BD1FF), Offset(x, s * 0.7f), Offset(x - s * 0.06f, s * 0.92f), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.Thunder -> {
                cloud(Offset(s * 0.5f, s * 0.4f), s * 0.74f, Color.White)
                val bolt = Path().apply {
                    moveTo(s * 0.56f, s * 0.56f)
                    lineTo(s * 0.40f, s * 0.80f)
                    lineTo(s * 0.52f, s * 0.80f)
                    lineTo(s * 0.44f, s * 1.0f)
                    lineTo(s * 0.66f, s * 0.72f)
                    lineTo(s * 0.54f, s * 0.72f)
                    close()
                }
                drawPath(bolt, SunColor)
            }
            Sky.Fog -> {
                for (i in 0..2) {
                    val y = s * (0.32f + i * 0.18f)
                    drawLine(Color.White, Offset(s * (0.16f + i * 0.06f), y), Offset(s * (0.84f - i * 0.04f), y), s * 0.08f, StrokeCap.Round)
                }
            }
            Sky.Dust -> {
                drawCircle(SunColor.copy(alpha = 0.55f), s * 0.16f, Offset(s * 0.66f, s * 0.3f))
                for (i in 0..2) {
                    val y = s * (0.5f + i * 0.16f)
                    drawLine(Color(0xFFE7C99A), Offset(s * 0.14f, y), Offset(s * (0.86f - i * 0.12f), y), s * 0.07f, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float) {
    drawCircle(SunColor, radius, center)
    for (i in 0 until 8) {
        val angle = i * Math.PI / 4
        val start = Offset(center.x + cos(angle).toFloat() * radius * 1.45f, center.y + sin(angle).toFloat() * radius * 1.45f)
        val end = Offset(center.x + cos(angle).toFloat() * radius * 2.0f, center.y + sin(angle).toFloat() * radius * 2.0f)
        drawLine(SunColor, start, end, radius * 0.32f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val full = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
    }
    val bite = Path().apply {
        val c = Offset(center.x + radius * 0.55f, center.y - radius * 0.35f)
        addOval(androidx.compose.ui.geometry.Rect(c.x - radius, c.y - radius, c.x + radius, c.y + radius))
    }
    val crescent = Path()
    crescent.op(full, bite, PathOperation.Difference)
    drawPath(crescent, Color(0xFFE9EEFF))
}

/** A simple cloud of three circles on a rounded base, [width] wide, centred at [center]. */
private fun DrawScope.cloud(center: Offset, width: Float, color: Color) {
    val h = width * 0.5f
    val left = center.x - width / 2
    val top = center.y - h / 2
    drawCircle(color, h * 0.36f, Offset(left + width * 0.3f, top + h * 0.55f))
    drawCircle(color, h * 0.5f, Offset(left + width * 0.55f, top + h * 0.42f))
    drawCircle(color, h * 0.32f, Offset(left + width * 0.78f, top + h * 0.62f))
    drawRoundRect(
        color,
        topLeft = Offset(left + width * 0.12f, top + h * 0.55f),
        size = Size(width * 0.78f, h * 0.42f),
        cornerRadius = CornerRadius(h * 0.21f),
    )
}
