package dev.tvlauncher.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text

private val TileWidth = 200.dp
private val TileHeight = 113.dp
private val TileGap = 26.dp
private val HeroShape = RoundedCornerShape(22.dp)

// Keeps the app name readable over bright backgrounds.
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 10f)

/** Where a tile is on screen, kept without causing redraws, for the "grow from the tile" open animation. */
private class BoundsHolder {
    var rect: Rect? = null
}

/**
 * The home screen: the top area (clock, greeting, dates, weather, buttons), a big picture of the
 * focused app, and one row of large favorite tiles. In edit mode the tiles jiggle; a tile being
 * moved follows the arrow keys.
 */
@Composable
fun HomeScreen(
    favorites: List<LaunchableApp>,
    loaded: Boolean,
    artCache: ArtCache,
    now: Long,
    weather: WeatherNow?,
    night: Boolean,
    editing: Boolean,
    moving: String?,
    canAddMore: Boolean,
    canOpenInputs: Boolean,
    focusTarget: String?,
    onFocusTargetHandled: () -> Unit,
    onTileClick: (String, Rect?) -> Unit,
    onTileLongClick: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
    onAllApps: () -> Unit,
    onAddApp: () -> Unit,
    onBackground: () -> Unit,
    onDone: () -> Unit,
    onHeroApp: (LaunchableApp) -> Unit,
) {
    var focusedPackage by remember { mutableStateOf<String?>(null) }
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(pkg: String): FocusRequester = requesters.getOrPut(pkg) { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Tiles off screen are not composed, so scroll to a tile before focusing it.
    suspend fun focusTile(pkg: String): Boolean {
        val index = favorites.indexOfFirst { it.packageName == pkg }
        if (index < 0) return false
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
            withFrameNanos { }
        }
        return runCatching { requesterFor(pkg).requestFocus() }.isSuccess
    }

    val hint = stringResource(
        when {
            moving != null -> R.string.move_hint
            editing -> R.string.edit_hint
            else -> R.string.press_ok_to_open
        },
    )

    val heroApp = favorites.firstOrNull { it.packageName == (moving ?: focusedPackage) } ?: favorites.firstOrNull()
    // Lets the "from the app" background follow the app in the big area.
    LaunchedEffect(heroApp?.packageName) {
        if (heroApp != null) onHeroApp(heroApp)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopArea(
            now = now,
            weather = weather,
            night = night,
            editing = editing,
            showAdd = canAddMore && moving == null,
            showBackground = moving == null,
            canOpenInputs = canOpenInputs,
            onAllApps = onAllApps,
            onAddApp = onAddApp,
            onBackground = onBackground,
            onDone = onDone,
        )
        Hero(
            favorites = favorites,
            shownPackage = heroApp?.packageName,
            hint = hint,
            artCache = artCache,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        when {
            !loaded -> Unit
            favorites.isEmpty() -> Text(
                text = stringResource(R.string.no_favorites),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 20.sp,
                modifier = Modifier.padding(ScreenPadding),
            )
            else -> Appear(durationMillis = 320, fromScale = 0.98f) {
                FavoriteRow(
                    favorites = favorites,
                    listState = listState,
                    artCache = artCache,
                    editing = editing,
                    moving = moving,
                    requesterFor = { requesterFor(it) },
                    onFocused = { focusedPackage = it },
                    onTileClick = onTileClick,
                    onTileLongClick = onTileLongClick,
                    onMoveTo = onMoveTo,
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    LaunchedEffect(loaded, favorites.isNotEmpty()) {
        if (!initialFocusDone && favorites.isNotEmpty()) {
            withFrameNanos { } // wait until the tiles are laid out
            val target = focusTarget?.takeIf { t -> favorites.any { it.packageName == t } }
                ?: favorites.first().packageName
            initialFocusDone = focusTile(target)
            if (focusTarget != null) onFocusTargetHandled()
        }
    }

    // Focus a given tile, for example the neighbor of a tile that was just removed.
    LaunchedEffect(focusTarget) {
        if (initialFocusDone && focusTarget != null) {
            withFrameNanos { }
            focusTile(focusTarget)
            onFocusTargetHandled()
        }
    }

    // Keep the tile being moved focused and on screen.
    LaunchedEffect(moving, favorites) {
        if (moving != null) {
            withFrameNanos { }
            focusTile(moving)
        }
    }
}

/** The big area that shows the focused app, with a dark gradient over its picture. */
@Composable
private fun Hero(
    favorites: List<LaunchableApp>,
    shownPackage: String?,
    hint: String,
    artCache: ArtCache,
    modifier: Modifier,
) {
    Crossfade(
        targetState = shownPackage,
        modifier = modifier,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "hero",
    ) { pkg ->
        val app = favorites.firstOrNull { it.packageName == pkg }
        if (app != null) HeroContent(app = app, art = rememberArtwork(app, artCache), hint = hint)
    }
}

@Composable
private fun HeroContent(app: LaunchableApp, art: Artwork?, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .shadow(elevation = 18.dp, shape = HeroShape)
                .clip(HeroShape)
                .background(Color(0xFF1B2430)),
            contentAlignment = Alignment.Center,
        ) {
            val banner = art?.banner
            val icon = art?.icon
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(110.dp))
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f),
                        ),
                    ),
            )
        }
        Spacer(modifier = Modifier.width(36.dp))
        Column {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = TextShadow),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 18.sp,
                style = LocalTextStyle.current.copy(shadow = TextShadow),
            )
        }
    }
}

/**
 * One row of large favorite tiles that scrolls sideways. The name shows only under the focused tile.
 */
@Composable
private fun FavoriteRow(
    favorites: List<LaunchableApp>,
    listState: LazyListState,
    artCache: ArtCache,
    editing: Boolean,
    moving: String?,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onTileClick: (String, Rect?) -> Unit,
    onTileLongClick: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // One shared animation drives the jiggle of every tile; it only redraws, it does not recompose.
    val jiggle: State<Float>? = if (editing) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -1.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 140, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "jiggleAngle",
        )
    } else {
        null
    }
    // Read at key-press time, so the move always uses the current order and the current moving tile.
    val currentFavorites by rememberUpdatedState(favorites)
    val currentMoving by rememberUpdatedState(moving)
    val currentOnMoveTo by rememberUpdatedState(onMoveTo)

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            // While a tile is being moved, the arrows move it instead of the focus.
            .onPreviewKeyEvent { event ->
                val movingPkg = currentMoving ?: return@onPreviewKeyEvent false
                val list = currentFavorites
                val index = list.indexOfFirst { it.packageName == movingPkg }
                if (index < 0) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val target = moveTarget(native.keyCode, index, list.size, list.size, rtl)
                    ?: return@onPreviewKeyEvent false
                if (native.action == NativeKeyEvent.ACTION_DOWN && target != index) {
                    currentOnMoveTo(movingPkg, target)
                }
                true
            },
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        itemsIndexed(favorites, key = { _, app -> app.packageName }) { index, app ->
            FavoriteItem(
                app = app,
                index = index,
                art = rememberArtwork(app, artCache),
                isMoving = app.packageName == moving,
                jiggle = jiggle,
                requester = requesterFor(app.packageName),
                onFocused = { onFocused(app.packageName) },
                onClick = { bounds -> onTileClick(app.packageName, bounds) },
                onLongClick = { onTileLongClick(app.packageName) },
            )
        }
    }
}

@Composable
private fun FavoriteItem(
    app: LaunchableApp,
    index: Int,
    art: Artwork?,
    isMoving: Boolean,
    jiggle: State<Float>?,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bounds = remember { BoundsHolder() }
    val nameAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "name",
    )
    val direction = if (index % 2 == 0) 1f else -1f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(TileWidth)) {
        AppTile(
            app = app,
            art = art,
            onClick = { onClick(bounds.rect) },
            onLongClick = onLongClick,
            highlight = isMoving,
            onFocused = onFocused,
            modifier = Modifier
                .size(TileWidth, TileHeight)
                .focusRequester(requester)
                .onGloballyPositioned { bounds.rect = it.boundsInWindow() }
                .onFocusChanged { focused = it.isFocused || it.hasFocus }
                .graphicsLayer {
                    rotationZ = if (jiggle != null && !isMoving) jiggle.value * direction else 0f
                },
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = LocalTextStyle.current.copy(shadow = TextShadow),
            modifier = Modifier.graphicsLayer { alpha = nameAlpha },
        )
    }
}

/**
 * Where the tile at [index] should go for an arrow key while it is being moved, or null when the key
 * is not an arrow. In Arabic (right to left) the left arrow moves the tile forward.
 */
private fun moveTarget(keyCode: Int, index: Int, count: Int, perRow: Int, rtl: Boolean): Int? {
    val row = index / perRow
    val column = index % perRow
    val rows = (count + perRow - 1) / perRow
    val forward = if (rtl) NativeKeyEvent.KEYCODE_DPAD_LEFT else NativeKeyEvent.KEYCODE_DPAD_RIGHT
    val backward = if (rtl) NativeKeyEvent.KEYCODE_DPAD_RIGHT else NativeKeyEvent.KEYCODE_DPAD_LEFT
    val target = when (keyCode) {
        forward -> index + 1
        backward -> index - 1
        NativeKeyEvent.KEYCODE_DPAD_DOWN -> if (row + 1 < rows) (row + 1) * perRow + column else index
        NativeKeyEvent.KEYCODE_DPAD_UP -> if (row > 0) (row - 1) * perRow + column else index
        else -> return null
    }
    return target.coerceIn(0, count - 1)
}
