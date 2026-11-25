package com.distrava.app.data

import android.graphics.Bitmap
import com.distrava.app.data.db.ActivityDao
import com.distrava.app.maps.MapSnapshots
import com.distrava.app.data.db.ActivityEntity
import com.distrava.app.data.db.ActivityStatus
import com.distrava.app.data.db.TrackPointEntity
import com.distrava.app.recording.StatsCalculator
import com.distrava.app.recording.TrackPoint
import com.distrava.app.share.RouteProjection
import com.distrava.app.share.SummaryImageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

enum class DiscardReason {
    /** Nenhum ponto de GPS válido foi registrado. */
    NO_GPS,

    /** Menos de 50 m. */
    TOO_SHORT,
}

sealed interface FinishResult {
    data class Saved(val activityId: Long) : FinishResult
    data class Discarded(val reason: DiscardReason) : FinishResult
}

class ActivityRepository(
    private val dao: ActivityDao,
    private val imageRenderer: SummaryImageRenderer,
    private val snapshots: MapSnapshots,
    private val imagesDir: File,
) {
    fun observeFinished(): Flow<List<ActivityEntity>> = dao.observeFinished()

    fun observe(id: Long): Flow<ActivityEntity?> = dao.observe(id)

    suspend fun get(id: Long): ActivityEntity? = dao.get(id)

    suspend fun findRecording(): ActivityEntity? = dao.findRecording()

    fun observeFavorites(): Flow<List<ActivityEntity>> = dao.observeFavorites()

    /**
     * Importa um favorito recebido por QR code: cria o percurso finalizado, calcula as
     * estatísticas e marca como favorito com o nome recebido. Retorna null se o percurso
     * for inválido (curto demais ou sem pontos suficientes).
     */
    suspend fun importFavorite(name: String, startedAtSec: Long, points: List<TrackPoint>): Long? {
        if (points.size < 2) return null
        val id = dao.insert(ActivityEntity(startedAt = startedAtSec * 1000))
        appendPoints(id, points)
        return when (finish(id, points.last().time)) {
            is FinishResult.Saved -> id.also { favorite(it, name) }
            is FinishResult.Discarded -> null
        }
    }

    /** Favorita o percurso com um nome; nome em branco usa a data. */
    suspend fun favorite(activityId: Long, name: String) =
        dao.setFavorite(activityId, true, name.trim().ifEmpty { null })

    suspend fun unfavorite(activityId: Long) = dao.setFavorite(activityId, false, null)

    /** Pontos de um percurso finalizado, reduzidos para sobrepor no mapa. */
    suspend fun overlayRoute(activityId: Long, maxPoints: Int = 2000): List<TrackPoint> =
        RouteProjection.downsample(points(activityId), maxPoints)

    suspend fun startActivity(startedAt: Long): Long = dao.insert(ActivityEntity(startedAt = startedAt))

    suspend fun appendPoints(activityId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        dao.insertPoints(points.map { TrackPointEntity.from(activityId, it) })
    }

    suspend fun points(activityId: Long): List<TrackPoint> = dao.getPoints(activityId).map { it.toTrackPoint() }

    /**
     * Rotas dos percursos já finalizados, reduzidas, para sobrepor no mapa.
     * Limita a quantidade para não pesar em quem tem muitos percursos.
     */
    suspend fun historyRoutes(maxRoutes: Int = 200, maxPointsPerRoute: Int = 400): List<List<TrackPoint>> =
        dao.finishedIds(maxRoutes).map { RouteProjection.downsample(points(it), maxPointsPerRoute) }

    /**
     * Calcula as estatísticas, gera a imagem e marca como finalizado.
     * Percursos inválidos (RF03) são apagados.
     */
    suspend fun finish(activityId: Long, finishedAt: Long): FinishResult {
        val activity = dao.get(activityId) ?: return FinishResult.Discarded(DiscardReason.NO_GPS)
        val pts = points(activityId)
        val end = maxOf(finishedAt, pts.lastOrNull()?.time ?: finishedAt)
        val stats = StatsCalculator.compute(pts, activity.startedAt, end)
        if (!StatsCalculator.isValidRide(pts, stats)) {
            delete(activityId)
            return FinishResult.Discarded(if (pts.size < 2) DiscardReason.NO_GPS else DiscardReason.TOO_SHORT)
        }
        val finished = activity.copy(
            finishedAt = end,
            status = ActivityStatus.FINISHED,
            distanceMeters = stats.distanceMeters,
            totalTimeMs = stats.totalTimeMs,
            movingTimeMs = stats.movingTimeMs,
            avgSpeedMps = stats.avgSpeedMps,
            maxSpeedMps = stats.maxSpeedMps,
        )
        val image = renderImage(finished, pts)
        dao.update(finished.copy(imagePath = image.absolutePath))
        return FinishResult.Saved(activityId)
    }

    /** Gera de novo a imagem se o arquivo sumiu ou é de um layout antigo. */
    suspend fun ensureImage(activity: ActivityEntity): File {
        val current = imageFile(activity.id)
        if (activity.imagePath == current.absolutePath && current.exists()) return current
        activity.imagePath?.let { File(it) }?.takeIf { it != current }?.delete()
        val image = renderImage(activity, points(activity.id))
        dao.update(activity.copy(imagePath = image.absolutePath))
        return image
    }

    /** Miniatura com mapa para o histórico; null se não houver mapa instalado. */
    suspend fun thumbnail(activityId: Long, sizePx: Int, density: Float): Bitmap? =
        snapshots.thumbnail(activityId, points(activityId), sizePx, density)

    suspend fun delete(activityId: Long) {
        withContext(Dispatchers.IO) {
            imagesDir.listFiles { f -> f.name.startsWith("${activityId}_") || f.name == "$activityId.png" }
                ?.forEach { it.delete() }
            snapshots.deleteThumbnails(activityId)
        }
        dao.delete(activityId)
    }

    private fun imageFile(activityId: Long) = File(imagesDir, "${activityId}_v${SummaryImageRenderer.VERSION}.png")

    private suspend fun renderImage(activity: ActivityEntity, pts: List<TrackPoint>): File {
        withContext(Dispatchers.IO) { imagesDir.mkdirs() }
        val file = imageFile(activity.id)
        imageRenderer.renderToFile(activity, pts, file)
        return file
    }
}
