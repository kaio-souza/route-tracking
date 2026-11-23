package com.distrava.app

import android.app.Application
import android.content.Context
import com.distrava.app.data.ActivityRepository
import com.distrava.app.data.db.AppDatabase
import com.distrava.app.maps.MapSnapshots
import com.distrava.app.maps.MapStyle
import com.distrava.app.maps.OfflineMapStore
import com.distrava.app.share.SummaryImageRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import java.io.File

/** Dependências do app, sem framework de injeção (D14). */
class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)

    val offlineMaps = OfflineMapStore(context)
    val mapStyle = MapStyle(context)
    val mapSnapshots = MapSnapshots(context, offlineMaps, mapStyle)
    val activityRepository = ActivityRepository(
        dao = database.activityDao(),
        imageRenderer = SummaryImageRenderer(mapSnapshots),
        snapshots = mapSnapshots,
        imagesDir = File(context.filesDir, "images"),
    )
    val prefs = context.getSharedPreferences("distrava", Context.MODE_PRIVATE)

    /** null = seguir o sistema; true/false = escolha do usuário. */
    private val _darkTheme = MutableStateFlow<Boolean?>(
        if (prefs.contains(PREF_DARK_THEME)) prefs.getBoolean(PREF_DARK_THEME, false) else null,
    )
    val darkTheme: StateFlow<Boolean?> = _darkTheme.asStateFlow()

    fun setDarkTheme(value: Boolean?) {
        _darkTheme.value = value
        prefs.edit().apply {
            if (value == null) remove(PREF_DARK_THEME) else putBoolean(PREF_DARK_THEME, value)
        }.apply()
    }

    companion object {
        private const val PREF_DARK_THEME = "dark_theme"
    }
}

class DistravaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
        appScope.launch { container.offlineMaps.installBundled() }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
