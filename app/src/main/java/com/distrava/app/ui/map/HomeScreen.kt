package com.distrava.app.ui.map

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrava.app.DistravaApp
import com.distrava.app.R
import com.distrava.app.BuildConfig
import com.distrava.app.data.DiscardReason
import com.distrava.app.data.FinishResult
import com.distrava.app.data.db.ActivityEntity
import com.distrava.app.data.db.PoiEntity
import com.distrava.app.recording.TrackPoint
import com.distrava.app.recording.RecordingService
import com.distrava.app.recording.RecordingState
import com.distrava.app.recording.RecordingStateHolder
import com.distrava.app.ui.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenHistory: () -> Unit,
    onOpenMaps: () -> Unit,
    onOpenActivity: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as DistravaApp).container
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val recState by RecordingStateHolder.state.collectAsStateWithLifecycle()
    val regions by container.offlineMaps.regions.collectAsStateWithLifecycle()
    val darkOverride by container.darkTheme.collectAsStateWithLifecycle()
    val pois by container.poiDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val finished by container.activityRepository.observeFinished().collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by container.activityRepository.observeFavorites().collectAsStateWithLifecycle(initialValue = emptyList())
    var showHistory by remember { mutableStateOf(container.prefs.getBoolean(PREF_SHOW_HISTORY, false)) }
    var showPois by remember { mutableStateOf(container.prefs.getBoolean(PREF_SHOW_POIS, true)) }
    val historyRoutes by produceState(initialValue = emptyList<List<TrackPoint>>(), showHistory, finished.size) {
        value = if (showHistory) container.activityRepository.historyRoutes() else emptyList()
    }
    // Percurso salvo escolhido para sobrepor (id guardado entre execuções; -1 = nenhum).
    var overlayId by remember {
        mutableStateOf(container.prefs.getLong(PREF_OVERLAY_ROUTE, NO_ROUTE).takeIf { it != NO_ROUTE })
    }
    var showOverlayPicker by remember { mutableStateOf(false) }
    val overlayRoute by produceState(initialValue = emptyList<TrackPoint>(), overlayId) {
        value = overlayId?.let { container.activityRepository.overlayRoute(it) }.orEmpty()
    }
    val overlayActivity = finished.firstOrNull { it.id == overlayId }
    var pendingPoi by remember { mutableStateOf<LatLon?>(null) }
    var selectedPoiId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        if (!container.prefs.getBoolean(PREF_POI_HINT, false)) {
            container.prefs.edit().putBoolean(PREF_POI_HINT, true).apply()
            snackbar.showSnackbar(context.getString(R.string.poi_hint), withDismissAction = true)
        }
    }
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val styleJson = remember(regions, darkMap) { container.mapStyle.build(regions, dark = darkMap) }

    // Permissões e GPS são reavaliados sempre que a tela volta ao primeiro plano.
    var hasFine by remember { mutableStateOf(LocationAccess.hasFine(context)) }
    var hasCoarse by remember { mutableStateOf(LocationAccess.hasCoarse(context)) }
    var gpsOn by remember { mutableStateOf(LocationAccess.isGpsOn(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        hasFine = LocationAccess.hasFine(context)
        hasCoarse = LocationAccess.hasCoarse(context)
        gpsOn = LocationAccess.isGpsOn(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        askedOnce = true
        hasFine = LocationAccess.hasFine(context)
        hasCoarse = LocationAccess.hasCoarse(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (!hasFine) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    val recording = recState as? RecordingState.Recording
    val isRecording = recording != null
    val idleLocation by rememberCurrentLocation(enabled = hasFine && !isRecording && recState !is RecordingState.Finishing)
    val myLocation = recording?.points?.lastOrNull()?.let { LatLon(it.lat, it.lon) } ?: idleLocation
    val approximateCenter = remember(hasFine, hasCoarse) { LocationAccess.lastKnownAny(context) }
    // Indicador de satélites só enquanto não há posição (economiza CPU).
    val gnss by rememberGnssStatus(enabled = hasFine && myLocation == null)
    LaunchedEffect(hasFine) {
        if (hasFine) requestGpsAssistance(context.getSystemService(android.location.LocationManager::class.java))
    }

    var follow by remember { mutableStateOf(true) }
    var lastGesture by remember { mutableLongStateOf(0L) }
    var bottomPanelPx by remember { mutableIntStateOf(0) }
    // Durante a gravação, se o usuário mexeu no mapa, volta a centralizar após alguns segundos.
    LaunchedEffect(follow, lastGesture, isRecording) {
        if (!follow && isRecording) {
            delay(FOLLOW_RESUME_MS)
            follow = true
        }
    }
    var recenter by remember { mutableIntStateOf(0) }
    var confirmFinish by remember { mutableStateOf(false) }
    var showBattery by remember { mutableStateOf(false) }
    var orphan by remember { mutableStateOf<ActivityEntity?>(null) }

    // RF08: percurso interrompido sem serviço ativo.
    LaunchedEffect(Unit) {
        delay(800) // dá tempo ao serviço reiniciado pelo sistema de publicar o estado
        if (RecordingStateHolder.state.value == RecordingState.Idle) {
            orphan = container.activityRepository.findRecording()
        }
    }

    // Resultado da finalização.
    LaunchedEffect(recState) {
        when (val s = recState) {
            is RecordingState.Finished -> {
                RecordingStateHolder.acknowledge()
                onOpenActivity(s.activityId)
            }
            is RecordingState.Discarded -> {
                RecordingStateHolder.acknowledge()
                snackbar.showSnackbar(context.getString(discardMessage(s.reason)), withDismissAction = true)
            }
            else -> Unit
        }
    }

    fun startRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !LocationAccess.granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        follow = true
        RecordingService.start(context)
    }

    fun onStartClicked() {
        val pm = context.getSystemService(PowerManager::class.java)
        val prompted = container.prefs.getBoolean(PREF_BATTERY_PROMPTED, false)
        if (!prompted && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
            showBattery = true
        } else {
            startRecording()
        }
    }

    Box(Modifier.fillMaxSize()) {
        DistravaMap(
            styleJson = styleJson,
            modifier = Modifier.fillMaxSize(),
            route = recording?.points.orEmpty(),
            myLocation = myLocation,
            approximateCenter = approximateCenter,
            follow = isRecording && follow,
            recenterSignal = recenter,
            bottomPaddingPx = bottomPanelPx,
            historyRoutes = historyRoutes,
            overlayRoute = overlayRoute,
            pois = if (showPois) pois else emptyList(),
            onLongPress = { pendingPoi = it },
            onPoiClick = { selectedPoiId = it },
            onUserGesture = {
                follow = false
                lastGesture = System.currentTimeMillis()
            },
        )

        // Topo: atalhos.
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MapChip(text = stringResource(R.string.history), enabled = !isRecording, onClick = onOpenHistory) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
            }
            MapChip(text = stringResource(R.string.offline_maps), enabled = !isRecording, onClick = onOpenMaps) {
                Icon(Icons.Default.Place, contentDescription = null)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onGloballyPositioned { coords ->
                    // Altura do painel medida da base da tela.
                    bottomPanelPx = (coords.parentLayoutCoordinates?.size?.height ?: 0) -
                        coords.positionInParent().y.toInt()
                }
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            SmallFloatingActionButton(
                onClick = { showOverlayPicker = true },
                containerColor = if (overlayId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (overlayId != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(Icons.Default.Layers, contentDescription = stringResource(R.string.overlay_action))
            }
            Spacer(Modifier.height(12.dp))

            SmallFloatingActionButton(
                onClick = {
                    showPois = !showPois
                    container.prefs.edit().putBoolean(PREF_SHOW_POIS, showPois).apply()
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Icon(
                    if (showPois) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = stringResource(if (showPois) R.string.poi_hide else R.string.poi_show),
                )
            }
            Spacer(Modifier.height(12.dp))

            SmallFloatingActionButton(
                onClick = {
                    showHistory = !showHistory
                    container.prefs.edit().putBoolean(PREF_SHOW_HISTORY, showHistory).apply()
                },
                containerColor = if (showHistory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = if (showHistory) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = stringResource(if (showHistory) R.string.history_routes_hide else R.string.history_routes_show),
                )
            }
            Spacer(Modifier.height(12.dp))

            val darkNow = darkOverride ?: isSystemInDarkTheme()
            SmallFloatingActionButton(
                onClick = { container.setDarkTheme(!darkNow) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Icon(
                    if (darkNow) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = stringResource(if (darkNow) R.string.theme_light else R.string.theme_dark),
                )
            }
            Spacer(Modifier.height(12.dp))

            if (myLocation != null) {
                SmallFloatingActionButton(
                    onClick = {
                        follow = true
                        recenter++
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.recenter))
                }
                Spacer(Modifier.height(12.dp))
            }

            if (overlayActivity != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AssistChip(
                        onClick = { showOverlayPicker = true },
                        label = { Text(stringResource(R.string.overlay_showing, routeLabel(overlayActivity))) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            when {
                recording != null -> RecordingPanel(recording, gnss, onFinish = { confirmFinish = true })
                recState is RecordingState.Finishing -> FinishingPanel()
                else -> IdlePanel(
                    hasMap = regions.isNotEmpty(),
                    hasFine = hasFine,
                    approximateOnly = hasCoarse && !hasFine,
                    deniedForever = askedOnce && !hasFine && !(context as Activity)
                        .shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION),
                    gpsOn = gpsOn,
                    hasFix = myLocation != null,
                    gnss = gnss,
                    onSimulate = if (BuildConfig.DEBUG) {
                        { RecordingService.start(context, simulate = true) }
                    } else {
                        null
                    },
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    onTurnOnGps = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onOpenMaps = onOpenMaps,
                    onStart = ::onStartClicked,
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp))
    }

    pendingPoi?.let { at ->
        AddPoiDialog(
            onDismiss = { pendingPoi = null },
            onSave = { name, kind ->
                pendingPoi = null
                scope.launch {
                    container.poiDao.insert(
                        PoiEntity(lat = at.lat, lon = at.lon, name = name, kind = kind.key, createdAt = System.currentTimeMillis()),
                    )
                }
            },
        )
    }
    pois.firstOrNull { it.id == selectedPoiId }?.let { poi ->
        PoiDetailDialog(
            poi = poi,
            onDismiss = { selectedPoiId = null },
            onDelete = {
                selectedPoiId = null
                scope.launch { container.poiDao.delete(poi.id) }
            },
        )
    }

    if (showOverlayPicker) {
        PickOverlayRouteDialog(
            routes = favorites,
            selectedId = overlayId,
            onDismiss = { showOverlayPicker = false },
            onPick = { id ->
                showOverlayPicker = false
                overlayId = id
                container.prefs.edit().putLong(PREF_OVERLAY_ROUTE, id ?: NO_ROUTE).apply()
            },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_confirm_title)) },
            text = { Text(stringResource(R.string.finish_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    RecordingService.stop(context)
                }) { Text(stringResource(R.string.finish_ride)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.keep_recording)) }
            },
        )
    }

    if (showBattery) {
        AlertDialog(
            onDismissRequest = { showBattery = false },
            title = { Text(stringResource(R.string.battery_title)) },
            text = { Text(stringResource(R.string.battery_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showBattery = false
                    container.prefs.edit().putBoolean(PREF_BATTERY_PROMPTED, true).apply()
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }) { Text(stringResource(R.string.battery_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBattery = false
                    container.prefs.edit().putBoolean(PREF_BATTERY_PROMPTED, true).apply()
                    startRecording()
                }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }

    orphan?.let { activity ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.recover_title)) },
            text = { Text(stringResource(R.string.recover_text)) },
            confirmButton = {
                TextButton(onClick = {
                    orphan = null
                    RecordingService.resume(context, activity.id)
                }) { Text(stringResource(R.string.recover_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    orphan = null
                    scope.launch {
                        val repo = container.activityRepository
                        val lastTime = repo.points(activity.id).lastOrNull()?.time ?: activity.startedAt
                        when (val r = repo.finish(activity.id, lastTime)) {
                            is FinishResult.Saved -> onOpenActivity(r.activityId)
                            is FinishResult.Discarded -> snackbar.showSnackbar(
                                context.getString(discardMessage(r.reason)),
                                withDismissAction = true,
                            )
                        }
                    }
                }) { Text(stringResource(R.string.recover_finish)) }
            },
        )
    }
}

private const val PREF_BATTERY_PROMPTED = "battery_prompted"
private const val FOLLOW_RESUME_MS = 8_000L
private const val PREF_SHOW_HISTORY = "show_history_routes"
private const val PREF_SHOW_POIS = "show_pois"
private const val PREF_POI_HINT = "poi_hint_shown"
private const val PREF_OVERLAY_ROUTE = "overlay_route_id"
private const val NO_ROUTE = -1L

private fun discardMessage(reason: DiscardReason) = when (reason) {
    DiscardReason.NO_GPS -> R.string.ride_discarded_no_gps
    DiscardReason.TOO_SHORT -> R.string.ride_discarded
}

@Composable
private fun MapChip(text: String, enabled: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
        icon = icon,
        text = { Text(text) },
        modifier = Modifier.height(48.dp),
    )
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun BigButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color? = null,
    contentColor: Color? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = if (color != null) {
            ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor ?: MaterialTheme.colorScheme.onPrimary)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IdlePanel(
    hasMap: Boolean,
    hasFine: Boolean,
    approximateOnly: Boolean,
    deniedForever: Boolean,
    gpsOn: Boolean,
    hasFix: Boolean,
    gnss: GnssInfo?,
    onSimulate: (() -> Unit)?,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTurnOnGps: () -> Unit,
    onOpenMaps: () -> Unit,
    onStart: () -> Unit,
) {
    val blocker: (@Composable () -> Unit)? = when {
        !hasFine -> {
            {
                Text(
                    stringResource(
                        when {
                            approximateOnly -> R.string.approximate_only
                            deniedForever -> R.string.permission_denied_forever
                            else -> R.string.need_permission
                        },
                    ),
                )
                Spacer(Modifier.height(12.dp))
                if (deniedForever) {
                    BigButton(stringResource(R.string.open_settings), onOpenAppSettings)
                } else {
                    BigButton(stringResource(R.string.grant_permission), onRequestPermission)
                }
            }
        }
        !gpsOn -> {
            {
                Text(stringResource(R.string.gps_off))
                Spacer(Modifier.height(12.dp))
                BigButton(stringResource(R.string.turn_on_gps), onTurnOnGps)
            }
        }
        else -> null
    }

    if (!hasMap) {
        PanelCard {
            Text(stringResource(R.string.no_map_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.no_map_text), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenMaps) { Text(stringResource(R.string.no_map_action)) }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (blocker != null) {
        PanelCard { blocker() }
    } else {
        if (!hasFix) {
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.waiting_gps_home), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                GnssLine(gnss)
            }
            Spacer(Modifier.height(12.dp))
        }
        BigButton(stringResource(R.string.start_ride), onStart)
    }
    if (onSimulate != null) {
        TextButton(onClick = onSimulate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.simulate_ride))
        }
    }
}

@Composable
private fun GnssLine(gnss: GnssInfo?) {
    Text(
        text = if (gnss == null) {
            stringResource(R.string.gnss_none)
        } else {
            stringResource(R.string.gnss_status, gnss.withSignal, gnss.usedInFix)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RecordingPanel(state: RecordingState.Recording, gnss: GnssInfo?, onFinish: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    PanelCard {
        if (state.points.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.waiting_gps), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            GnssLine(gnss)
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(stringResource(R.string.label_time), Format.duration(now - state.startedAt))
            Metric(stringResource(R.string.label_distance), Format.distance(state.distanceMeters))
            Metric(stringResource(R.string.label_speed), Format.speed(state.currentSpeedMps))
        }
        Spacer(Modifier.height(16.dp))
        BigButton(
            stringResource(R.string.finish_ride),
            onFinish,
            color = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun FinishingPanel() {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.size(12.dp))
            Text(stringResource(R.string.finishing))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
