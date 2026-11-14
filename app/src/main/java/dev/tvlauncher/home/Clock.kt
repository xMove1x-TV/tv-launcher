package dev.tvlauncher.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/** Time and date. Wakes up once a minute, nothing more. */
@Composable
fun Clock() {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 200L)
        }
    }

    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = remember(locale, is24Hour) { SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale) }
    val dateFormat = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }
    val date = Date(now)

    Column {
        Text(
            text = timeFormat.format(date),
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            text = dateFormat.format(date),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
        )
    }
}
