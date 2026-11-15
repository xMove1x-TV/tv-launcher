package dev.tvlauncher.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    /** Bumped whenever the list of apps may have changed; the home screen reloads when it changes. */
    private val refreshTick = mutableIntStateOf(0)

    /** Bumped when the Home button (or the app icon) is pressed while the launcher already exists. */
    private val homeTick = mutableIntStateOf(0)

    // Registered only while the screen is visible: nothing runs in the background.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshTick.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LauncherApp(refreshTick = refreshTick.intValue, homeTick = homeTick.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        homeTick.intValue++
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }
        refreshTick.intValue++
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }
}
