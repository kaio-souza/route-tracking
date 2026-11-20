package com.distrava.app.maps

import android.content.Context
import com.distrava.app.recording.TrackPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Monta o style JSON do MapLibre a partir de um modelo de camadas em assets/map/layers.json.
 * Cada região instalada vira uma fonte vetorial; as camadas são repetidas por fonte,
 * agrupadas por tipo, para que a água de todas as regiões fique abaixo das ruas e assim por diante.
 */
class MapStyle(context: Context) {
    private val layerTemplate: JSONArray =
        context.assets.open("map/layers.json").bufferedReader().use { JSONArray(it.readText()) }

    /**
     * @param route se informada, a rota é embutida no estilo (usado nos snapshots da imagem e do histórico).
     * @param routeWidth largura da linha da rota em pixels lógicos.
     */
    fun build(regions: List<MapRegion>, route: List<TrackPoint>? = null, routeWidth: Float = 5f): String {
        val sources = JSONObject()
        regions.forEachIndexed { i, r ->
            sources.put(
                "r$i",
                JSONObject()
                    .put("type", "vector")
                    .put("url", "pmtiles://file://${r.file.absolutePath}")
                    .put("attribution", "© OpenStreetMap"),
            )
        }

        val layers = JSONArray()
        layers.put(
            JSONObject()
                .put("id", "background")
                .put("type", "background")
                .put("paint", JSONObject().put("background-color", LAND_COLOR)),
        )
        for (t in 0 until layerTemplate.length()) {
            val template = layerTemplate.getJSONObject(t)
            for (i in regions.indices) {
                val layer = JSONObject(template.toString())
                layer.put("id", "r$i-${template.getString("id")}")
                layer.put("source", "r$i")
                layers.put(layer)
            }
        }

        if (route != null && route.size >= 2) addRoute(sources, layers, route, routeWidth)

        return JSONObject()
            .put("version", 8)
            .put("name", "distrava")
            .put("glyphs", "asset://glyphs/{fontstack}/{range}.pbf")
            .put("sources", sources)
            .put("layers", layers)
            .toString()
    }

    private fun addRoute(sources: JSONObject, layers: JSONArray, route: List<TrackPoint>, width: Float) {
        val coords = JSONArray()
        route.forEach { coords.put(JSONArray().put(it.lon).put(it.lat)) }
        fun point(p: TrackPoint, kind: String) = JSONObject()
            .put("type", "Feature")
            .put("properties", JSONObject().put("kind", kind))
            .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(p.lon).put(p.lat)))

        val features = JSONArray()
            .put(
                JSONObject()
                    .put("type", "Feature")
                    .put("properties", JSONObject().put("kind", "line"))
                    .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coords)),
            )
            .put(point(route.first(), "start"))
            .put(point(route.last(), "end"))
        sources.put(
            "route",
            JSONObject().put("type", "geojson").put("data", JSONObject().put("type", "FeatureCollection").put("features", features)),
        )

        val isLine = JSONArray("""["==", ["geometry-type"], "LineString"]""")
        val isPoint = JSONArray("""["==", ["geometry-type"], "Point"]""")
        val roundLayout = JSONObject().put("line-cap", "round").put("line-join", "round")
        layers.put(
            JSONObject().put("id", "route-casing").put("type", "line").put("source", "route").put("filter", isLine)
                .put("layout", roundLayout)
                .put("paint", JSONObject().put("line-color", "#FFFFFF").put("line-width", width * 1.7f)),
        )
        layers.put(
            JSONObject().put("id", "route-line").put("type", "line").put("source", "route").put("filter", isLine)
                .put("layout", roundLayout)
                .put("paint", JSONObject().put("line-color", ROUTE_COLOR).put("line-width", width)),
        )
        layers.put(
            JSONObject().put("id", "route-endpoints").put("type", "circle").put("source", "route").put("filter", isPoint)
                .put(
                    "paint",
                    JSONObject()
                        .put("circle-radius", width * 1.3f)
                        .put("circle-color", JSONArray("""["match", ["get", "kind"], "end", "$GRAPHITE", "#FFFFFF"]"""))
                        .put("circle-stroke-color", GRAPHITE)
                        .put("circle-stroke-width", width * 0.6f),
                ),
        )
    }

    companion object {
        const val LAND_COLOR = "#EFECE5"
        const val ROUTE_COLOR = "#FC6A03"
        const val GRAPHITE = "#2B2B2B"
    }
}
