package dev.tvlauncher.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PreviewWidth = 198.dp
private val PreviewHeight = 111.dp
private val PreviewShape = RoundedCornerShape(14.dp)

/**
 * Picks the background. The focused choice shows on the whole screen right away ([onPreview]);
 * OK keeps it ([onChoose]) and Back cancels (handled by the caller).
 */
@Composable
fun BackgroundScreen(
    current: BackgroundStyle,
    sampleApp: LaunchableApp?,
    artCache: ArtCache,
    scene: WeatherScene,
    onPreview: (BackgroundStyle) -> Unit,
    onChoose: (BackgroundStyle) -> Unit,
) {
    val requesters = remember { BackgroundStyle.entries.associateWith { FocusRequester() } }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 32.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(R.string.background_title),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.background_hint),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BackgroundStyle.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { style ->
                        PreviewTile(
                            style = style,
                            sampleApp = sampleApp,
                            artCache = artCache,
                            scene = scene,
                            selected = style == current,
                            modifier = Modifier.focusRequester(requesters.getValue(style)),
                            onFocused = { onPreview(style) },
                            onClick = { onChoose(style) },
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { } // wait until the tiles are laid out
        runCatching { requesters.getValue(current).requestFocus() }
    }
    DisposableEffect(Unit) {
        onDispose { Backgrounds.dropPreviews() }
    }
}

@Composable
private fun PreviewTile(
    style: BackgroundStyle,
    sampleApp: LaunchableApp?,
    artCache: ArtCache,
    scene: WeatherScene,
    selected: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, style, sampleApp, scene) {
        value = withContext(Dispatchers.IO) {
            val backdrop = if (style == BackgroundStyle.FromApp && sampleApp != null) {
                artCache.loadBackdrop(sampleApp)
            } else {
                null
            }
            when {
                backdrop != null -> backdrop
                style == BackgroundStyle.Weather -> WeatherScenes.preview(scene)
                else -> Backgrounds.preview(if (style == BackgroundStyle.FromApp) BackgroundStyle.Night else style)
            }
        }
    }
    var focused by remember { mutableStateOf(false) }
    val name = stringResource(style.labelRes)
    val label = if (selected) name + " · " + stringResource(R.string.bg_current) else name
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .size(PreviewWidth, PreviewHeight)
                .onFocusChanged {
                    val now = it.isFocused || it.hasFocus
                    if (now && !focused) onFocused()
                    focused = now
                },
            shape = ClickableSurfaceDefaults.shape(shape = PreviewShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 16.dp),
            ),
            border = ClickableSurfaceDefaults.border(
                border = if (selected) {
                    Border(border = BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)), shape = PreviewShape)
                } else {
                    Border.None
                },
                focusedBorder = Border(border = BorderStroke(3.dp, Color.White), shape = PreviewShape),
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1B2430),
                focusedContainerColor = Color(0xFF1B2430),
            ),
        ) {
            val picture = image
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (focused) 1f else 0.7f),
            fontSize = 15.sp,
        )
    }
}
