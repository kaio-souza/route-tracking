package com.distrava.app.ui.map

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Resumo dos satélites para o usuário saber se o GPS está funcionando. */
data class GnssInfo(val visible: Int, val usedInFix: Int, val withSignal: Int)

/**
 * Satélites vistos pelo chip GNSS enquanto [enabled]. Só recebe dados quando
 * alguém (a própria tela ou o serviço) está pedindo localização por GPS.
 */
@SuppressLint("MissingPermission")
@Composable
fun rememberGnssStatus(enabled: Boolean): State<GnssInfo?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<GnssInfo?>(null) }

    DisposableEffect(enabled) {
        if (!enabled || !LocationAccess.hasFine(context)) return@DisposableEffect onDispose { }
        val lm = context.getSystemService(LocationManager::class.java)
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                var signal = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                    if (status.getCn0DbHz(i) > 0f) signal++
                }
                state.value = GnssInfo(status.satelliteCount, used, signal)
            }

            override fun onStopped() {
                state.value = null
            }
        }
        runCatching { lm.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper())) }
            .onFailure { Log.w("GnssStatus", "Falha ao registrar", it) }
        onDispose { lm.unregisterGnssStatusCallback(callback) }
    }
    return state
}

/**
 * Pede ao sistema para baixar os dados de órbita dos satélites (A-GPS / XTRA) e acertar a hora.
 * Com internet, isso reduz a primeira posição de minutos para segundos. Sem internet não faz nada.
 */
fun requestGpsAssistance(lm: LocationManager) {
    runCatching {
        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
        lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
    }.onFailure { Log.w("GnssStatus", "A-GPS indisponível", it) }
}
