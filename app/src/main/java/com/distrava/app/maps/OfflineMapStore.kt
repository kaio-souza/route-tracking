package com.distrava.app.maps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class MapRegion(val name: String, val file: File) {
    val sizeBytes: Long get() = file.length()
}

/**
 * Regiões de mapa offline instaladas em filesDir/maps (D11, D12).
 * Cada região é um arquivo .pmtiles no schema do Protomaps.
 */
class OfflineMapStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "maps").apply { mkdirs() }

    private val _regions = MutableStateFlow(scan())
    val regions: StateFlow<List<MapRegion>> = _regions.asStateFlow()

    private fun scan(): List<MapRegion> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            .orEmpty()
            .sortedBy { it.name }
            .map { MapRegion(it.name.removeSuffix(EXT), it) }

    /** Copia o arquivo escolhido pelo usuário para o armazenamento do app. */
    suspend fun import(uri: Uri): Result<MapRegion> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "regiao"
            val name = sanitize(displayName.substringBeforeLast('.'))

            val tmp = File(dir, "$name$EXT.tmp")
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it, bufferSize = 1 shl 16) }
            } ?: throw IOException("Não foi possível abrir o arquivo")

            if (!isPmTiles(tmp)) {
                tmp.delete()
                throw IOException("O arquivo não é um mapa .pmtiles válido")
            }
            val target = File(dir, "$name$EXT")
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Falha ao salvar o mapa")
            _regions.value = scan()
            MapRegion(name, target)
        }
    }

    /**
     * Instala os mapas embutidos no APK (assets/maps) na primeira abertura.
     * Um mapa removido pelo usuário não é reinstalado.
     */
    suspend fun installBundled() = withContext(Dispatchers.IO) {
        val prefs = appContext.getSharedPreferences("distrava", Context.MODE_PRIVATE)
        val bundled = appContext.assets.list("maps").orEmpty().filter { it.endsWith(EXT) }
        for (asset in bundled) {
            val key = "bundled_installed_$asset"
            if (prefs.getBoolean(key, false)) continue
            val target = File(dir, asset)
            val installed = target.exists() || runCatching {
                val tmp = File(dir, "$asset.tmp")
                try {
                    appContext.assets.open("maps/$asset").use { input ->
                        tmp.outputStream().use { input.copyTo(it, bufferSize = 1 shl 16) }
                    }
                    if (!tmp.renameTo(target)) throw IOException("Falha ao instalar $asset")
                } finally {
                    tmp.delete()
                }
            }.onFailure { android.util.Log.e("OfflineMapStore", "Mapa embutido não instalado", it) }.isSuccess
            if (installed) prefs.edit().putBoolean(key, true).apply()
        }
        _regions.value = scan()
    }

    suspend fun delete(region: MapRegion) = withContext(Dispatchers.IO) {
        region.file.delete()
        _regions.value = scan()
    }

    private fun isPmTiles(file: File): Boolean = runCatching {
        file.inputStream().use { s ->
            val header = ByteArray(8)
            s.read(header) == 8 && String(header, 0, 7, Charsets.US_ASCII) == "PMTiles" && header[7].toInt() == 3
        }
    }.getOrDefault(false)

    private fun sanitize(s: String): String =
        s.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "regiao" }

    companion object {
        const val EXT = ".pmtiles"
    }
}
