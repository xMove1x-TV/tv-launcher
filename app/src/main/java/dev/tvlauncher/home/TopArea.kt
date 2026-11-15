package dev.tvlauncher.home

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val SoftShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), offset = Offset(0f, 2f), blurRadius = 10f)

/**
 * The top of the home screen: a big thin clock, a greeting, the Gregorian and Hijri (Umm al-Qura)
 * dates, the weather, and the small buttons. In edit mode the buttons become the edit actions.
 */
@Composable
fun TopArea(
    now: Long,
    weather: WeatherNow?,
    night: Boolean,
    editing: Boolean,
    showAdd: Boolean,
    showBackground: Boolean,
    canOpenInputs: Boolean,
    onAllApps: () -> Unit,
    onAddApp: () -> Unit,
    onBackground: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val date = Date(now)
    val time = SimpleDateFormat(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", locale).format(date)
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(if (hour in 4..11) R.string.good_morning else R.string.good_evening)
    val gregorian = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(date)
    val hijri = remember(now / 60_000L) { HijriDate.format(now, locale) }
    val shadowed = LocalTextStyle.current.copy(shadow = SoftShadow)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            color = Color.White,
            fontSize = 76.sp,
            fontWeight = FontWeight.ExtraLight,
            style = shadowed,
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(text = greeting, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, style = shadowed)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hijri.isEmpty()) gregorian else "$gregorian  ·  $hijri",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 17.sp,
                style = shadowed,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    if (showAdd) ShortcutButton(text = stringResource(R.string.add_app), onClick = onAddApp)
                    if (showBackground) ShortcutButton(text = stringResource(R.string.background), onClick = onBackground)
                    ShortcutButton(text = stringResource(R.string.done), onClick = onDone)
                } else {
                    IconShortcut(LauncherIcons.AllApps, stringResource(R.string.all_apps), onAllApps)
                    if (canOpenInputs) {
                        IconShortcut(LauncherIcons.Inputs, stringResource(R.string.inputs)) { AppRepository.openInputs(context) }
                    }
                    IconShortcut(LauncherIcons.Settings, stringResource(R.string.settings)) { AppRepository.openSettings(context) }
                }
            }
            if (weather != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(weather.sky, night, Modifier.size(30.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = NumberFormat.getIntegerInstance(locale).format(weather.tempC.roundToInt()) + "°",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Light,
                        style = shadowed,
                    )
                }
            }
        }
    }
}

/** A small round icon button; its name slides out only while it is focused. */
@Composable
fun IconShortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused || it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White.copy(alpha = 0.9f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                imageVector = icon,
                contentDescription = label,
                colorFilter = ColorFilter.tint(LocalContentColor.current),
                modifier = Modifier.size(22.dp),
            )
            AnimatedVisibility(
                visible = focused,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                )
            }
        }
    }
}
