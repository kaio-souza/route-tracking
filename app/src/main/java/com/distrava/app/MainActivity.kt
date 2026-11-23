package com.distrava.app

import android.content.Intent
import android.os.Bundle
import com.distrava.app.recording.RecordingService
import com.distrava.app.recording.RecordingState
import com.distrava.app.recording.RecordingStateHolder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.distrava.app.ui.history.ActivityDetailScreen
import com.distrava.app.ui.history.HistoryScreen
import com.distrava.app.ui.map.HomeScreen
import com.distrava.app.ui.offlinemaps.OfflineMapsScreen
import com.distrava.app.ui.theme.DistravaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleDebugIntent(intent)
        val container = (application as DistravaApp).container
        setContent {
            val override by container.darkTheme.collectAsStateWithLifecycle()
            val dark = override ?: isSystemInDarkTheme()
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                )
                onDispose { }
            }
            DistravaTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DistravaNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    /** Só debug: `adb shell am start -n com.distrava.app/.MainActivity --ez simulate_ride true`. */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent?.getBooleanExtra("simulate_ride", false) != true) return
        intent.removeExtra("simulate_ride")
        if (RecordingStateHolder.state.value == RecordingState.Idle) RecordingService.start(this, simulate = true)
    }
}

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val MAPS = "maps"
    const val ACTIVITY = "activity/{id}"
    fun activity(id: Long) = "activity/$id"
}

@androidx.compose.runtime.Composable
private fun DistravaNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenMaps = { nav.navigate(Routes.MAPS) },
                onOpenActivity = { id -> nav.navigate(Routes.activity(id)) { launchSingleTop = true } },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenActivity = { id -> nav.navigate(Routes.activity(id)) },
            )
        }
        composable(Routes.MAPS) {
            OfflineMapsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.ACTIVITY, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ActivityDetailScreen(
                activityId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
