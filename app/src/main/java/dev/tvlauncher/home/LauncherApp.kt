package dev.tvlauncher.home

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Screen { Home, AllApps, AddFavorite }

/** The options menu for one app: from edit mode on the home screen, or by holding OK in "All apps". */
private class MenuRequest(val app: LaunchableApp, val fromHome: Boolean)

private class Loaded(val apps: List<LaunchableApp>, val favorites: List<String>, val hidden: Set<String>)

@Composable
fun LauncherApp(refreshTick: Int) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2A3A), Color(0xFF0A0E15)))),
        ) {
            Launcher(refreshTick)
        }
    }
}

@Composable
private fun Launcher(refreshTick: Int) {
    val context = LocalContext.current
    val store = remember { LauncherStore(context.applicationContext) }
    val artCache = remember { ArtCache(context.applicationContext) }
    val canOpenInputs = remember { AppRepository.canOpenInputs(context) }

    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var hidden by remember { mutableStateOf<Set<String>>(emptySet()) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var editing by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var homeFocus by remember { mutableStateOf<String?>(null) }
    var gridFocus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTick) {
        val loaded = withContext(Dispatchers.IO) {
            val list = AppRepository.queryApps(context)
            val hiddenNow = store.loadHidden()
            val favs = store.resolveFavorites(list, hiddenNow)
            // Load the favorites' pictures before showing them, so the home screen does not pop in.
            val byPackage = list.associateBy { it.packageName }
            for (pkg in favs) byPackage[pkg]?.let { artCache.load(it) }
            Loaded(list, favs, hiddenNow)
        }
        apps = loaded.apps
        hidden = loaded.hidden
        if (moving == null) favorites = loaded.favorites
    }

    val allApps = apps.orEmpty()
    val byPackage = remember(allApps) { allApps.associateBy { it.packageName } }
    val favoriteApps = remember(favorites, byPackage) { favorites.mapNotNull { byPackage[it] } }
    val visibleApps = remember(allApps, hidden) { allApps.filter { it.packageName !in hidden } }

    fun saveFavorites(list: List<String>) {
        favorites = list
        store.saveFavorites(list)
    }

    fun dropMovingTile() {
        moving = null
        store.saveFavorites(favorites)
    }

    fun openHomeMenu(pkg: String) {
        val app = byPackage[pkg] ?: return
        menu = MenuRequest(app, fromHome = true)
    }

    BackHandler(enabled = true) {
        when {
            screen != Screen.Home -> {
                screen = Screen.Home
            }
            moving != null -> dropMovingTile()
            editing -> {
                editing = false
            }
            else -> Unit // Back does nothing on the home screen, like Apple TV.
        }
    }

    when (screen) {
        Screen.Home -> HomeScreen(
            favorites = favoriteApps,
            loaded = apps != null,
            artCache = artCache,
            editing = editing,
            moving = moving,
            canAddMore = favorites.size < LauncherStore.MAX,
            canOpenInputs = canOpenInputs,
            focusTarget = homeFocus,
            onFocusTargetHandled = { homeFocus = null },
            onTileClick = { pkg ->
                when {
                    moving != null -> dropMovingTile()
                    editing -> openHomeMenu(pkg)
                    else -> {
                        AppRepository.launch(context, pkg)
                    }
                }
            },
            onTileLongClick = { pkg ->
                when {
                    moving != null -> Unit
                    editing -> openHomeMenu(pkg)
                    else -> {
                        editing = true
                    }
                }
            },
            onMoveTo = { pkg, target ->
                val list = favorites.toMutableList()
                if (list.remove(pkg)) {
                    list.add(target.coerceIn(0, list.size), pkg)
                    favorites = list // saved when the tile is dropped
                }
                Log.d("TvLauncher", "moved $pkg to $target, now at ${favorites.indexOf(pkg)}")
            },
            onAllApps = { screen = Screen.AllApps },
            onAddApp = { screen = Screen.AddFavorite },
            onDone = {
                if (moving != null) dropMovingTile()
                editing = false
            },
        )
        Screen.AllApps -> AppGridScreen(
            title = stringResource(R.string.all_apps),
            hint = stringResource(R.string.all_apps_hint),
            apps = if (showHidden) allApps else visibleApps,
            hidden = hidden,
            artCache = artCache,
            emptyText = stringResource(R.string.no_apps),
            focusTarget = gridFocus,
            onFocusTargetHandled = { gridFocus = null },
            onClick = { app ->
                AppRepository.launch(context, app.packageName)
            },
            onLongClick = { app ->
                menu = MenuRequest(app, fromHome = false)
            },
            headerAction = {
                if (hidden.isNotEmpty()) {
                    ShortcutButton(
                        text = if (showHidden) {
                            stringResource(R.string.hide_hidden)
                        } else {
                            stringResource(R.string.show_hidden, hidden.size)
                        },
                        onClick = { showHidden = !showHidden },
                    )
                }
            },
        )
        Screen.AddFavorite -> AppGridScreen(
            title = stringResource(R.string.add_title),
            hint = stringResource(R.string.add_hint, favorites.size, LauncherStore.MAX),
            apps = visibleApps.filter { it.packageName !in favorites },
            hidden = emptySet(),
            artCache = artCache,
            emptyText = stringResource(R.string.no_apps_to_add),
            focusTarget = null,
            onFocusTargetHandled = {},
            onClick = { app ->
                if (favorites.size < LauncherStore.MAX && app.packageName !in favorites) {
                    saveFavorites(favorites + app.packageName)
                    homeFocus = app.packageName
                }
                screen = Screen.Home
            },
            onLongClick = null,
        )
    }

    val moveText = stringResource(R.string.move)
    val removeText = stringResource(R.string.remove_favorite)
    val addText = stringResource(R.string.add_favorite)
    val hideText = stringResource(R.string.hide_app)
    val unhideText = stringResource(R.string.unhide_app)
    val cancelText = stringResource(R.string.cancel)

    val request = menu
    if (request != null) {
        val pkg = request.app.packageName
        val options = ArrayList<MenuOption>()
        if (request.fromHome) {
            options += MenuOption(moveText) {
                menu = null
                moving = pkg
            }
            options += MenuOption(removeText) {
                val index = favorites.indexOf(pkg)
                val next = favorites - pkg
                saveFavorites(next)
                homeFocus = next.getOrNull(index) ?: next.lastOrNull()
                menu = null
            }
        } else {
            if (pkg in favorites) {
                options += MenuOption(removeText) {
                    saveFavorites(favorites - pkg)
                    menu = null
                }
            } else if (favorites.size < LauncherStore.MAX) {
                options += MenuOption(addText) {
                    saveFavorites(favorites + pkg)
                    menu = null
                }
            }
            if (pkg in hidden) {
                options += MenuOption(unhideText) {
                    val next = hidden - pkg
                    hidden = next
                    store.saveHidden(next)
                    menu = null
                }
            } else {
                options += MenuOption(hideText) {
                    val shown = if (showHidden) allApps else visibleApps
                    val index = shown.indexOfFirst { it.packageName == pkg }
                    val next = hidden + pkg
                    hidden = next
                    store.saveHidden(next)
                    // A hidden app also leaves the favorites.
                    if (pkg in favorites) saveFavorites(favorites - pkg)
                    if (!showHidden) {
                        val remaining = shown.filter { it.packageName != pkg }
                        gridFocus = (remaining.getOrNull(index) ?: remaining.lastOrNull())?.packageName
                    }
                    menu = null
                }
            }
        }
        options += MenuOption(cancelText) { menu = null }
        OptionsDialog(title = request.app.label, options = options, onDismiss = { menu = null })
    }
}
