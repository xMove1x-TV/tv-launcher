package dev.tvlauncher.home

import android.view.KeyEvent as NativeKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Text

private val TileGap = 24.dp
private val MaxTileWidth = 196.dp

// Keeps the app name readable over bright backgrounds.
private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 8f)

/**
 * The home screen: clock, a big picture of the focused app, and the favorites (one row for up to 6,
 * two rows for 7-12). In edit mode the tiles jiggle; a tile being moved follows the arrow keys.
 */
@Composable
fun HomeScreen(
    favorites: List<LaunchableApp>,
    loaded: Boolean,
    artCache: ArtCache,
    editing: Boolean,
    moving: String?,
    canAddMore: Boolean,
    canOpenInputs: Boolean,
    focusTarget: String?,
    onFocusTargetHandled: () -> Unit,
    onTileClick: (String) -> Unit,
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
        TopBar(
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
                FavoriteGrid(
                    favorites = favorites,
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
        Spacer(modifier = Modifier.height(32.dp))
    }

    LaunchedEffect(loaded, favorites.isNotEmpty()) {
        if (!initialFocusDone && favorites.isNotEmpty()) {
            withFrameNanos { } // wait until the tiles are laid out
            val target = focusTarget?.takeIf { t -> favorites.any { it.packageName == t } }
                ?: favorites.first().packageName
            initialFocusDone = runCatching { requesterFor(target).requestFocus() }.isSuccess
            if (focusTarget != null) onFocusTargetHandled()
        }
    }

    // Focus a given tile, for example the neighbor of a tile that was just removed.
    LaunchedEffect(focusTarget) {
        if (initialFocusDone && focusTarget != null) {
            withFrameNanos { }
            runCatching { requesterFor(focusTarget).requestFocus() }
            onFocusTargetHandled()
        }
    }

    // Keep the focus on the tile being moved, also when it changes row.
    LaunchedEffect(moving, favorites) {
        if (moving != null) {
            withFrameNanos { }
            runCatching { requesterFor(moving).requestFocus() }
        }
    }
}

@Composable
private fun TopBar(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Clock()
        Spacer(modifier = Modifier.weight(1f))
        if (editing) {
            if (showAdd) {
                ShortcutButton(text = stringResource(R.string.add_app), onClick = onAddApp)
                Spacer(modifier = Modifier.width(12.dp))
            }
            if (showBackground) {
                ShortcutButton(text = stringResource(R.string.background), onClick = onBackground)
                Spacer(modifier = Modifier.width(12.dp))
            }
            ShortcutButton(text = stringResource(R.string.done), onClick = onDone)
        } else {
            ShortcutButton(text = stringResource(R.string.all_apps), onClick = onAllApps)
            Spacer(modifier = Modifier.width(12.dp))
            if (canOpenInputs) {
                ShortcutButton(text = stringResource(R.string.inputs), onClick = { AppRepository.openInputs(context) })
                Spacer(modifier = Modifier.width(12.dp))
            }
            ShortcutButton(text = stringResource(R.string.settings), onClick = { AppRepository.openSettings(context) })
        }
    }
}

/** Big area that shows the app under focus. */
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
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
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
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(96.dp))
            }
        }
        Spacer(modifier = Modifier.width(36.dp))
        Column {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.copy(shadow = TextShadow),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
                style = LocalTextStyle.current.copy(shadow = TextShadow),
            )
        }
    }
}

/**
 * The favorite tiles in one or two plain rows, sized so a row never needs scrolling.
 * (FlowRow was tried first, but on the TV it did not redraw the tiles in their new order while moving.)
 */
@Composable
private fun FavoriteGrid(
    favorites: List<LaunchableApp>,
    artCache: ArtCache,
    editing: Boolean,
    moving: String?,
    requesterFor: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onTileClick: (String) -> Unit,
    onTileLongClick: (String) -> Unit,
    onMoveTo: (String, Int) -> Unit,
) {
    val count = favorites.size
    val perRow = if (count <= 6) count else (count + 1) / 2
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth - ScreenPadding * 2
        val tileWidth = ((available - TileGap * (perRow - 1)) / perRow).coerceAtMost(MaxTileWidth)
        val tileHeight = tileWidth * 9f / 16f
        Column(
            modifier = Modifier
                .padding(horizontal = ScreenPadding, vertical = 12.dp)
                // While a tile is being moved, the arrows move it instead of the focus.
                .onPreviewKeyEvent { event ->
                    val movingPkg = currentMoving ?: return@onPreviewKeyEvent false
                    val list = currentFavorites
                    val index = list.indexOfFirst { it.packageName == movingPkg }
                    if (index < 0) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    val target = moveTarget(native.keyCode, index, list.size, perRow, rtl)
                        ?: return@onPreviewKeyEvent false
                    if (native.action == NativeKeyEvent.ACTION_DOWN && target != index) {
                        currentOnMoveTo(movingPkg, target)
                    }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            favorites.chunked(perRow).forEachIndexed { rowIndex, row ->
                key(rowIndex) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        row.forEachIndexed { column, app ->
                            key(app.packageName) {
                                val pkg = app.packageName
                                val isMoving = pkg == moving
                                val direction = if ((rowIndex * perRow + column) % 2 == 0) 1f else -1f
                                AppTile(
                                    app = app,
                                    art = rememberArtwork(app, artCache),
                                    onClick = { onTileClick(pkg) },
                                    onLongClick = { onTileLongClick(pkg) },
                                    highlight = isMoving,
                                    onFocused = { onFocused(pkg) },
                                    modifier = Modifier
                                        .size(tileWidth, tileHeight)
                                        .focusRequester(requesterFor(pkg))
                                        .graphicsLayer {
                                            rotationZ = if (jiggle != null && !isMoving) jiggle.value * direction else 0f
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
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
