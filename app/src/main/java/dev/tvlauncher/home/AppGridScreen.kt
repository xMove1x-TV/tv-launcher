package dev.tvlauncher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

private const val GridColumns = 5

/**
 * A full-screen grid of apps, sorted by name. Used for "All apps" and for picking an app to add to
 * the favorites. Pictures load only for the tiles on screen.
 */
@Composable
fun AppGridScreen(
    title: String,
    hint: String,
    apps: List<LaunchableApp>,
    hidden: Set<String>,
    artCache: ArtCache,
    emptyText: String,
    focusTarget: String?,
    onFocusTargetHandled: () -> Unit,
    onClick: (LaunchableApp) -> Unit,
    onLongClick: ((LaunchableApp) -> Unit)?,
    headerAction: @Composable () -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(pkg: String): FocusRequester = requesters.getOrPut(pkg) { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenPadding, end = ScreenPadding, top = 32.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = hint, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
            }
            headerAction()
        }
        if (apps.isEmpty()) {
            Text(
                text = emptyText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 20.sp,
                modifier = Modifier.padding(ScreenPadding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GridColumns),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    GridItem(
                        app = app,
                        art = rememberArtwork(app, artCache),
                        isHidden = app.packageName in hidden,
                        requester = requesterFor(app.packageName),
                        onClick = { onClick(app) },
                        onLongClick = if (onLongClick != null) ({ onLongClick(app) }) else null,
                    )
                }
            }
        }
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (!initialFocusDone && apps.isNotEmpty()) {
            withFrameNanos { } // wait until the first tiles are laid out
            initialFocusDone = runCatching { requesterFor(apps.first().packageName).requestFocus() }.isSuccess
        }
    }

    // After an app disappears from the grid (for example it was hidden), focus its neighbor.
    LaunchedEffect(focusTarget, apps) {
        val target = focusTarget ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.packageName == target }
        if (index >= 0) {
            withFrameNanos { }
            if (runCatching { requesterFor(target).requestFocus() }.isFailure) {
                gridState.scrollToItem(index)
                withFrameNanos { }
                runCatching { requesterFor(target).requestFocus() }
            }
        }
        onFocusTargetHandled()
    }
}

@Composable
private fun GridItem(
    app: LaunchableApp,
    art: Artwork?,
    isHidden: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    var focused by remember { mutableStateOf(false) }
    val label = if (isHidden) app.label + " · " + stringResource(R.string.hidden_badge) else app.label
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (isHidden) 0.45f else 1f),
    ) {
        AppTile(
            app = app,
            art = art,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused || it.hasFocus },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (focused) 1f else 0.65f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
