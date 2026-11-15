package dev.tvlauncher.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val ScreenPadding = 48.dp
internal val TileShape = RoundedCornerShape(16.dp)
private val Accent = Color(0xFF4DA3FF)

private fun isOkKey(keyCode: Int): Boolean =
    keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == NativeKeyEvent.KEYCODE_ENTER ||
        keyCode == NativeKeyEvent.KEYCODE_NUMPAD_ENTER

/** Remembers that OK is being held, so the long-press action runs when OK is let go. */
private class PressFlag {
    var longPressed = false
}

/**
 * A rounded app tile that grows a little and casts a soft shadow when focused.
 * Holding OK (or pressing the remote's Menu key) runs [onLongClick] when the key is released, so the
 * release never lands on whatever the long press opened.
 */
@Composable
fun AppTile(
    app: LaunchableApp,
    art: Artwork?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    onFocused: () -> Unit = {},
) {
    val press = remember { PressFlag() }
    var focused by remember { mutableStateOf(false) }
    // A light sheen sweeps across the tile once when it gets the focus.
    val shine = remember { Animatable(-1f) }
    LaunchedEffect(focused) {
        if (focused) {
            shine.snapTo(-0.6f)
            shine.animateTo(1.6f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }
    Surface(
        onClick = onClick,
        onLongClick = if (onLongClick != null) ({ press.longPressed = true }) else null,
        modifier = modifier
            .onFocusChanged {
                val now = it.isFocused || it.hasFocus
                if (now) onFocused()
                focused = now
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    onLongClick == null -> false
                    isOkKey(native.keyCode) -> {
                        if (native.action == NativeKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                            press.longPressed = false
                        } else if (native.action == NativeKeyEvent.ACTION_UP && press.longPressed) {
                            press.longPressed = false
                            onLongClick?.invoke()
                        }
                        false
                    }
                    native.keyCode == NativeKeyEvent.KEYCODE_MENU -> {
                        if (native.action == NativeKeyEvent.ACTION_UP) onLongClick?.invoke()
                        true
                    }
                    else -> false
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (highlight) 1.16f else 1.12f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.7f), elevation = 22.dp),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (highlight) Border(border = BorderStroke(3.dp, Accent), shape = TileShape) else Border.None,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF263241),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF34445A),
            focusedContentColor = Color.White,
        ),
    ) {
        TileContent(app, art)
        // A soft gloss on the top half plus the sweeping sheen, only while focused.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    if (!focused) return@drawBehind
                    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.45f to Color.Transparent))
                    val x = shine.value * size.width
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                            start = Offset(x - size.width * 0.35f, 0f),
                            end = Offset(x + size.width * 0.05f, size.height),
                        ),
                    )
                },
        )
    }
}

@Composable
private fun TileContent(app: LaunchableApp, art: Artwork?) {
    val banner = art?.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = app.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val icon = art?.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(text = app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The app's pictures: from memory at once if already loaded, otherwise loaded in the background. */
@Composable
fun rememberArtwork(app: LaunchableApp, cache: ArtCache): Artwork? {
    var art by remember(app.packageName, app.updateTime) { mutableStateOf(cache.peek(app)) }
    if (art == null) {
        LaunchedEffect(app.packageName, app.updateTime) {
            art = withContext(Dispatchers.IO) { cache.load(app) }
        }
    }
    return art
}

/** A small pill button, used for the shortcuts in the top bar. */
@Composable
fun ShortcutButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

/** Fades its content in, with a very slight zoom, when it first appears. */
@Composable
fun Appear(
    modifier: Modifier = Modifier,
    durationMillis: Int = 240,
    fromScale: Float = 0.97f,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis, easing = FastOutSlowInEasing))
    }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            val scale = fromScale + (1f - fromScale) * p
            scaleX = scale
            scaleY = scale
        },
    ) {
        content()
    }
}

class MenuOption(val label: String, val action: () -> Unit)

/** A small centered menu. Back closes it. */
@Composable
fun OptionsDialog(title: String, options: List<MenuOption>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val firstOption = remember { FocusRequester() }
        // Ignore an OK release (or held-key repeats) that started before the menu opened.
        var armed by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1B2430))
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (!isOkKey(native.keyCode)) return@onPreviewKeyEvent false
                    if (native.action == NativeKeyEvent.ACTION_DOWN && native.repeatCount == 0) armed = true
                    !armed
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            options.forEachIndexed { index, option ->
                MenuButton(
                    text = option.label,
                    onClick = option.action,
                    modifier = if (index == 0) Modifier.focusRequester(firstOption) else Modifier,
                )
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstOption.requestFocus() }
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = Color.White.copy(alpha = 0.9f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
