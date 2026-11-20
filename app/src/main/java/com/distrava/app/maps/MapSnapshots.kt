package com.distrava.app.maps

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.distrava.app.recording.TrackPoint
import com.distrava.app.share.RouteProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Renderiza o mapa offline com a rota num bitmap, sem MapView na tela (D20).
 * Usado na imagem resumo e nas miniaturas do histórico.
 */
class MapSnapshots(
    context: Context,
    private val offlineMaps: OfflineMapStore,
    private val mapStyle: MapStyle,
) {
    private val appContext = context.applicationContext
    private val thumbsDir = File(appContext.filesDir, "thumbs")

    // Um snapshot por vez: cada um sobe um contexto OpenGL próprio.
    private val lock = Mutex()

    val hasMaps: Boolean get() = offlineMaps.regions.value.isNotEmpty()

    /**
     * Mapa com a rota, ou null se não houver mapa instalado ou o snapshot falhar.
     * @param widthPx/heightPx tamanho final do bitmap em pixels.
     * @param pixelRatio densidade (rótulos e linhas escalam com ela).
     */
    suspend fun routeMap(
        points: List<TrackPoint>,
        widthPx: Int,
        heightPx: Int,
        pixelRatio: Float,
        routeWidth: Float = 5f,
        paddingFraction: Double = 0.12,
    ): Bitmap? {
        val regions = offlineMaps.regions.value
        if (regions.isEmpty() || points.size < 2) return null
        val sampled = RouteProjection.downsample(points, MAX_POINTS)
        val style = mapStyle.build(regions, sampled, routeWidth)
        val bounds = boundsWithPadding(sampled, paddingFraction)
        val w = (widthPx / pixelRatio).toInt().coerceAtLeast(1)
        val h = (heightPx / pixelRatio).toInt().coerceAtLeast(1)

        return lock.withLock {
            withTimeoutOrNull(TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { cont ->
                        val options = MapSnapshotter.Options(w, h)
                            .withStyleBuilder(Style.Builder().fromJson(style))
                            .withRegion(bounds)
                            .withPixelRatio(pixelRatio)
                            .withLogo(false)
                        val snapshotter = MapSnapshotter(appContext, options)
                        snapshotter.start(
                            { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                            { error ->
                                Log.w(TAG, "Snapshot falhou: $error")
                                if (cont.isActive) cont.resume(null)
                            },
                        )
                        cont.invokeOnCancellation { snapshotter.cancel() }
                    }
                }
            }
        }
    }

    /** Miniatura quadrada do histórico, com cache em disco por conjunto de mapas instalados. */
    suspend fun thumbnail(activityId: Long, points: List<TrackPoint>, sizePx: Int, density: Float): Bitmap? {
        if (!hasMaps || points.size < 2) return null
        val file = File(thumbsDir, "${activityId}_${regionsKey()}_$sizePx.png")
        withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) } }
            ?.let { return it }
        val bmp = routeMap(points, sizePx, sizePx, density, routeWidth = 3f, paddingFraction = 0.18) ?: return null
        withContext(Dispatchers.IO) {
            thumbsDir.mkdirs()
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return bmp
    }

    fun deleteThumbnails(activityId: Long) {
        thumbsDir.listFiles { f -> f.name.startsWith("${activityId}_") }?.forEach { it.delete() }
    }

    private fun regionsKey(): String =
        offlineMaps.regions.value.joinToString("|") { "${it.name}:${it.sizeBytes}" }.hashCode().toUInt().toString(16)

    private fun boundsWithPadding(points: List<TrackPoint>, fraction: Double): LatLngBounds {
        var minLat = points.minOf { it.lat }
        var maxLat = points.maxOf { it.lat }
        var minLon = points.minOf { it.lon }
        var maxLon = points.maxOf { it.lon }
        // Tamanho mínimo (~300 m) para percursos muito curtos não virarem zoom máximo.
        val minSpan = 0.003
        if (maxLat - minLat < minSpan) {
            val c = (maxLat + minLat) / 2
            minLat = c - minSpan / 2
            maxLat = c + minSpan / 2
        }
        if (maxLon - minLon < minSpan) {
            val c = (maxLon + minLon) / 2
            minLon = c - minSpan / 2
            maxLon = c + minSpan / 2
        }
        val padLat = (maxLat - minLat) * fraction
        val padLon = (maxLon - minLon) * fraction
        return LatLngBounds.Builder()
            .include(LatLng(maxLat + padLat, maxLon + padLon))
            .include(LatLng(minLat - padLat, minLon - padLon))
            .build()
    }

    companion object {
        private const val TAG = "MapSnapshots"
        private const val MAX_POINTS = 3000
        private const val TIMEOUT_MS = 20_000L
    }
}
