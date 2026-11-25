package com.distrava.app.share

import android.graphics.Bitmap
import android.graphics.Color
import com.distrava.app.data.db.PoiEntity
import com.distrava.app.recording.TrackPoint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Formato do QR code compartilhado entre celulares, sem servidor.
 *
 * Texto = "distrava1:" + base64url(deflate(JSON)).
 * - Favorito: {"t":"r","n":nome,"s":início em segundos,"p":polyline}
 * - Pontos de interesse: {"t":"p","i":[[nome,tipo,lat,lon], ...]}
 *
 * O QR em correção L comporta até ~2900 caracteres; [MAX_TEXT_LENGTH] fica abaixo disso.
 */
sealed interface SharedItem {
    /** Favorito com a rota já reduzida. [startedAtSec] é o início original em epoch segundos. */
    data class Route(val name: String, val startedAtSec: Long, val points: List<TrackPoint>) : SharedItem

    data class Pois(val pois: List<PoiEntity>) : SharedItem
}

object QrPayload {
    const val PREFIX = "distrava1:"
    const val MAX_TEXT_LENGTH = 2800
    private const val ROUTE_MAX_POINTS = 300
    private const val COORD_SCALE = 1e5

    /** Gera o texto do QR; null se o conteúdo não couber em um único QR. */
    fun encode(item: SharedItem): String? {
        val json = when (item) {
            is SharedItem.Route -> JSONObject()
                .put("t", "r")
                .put("n", item.name)
                .put("s", item.startedAtSec)
                .put("p", polyline(RouteProjection.downsample(item.points, ROUTE_MAX_POINTS)))
            is SharedItem.Pois -> JSONObject()
                .put("t", "p")
                .put(
                    "i",
                    JSONArray().also { arr ->
                        item.pois.forEach { p ->
                            arr.put(JSONArray().put(p.name).put(p.kind).put(round5(p.lat)).put(round5(p.lon)))
                        }
                    },
                )
        }
        val text = PREFIX + base64Url(deflate(json.toString().toByteArray(Charsets.UTF_8)))
        return text.takeIf { it.length <= MAX_TEXT_LENGTH }
    }

    /** Lê um texto escaneado; null se não for do distrava ou estiver corrompido. */
    fun decode(text: String): SharedItem? = runCatching {
        if (!text.startsWith(PREFIX)) return null
        val json = JSONObject(String(inflate(base64UrlDecode(text.removePrefix(PREFIX))), Charsets.UTF_8))
        when (json.getString("t")) {
            "r" -> {
                val start = json.getLong("s")
                val pts = decodePolyline(json.getString("p")).mapIndexed { i, (lat, lon) ->
                    // Sem horário original por ponto: distribui a 1 s por ponto a partir do início.
                    TrackPoint(time = start * 1000 + i * 1000L, lat = lat, lon = lon)
                }
                SharedItem.Route(json.getString("n"), start, pts)
            }
            "p" -> {
                val arr = json.getJSONArray("i")
                SharedItem.Pois(
                    List(arr.length()) { i ->
                        val a = arr.getJSONArray(i)
                        PoiEntity(
                            lat = a.getDouble(2),
                            lon = a.getDouble(3),
                            name = a.getString(0),
                            kind = a.getString(1),
                            createdAt = System.currentTimeMillis(),
                        )
                    },
                )
            }
            else -> null
        }
    }.getOrNull()

    /** Bitmap quadrado do QR, pronto para exibir. */
    fun toBitmap(text: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    private fun round5(v: Double) = Math.round(v * COORD_SCALE) / COORD_SCALE

    private fun polyline(points: List<TrackPoint>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for (p in points) {
            val lat = Math.round(p.lat * COORD_SCALE)
            val lon = Math.round(p.lon * COORD_SCALE)
            encodeSigned(lat - prevLat, sb)
            encodeSigned(lon - prevLon, sb)
            prevLat = lat
            prevLon = lon
        }
        return sb.toString()
    }

    private fun encodeSigned(value: Long, sb: StringBuilder) {
        var v = if (value < 0) value.inv() shl 1 else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }

    private fun decodePolyline(s: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        val idx = intArrayOf(0)
        var lat = 0L
        var lon = 0L
        while (idx[0] < s.length) {
            lat += readSigned(s, idx)
            lon += readSigned(s, idx)
            out.add(lat / COORD_SCALE to lon / COORD_SCALE)
        }
        return out
    }

    private fun readSigned(s: String, idx: IntArray): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val b = s[idx[0]++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }

    private fun deflate(bytes: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(bytes)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val inf = Inflater(true)
        inf.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    private fun base64Url(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

    private fun base64UrlDecode(s: String): ByteArray =
        android.util.Base64.decode(s, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
}
