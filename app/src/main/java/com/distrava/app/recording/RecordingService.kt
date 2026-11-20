package com.distrava.app.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.distrava.app.BuildConfig
import com.distrava.app.DistravaApp
import com.distrava.app.MainActivity
import com.distrava.app.R
import com.distrava.app.data.FinishResult
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service de localização (D15). Grava pontos no Room em lotes de 5 s (RNF03).
 */
class RecordingService : LifecycleService() {

    private val repo by lazy { (application as DistravaApp).container.activityRepository }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var activityId: Long = -1
    private var startedAt: Long = 0
    private val points = mutableListOf<TrackPoint>()
    private val pending = mutableListOf<TrackPoint>()
    private var distance = 0.0
    private var rejected = 0
    private var flushJob: Job? = null
    private var simulationJob: Job? = null
    private var simulateRide = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var tracking = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                simulateRide = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SIMULATE, false)
                begin(resumeId = null)
            }
            ACTION_RESUME -> begin(resumeId = intent.getLongExtra(EXTRA_ID, -1).takeIf { it > 0 } ?: RESUME_LATEST)
            ACTION_STOP -> stop()
            // Reiniciado pelo sistema (START_STICKY): retoma o percurso em andamento.
            null -> begin(resumeId = RESUME_LATEST)
        }
        return START_STICKY
    }

    private fun begin(resumeId: Long?) {
        if (tracking) return
        tracking = true
        startInForeground(System.currentTimeMillis())

        lifecycleScope.launch {
            val resumed = when (resumeId) {
                null -> null
                RESUME_LATEST -> repo.findRecording()
                else -> repo.get(resumeId)
            }
            if (resumeId != null && resumed == null) {
                // Nada para retomar.
                tracking = false
                RecordingStateHolder.set(RecordingState.Idle)
                shutdown()
                return@launch
            }
            if (resumed != null) {
                activityId = resumed.id
                startedAt = resumed.startedAt
                points.addAll(repo.points(resumed.id))
                for (i in 1 until points.size) distance += Geo.distanceMeters(points[i - 1], points[i])
            } else {
                startedAt = System.currentTimeMillis()
                activityId = repo.startActivity(startedAt)
            }
            startInForeground(startedAt)
            publish(currentSpeed = 0.0, accuracy = null)
            acquireWakeLock()
            if (simulateRide) simulate() else requestUpdates()
            flushJob = launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // A UI só inicia o serviço com a permissão concedida.
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { fused.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { Log.e(TAG, "Falha ao pedir localização", it) }
    }

    private fun onLocation(loc: Location) {
        onPoint(
            TrackPoint(
                time = loc.time,
                lat = loc.latitude,
                lon = loc.longitude,
                altitude = if (loc.hasAltitude()) loc.altitude else null,
                speedMps = if (loc.hasSpeed()) loc.speed else null,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
            ),
        )
    }

    /** Só debug: alimenta o serviço com um percurso falso, 10× mais rápido que o real. */
    private fun simulate() {
        simulationJob = lifecycleScope.launch {
            for (p in RideSimulator.generate(startTime = System.currentTimeMillis())) {
                onPoint(p)
                delay(100)
            }
        }
    }

    private fun onPoint(p: TrackPoint) {
        if (activityId <= 0) return
        val last = points.lastOrNull()
        val result = LocationFilter.check(last, p)
        if (result != LocationFilter.Result.ACCEPTED) {
            rejected++
            publish(currentSpeed = 0.0, accuracy = p.accuracyM)
            return
        }
        if (last != null) distance += Geo.distanceMeters(last, p)
        points.add(p)
        pending.add(p)
        val speed = p.speedMps?.toDouble()
            ?: last?.let { Geo.distanceMeters(it, p) / ((p.time - it.time) / 1000.0) }
            ?: 0.0
        publish(currentSpeed = speed, accuracy = p.accuracyM)
    }

    private fun publish(currentSpeed: Double, accuracy: Float?) {
        RecordingStateHolder.set(
            RecordingState.Recording(
                activityId = activityId,
                startedAt = startedAt,
                points = points.toList(),
                distanceMeters = distance,
                currentSpeedMps = currentSpeed,
                gpsAccuracyM = accuracy,
            ),
        )
    }

    /** Tudo roda na main thread (callbacks no main looper), então a lista não precisa de trava. */
    private suspend fun flush() {
        if (pending.isEmpty() || activityId <= 0) return
        val batch = pending.toList()
        pending.clear()
        repo.appendPoints(activityId, batch)
    }

    private fun stop() {
        if (!tracking || activityId <= 0) {
            tracking = false
            RecordingStateHolder.set(RecordingState.Idle)
            shutdown()
            return
        }
        fused.removeLocationUpdates(callback)
        simulationJob?.cancel()
        flushJob?.cancel()
        RecordingStateHolder.set(RecordingState.Finishing)
        lifecycleScope.launch {
            val result = withContext(NonCancellable) {
                flush()
                Log.i(TAG, "Finalizando $activityId: ${points.size} pontos, $rejected descartados")
                repo.finish(activityId, System.currentTimeMillis())
            }
            tracking = false
            RecordingStateHolder.set(
                when (result) {
                    is FinishResult.Saved -> RecordingState.Finished(result.activityId)
                    is FinishResult.Discarded -> RecordingState.Discarded(result.reason)
                },
            )
            shutdown()
        }
    }

    private fun shutdown() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startInForeground(chronoBase: Long) {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(chronoBase), type)
    }

    private fun buildNotification(chronoBase: Long): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setColor(ContextCompat.getColor(this, R.color.orange))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(chronoBase)
            .setShowWhen(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.recording_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "distrava:recording").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val FLUSH_INTERVAL_MS = 5_000L
        private const val MAX_WAKE_LOCK_MS = 12 * 60 * 60 * 1000L
        private const val RESUME_LATEST = -2L

        const val ACTION_START = "com.distrava.app.START"
        const val ACTION_RESUME = "com.distrava.app.RESUME"
        const val ACTION_STOP = "com.distrava.app.STOP"
        const val EXTRA_ID = "activity_id"

        const val EXTRA_SIMULATE = "simulate"

        fun start(context: Context, simulate: Boolean = false) = send(
            context,
            Intent(context, RecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_SIMULATE, simulate),
        )

        fun resume(context: Context, activityId: Long) = send(
            context,
            Intent(context, RecordingService::class.java).setAction(ACTION_RESUME).putExtra(EXTRA_ID, activityId),
        )

        fun stop(context: Context) =
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))

        private fun send(context: Context, intent: Intent) = ContextCompat.startForegroundService(context, intent)
    }
}
