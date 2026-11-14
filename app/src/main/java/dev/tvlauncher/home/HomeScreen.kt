package dev.tvlauncher.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** A favorite app with its pictures, ready to draw. */
class FavoriteTile(val app: LaunchableApp, val art: Artwork)

private val ScreenPadding = 48.dp
private val TileGap = 24.dp
private val MaxTileWidth = 196.dp
private val TileShape = RoundedCornerShape(14.dp)

@Composable
fun LauncherApp(refreshTick: Int) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        HomeScreen(refreshTick)
    }
}

@Composable
private fun HomeScreen(refreshTick: Int) {
    val context = LocalContext.current
    // Pictures are cached per app version, so coming back home does not reload them.
    val artCache = remember { ConcurrentHashMap<String, Artwork>() }
    var tiles by remember { mutableStateOf<List<FavoriteTile>?>(null) }
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    val canOpenInputs = remember { AppRepository.canOpenInputs(context) }
    val firstTileFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    LaunchedEffect(refreshTick) {
        tiles = withContext(Dispatchers.IO) {
            val apps = AppRepository.queryApps(context)
            FavoritesStore(context).resolve(apps).map { app ->
                val cacheKey = app.packageName + ":" + AppRepository.lastUpdateTime(context, app.packageName)
                FavoriteTile(app, artCache.getOrPut(cacheKey) { AppRepository.loadArtwork(context, app) })
            }
        }
    }

    // Back does nothing on the home screen, like Apple TV. (While this is a normal app, press Home to leave.)
    BackHandler(enabled = true) {}

    val current = tiles
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C2A3A), Color(0xFF0A0E15)))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(canOpenInputs = canOpenInputs)
            Hero(
                tiles = current.orEmpty(),
                focusedPackage = focusedPackage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            when {
                current == null -> Unit
                current.isEmpty() -> Text(
                    text = stringResource(R.string.no_favorites),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(ScreenPadding),
                )
                else -> FavoriteRows(
                    tiles = current,
                    firstTileFocus = firstTileFocus,
                    onFocused = { focusedPackage = it },
                    onOpen = { AppRepository.launch(context, it) },
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    LaunchedEffect(current?.isNotEmpty()) {
        if (!initialFocusDone && current != null && current.isNotEmpty()) {
            withFrameNanos { } // wait until the tiles are laid out
            initialFocusDone = runCatching { firstTileFocus.requestFocus() }.isSuccess
        }
    }
}

@Composable
private fun TopBar(canOpenInputs: Boolean) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Clock()
        Spacer(modifier = Modifier.weight(1f))
        if (canOpenInputs) {
            ShortcutButton(text = stringResource(R.string.inputs), onClick = { AppRepository.openInputs(context) })
            Spacer(modifier = Modifier.width(12.dp))
        }
        ShortcutButton(text = stringResource(R.string.settings), onClick = { AppRepository.openSettings(context) })
    }
}

@Composable
private fun ShortcutButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
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

/** Big area that shows the app under focus. */
@Composable
private fun Hero(tiles: List<FavoriteTile>, focusedPackage: String?, modifier: Modifier) {
    val shownPackage = (tiles.firstOrNull { it.app.packageName == focusedPackage } ?: tiles.firstOrNull())
        ?.app?.packageName
    Crossfade(
        targetState = shownPackage,
        modifier = modifier,
        animationSpec = tween(durationMillis = 220),
        label = "hero",
    ) { pkg ->
        val tile = tiles.firstOrNull { it.app.packageName == pkg }
        if (tile != null) HeroContent(tile)
    }
}

@Composable
private fun HeroContent(tile: FavoriteTile) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            val banner = tile.art.banner
            val icon = tile.art.icon
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(modifier = Modifier.width(36.dp))
        Column {
            Text(
                text = tile.app.label,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.press_ok_to_open),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 18.sp,
            )
        }
    }
}

/** One row for up to 6 favorites, two rows for 7-12. Tiles are sized so a row never needs scrolling. */
@Composable
private fun FavoriteRows(
    tiles: List<FavoriteTile>,
    firstTileFocus: FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val rows = if (tiles.size <= 6) {
        listOf(tiles)
    } else {
        val half = (tiles.size + 1) / 2
        listOf(tiles.take(half), tiles.drop(half))
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val perRow = rows.maxOf { it.size }
        val available = maxWidth - ScreenPadding * 2
        val tileWidth = ((available - TileGap * (perRow - 1)) / perRow).coerceAtMost(MaxTileWidth)
        val tileHeight = tileWidth * 9f / 16f
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { rowIndex, row ->
                key(rowIndex) {
                    Row(
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(TileGap),
                    ) {
                        row.forEachIndexed { index, tile ->
                            key(tile.app.packageName) {
                                AppTile(
                                    tile = tile,
                                    width = tileWidth,
                                    height = tileHeight,
                                    modifier = if (rowIndex == 0 && index == 0) {
                                        Modifier.focusRequester(firstTileFocus)
                                    } else {
                                        Modifier
                                    },
                                    onFocused = { onFocused(tile.app.packageName) },
                                    onClick = { onOpen(tile.app.packageName) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A rounded app tile that grows a little and casts a soft shadow when focused. */
@Composable
private fun AppTile(
    tile: FavoriteTile,
    width: Dp,
    height: Dp,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width, height)
            .onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape = TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 18.dp),
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = TileShape),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF263241),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF34445A),
            focusedContentColor = Color.White,
        ),
    ) {
        TileContent(tile)
    }
}

@Composable
private fun TileContent(tile: FavoriteTile) {
    val banner = tile.art.banner
    if (banner != null) {
        Image(
            bitmap = banner,
            contentDescription = tile.app.label,
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
            val icon = tile.art.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(text = tile.app.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
