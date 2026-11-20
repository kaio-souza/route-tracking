package com.distrava.app.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

object LocationAccess {
    fun hasFine(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasCoarse(context: Context) = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun isGpsOn(context: Context): Boolean =
        LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    /** Última posição conhecida de qualquer provedor, mesmo antiga ou imprecisa. Só para centralizar o mapa. */
    @SuppressLint("MissingPermission")
    fun lastKnownAny(context: Context): LatLon? {
        if (!hasCoarse(context) && !hasFine(context)) return null
        val lm = context.getSystemService(LocationManager::class.java)
        return lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { LatLon(it.latitude, it.longitude) }
    }

    fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Posição atual enquanto [enabled] (tela visível, permissão concedida e sem gravação).
 * Durante a gravação a posição vem do serviço.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberCurrentLocation(enabled: Boolean): State<LatLon?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<LatLon?>(null) }

    DisposableEffect(enabled) {
        if (!enabled || !LocationAccess.hasFine(context)) return@DisposableEffect onDispose { }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { state.value = LatLon(it.latitude, it.longitude) }
            }
        }
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && state.value == null) state.value = LatLon(loc.latitude, loc.longitude)
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { client.removeLocationUpdates(callback) }
    }
    return state
}
