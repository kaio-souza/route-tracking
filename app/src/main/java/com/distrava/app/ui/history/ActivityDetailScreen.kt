package com.distrava.app.ui.history

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distrava.app.DistravaApp
import com.distrava.app.R
import com.distrava.app.recording.TrackPoint
import com.distrava.app.share.ImageExporter
import com.distrava.app.ui.Format
import com.distrava.app.ui.map.DistravaMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Resumo logo após finalizar e detalhe a partir do histórico (RF04, RF05, RF06). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(activityId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as DistravaApp).container
    val repo = container.activityRepository
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val activity by repo.observe(activityId).collectAsStateWithLifecycle(initialValue = null)
    val regions by container.offlineMaps.regions.collectAsStateWithLifecycle()
    val styleJson = remember(regions) { container.mapStyle.build(regions) }

    val points by produceState(initialValue = emptyList<TrackPoint>(), activityId) {
        value = repo.points(activityId)
    }
    var imageFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(activity?.id) {
        val a = activity ?: return@LaunchedEffect
        imageFile = repo.ensureImage(a)
    }
    val image by produceState<ImageBitmap?>(initialValue = null, imageFile) {
        val f = imageFile ?: return@produceState
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }
    }

    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val f = imageFile ?: return
        val a = activity ?: return
        scope.launch {
            val ok = ImageExporter.saveToGallery(context, f, "distrava-${Format.date(a.startedAt).replace('/', '-')}-${a.id}")
            snackbar.showSnackbar(context.getString(if (ok) R.string.saved_to_gallery else R.string.save_failed))
        }
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity?.let { Format.dateTime(it.startedAt) } ?: stringResource(R.string.summary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val a = activity
        if (a == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (points.isNotEmpty()) {
                DistravaMap(
                    styleJson = styleJson,
                    route = points,
                    fitRoute = true,
                    showEndpoints = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
                Spacer(Modifier.height(20.dp))
            }

            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.label_distance), Format.distance(a.distanceMeters), Modifier.weight(1f))
                Stat(stringResource(R.string.label_avg_speed), Format.speed(a.avgSpeedMps), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.label_moving_time), Format.duration(a.movingTimeMs), Modifier.weight(1f))
                Stat(stringResource(R.string.label_max_speed), Format.speed(a.maxSpeedMps), Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Stat(stringResource(R.string.label_total_time), Format.duration(a.totalTimeMs), Modifier.fillMaxWidth())

            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val img = image
                if (img != null) {
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1080f / 1920f)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { imageFile?.let { ImageExporter.share(context, it) } },
                enabled = imageFile != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.share), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (ImageExporter.needsStoragePermission()) {
                        storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        save()
                    }
                },
                enabled = imageFile != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(stringResource(R.string.save_to_gallery))
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.delete(activityId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Color(0xFF7A766E))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
