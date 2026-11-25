package com.distrava.app.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.distrava.app.DistravaApp
import com.distrava.app.data.ActivityRepository
import com.distrava.app.data.db.PoiDao
import com.distrava.app.data.db.PoiEntity
import com.distrava.app.share.QrPayload
import com.distrava.app.share.SharedItem
import com.distrava.app.ui.share.ShareQrDialog
import kotlinx.coroutines.launch
import com.distrava.app.R
import com.distrava.app.data.db.ActivityEntity
import com.distrava.app.share.RouteProjection
import com.distrava.app.ui.Format
import com.distrava.app.ui.theme.DistravaOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    val container = (LocalContext.current.applicationContext as DistravaApp).container
    val activities by container.activityRepository.observeFinished().collectAsStateWithLifecycle(initialValue = null)
    val favorites by container.activityRepository.observeFavorites().collectAsStateWithLifecycle(initialValue = null)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val pois by container.poiDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var poiQr by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        scope.launch {
            snackbar.showSnackbar(importScanned(text, container.activityRepository, container.poiDao, pois, context))
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt(context.getString(R.string.receive_prompt))
                                .setBeepEnabled(false)
                                .setOrientationLocked(true),
                        )
                    }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.receive_qr))
                    }
                    IconButton(onClick = {
                        if (pois.isEmpty()) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.no_pois_to_share)) }
                        } else {
                            val text = QrPayload.encode(SharedItem.Pois(pois))
                            if (text == null) {
                                scope.launch { snackbar.showSnackbar(context.getString(R.string.share_pois_too_long)) }
                            } else {
                                poiQr = text
                            }
                        }
                    }) {
                        Icon(Icons.Default.Place, contentDescription = stringResource(R.string.share_pois_qr))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.history_tab_all)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.history_tab_favorites)) })
            }
            val isFavorites = tab == 1
            val list = if (isFavorites) favorites else activities
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (isFavorites) R.string.history_favorites_empty else R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { a -> HistoryItem(a, onClick = { onOpenActivity(a.id) }) }
                }
            }
        }
    }

    poiQr?.let { text ->
        ShareQrDialog(
            title = stringResource(R.string.share_pois_qr),
            hint = stringResource(R.string.share_hint),
            text = text,
            onDismiss = { poiQr = null },
        )
    }
}

/** Importa o que foi escaneado e devolve a mensagem para o usuário. */
private suspend fun importScanned(
    text: String,
    repo: ActivityRepository,
    poiDao: PoiDao,
    existingPois: List<PoiEntity>,
    context: Context,
): String = when (val item = QrPayload.decode(text)) {
    is SharedItem.Route -> {
        val label = item.name.ifEmpty { Format.dateTime(item.startedAtSec * 1000) }
        if (repo.importFavorite(item.name, item.startedAtSec, item.points) != null) {
            context.getString(R.string.imported_route, label)
        } else {
            context.getString(R.string.imported_route_failed)
        }
    }
    is SharedItem.Pois -> {
        var added = 0
        item.pois.forEach { p ->
            val duplicate = existingPois.any { it.name == p.name && it.lat == p.lat && it.lon == p.lon }
            if (!duplicate) {
                poiDao.insert(p)
                added++
            }
        }
        context.getString(R.string.imported_pois, added)
    }
    null -> context.getString(R.string.invalid_qr)
}

@Composable
private fun HistoryItem(activity: ActivityEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RouteThumbnail(activity.id, Modifier.size(84.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        activity.name ?: Format.dateTime(activity.startedAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (activity.favorite) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = DistravaOrange,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
                Text(Format.distance(activity.distanceMeters), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${Format.duration(activity.movingTimeMs)} · ${Format.speed(activity.avgSpeedMps)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Miniatura com o mapa atrás da rota (D20); sem mapa instalado, só a rota (RF06). */
@Composable
private fun RouteThumbnail(activityId: Long, modifier: Modifier) {
    val repo = (LocalContext.current.applicationContext as DistravaApp).container.activityRepository
    val density = LocalDensity.current.density
    val sizePx = with(LocalDensity.current) { 84.dp.roundToPx() }
    val mapThumb by produceState<ImageBitmap?>(initialValue = null, activityId) {
        value = repo.thumbnail(activityId, sizePx, density)?.asImageBitmap()
    }
    mapThumb?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(14.dp)),
        )
        return
    }

    val latLons by produceState(initialValue = emptyList<Pair<Double, Double>>(), activityId) {
        value = RouteProjection.downsample(repo.points(activityId), 300).map { it.lat to it.lon }
    }
    Canvas(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (latLons.size < 2) return@Canvas
        val pad = 10.dp.toPx()
        val px = RouteProjection.fit(latLons, pad, pad, size.width - 2 * pad, size.height - 2 * pad)
        val path = Path().apply {
            moveTo(px[0].x, px[0].y)
            for (i in 1 until px.size) lineTo(px[i].x, px[i].y)
        }
        drawPath(path, DistravaOrange, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
