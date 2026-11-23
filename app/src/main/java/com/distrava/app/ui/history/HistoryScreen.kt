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
import com.distrava.app.DistravaApp
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val list = activities
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { a -> HistoryItem(a, onClick = { onOpenActivity(a.id) }) }
            }
        }
    }
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
                Text(Format.dateTime(activity.startedAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
