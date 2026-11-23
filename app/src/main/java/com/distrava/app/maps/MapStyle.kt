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
    fun build(
        regions: List<MapRegion>,
        route: List<TrackPoint>? = null,
        routeWidth: Float = 5f,
        dark: Boolean = false,
    ): String {
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
                .put("paint", JSONObject().put("background-color", if (dark) DARK_LAND else LAND_COLOR)),
        )
        for (t in 0 until layerTemplate.length()) {
            val template = layerTemplate.getJSONObject(t)
            for (i in regions.indices) {
                val layer = JSONObject(template.toString())
                layer.put("id", "r$i-${template.getString("id")}")
                layer.put("source", "r$i")
                if (dark) layer.optJSONObject("paint")?.let(::darken)
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

    /** Troca as cores claras do modelo por equivalentes escuras, propriedade por propriedade. */
    private fun darken(paint: JSONObject) {
        for (key in paint.keys().asSequence().toList()) {
            if (!key.endsWith("-color")) continue
            paint.put(key, if (key == "text-halo-color") DARK_LAND else darkenValue(paint.get(key)))
        }
    }

    private fun darkenValue(value: Any): Any = when (value) {
        is String -> DARK_PALETTE[value.uppercase()] ?: value
        is JSONArray -> JSONArray().also { out -> for (i in 0 until value.length()) out.put(darkenValue(value.get(i))) }
        else -> value
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
        const val DARK_LAND = "#1C1B19"

        private val DARK_PALETTE = mapOf(
            "#EFECE5" to DARK_LAND, // terra
            "#D3E2C3" to "#1F2A1D", // floresta
            "#E1E8CF" to "#232A20", // campo
            "#DCE5C9" to "#22281F", // mato
            "#ECE9D8" to "#24241E", // lavoura
            "#EAE4D6" to "#252320", // solo exposto
            "#E8E3DA" to "#22211E",
            "#CFE2BF" to "#1E2B1C", // áreas verdes
            "#E6E0D5" to "#211F1C", // área urbana
            "#A8CDE0" to "#15283A", // água
            "#DDD6CA" to "#2C2A27", // prédios
            "#B7AE9E" to "#5A554D", // trilhas
            "#D6CFC2" to "#2A2825", // contorno ruas menores
            "#FFFFFF" to "#3A3733", // ruas
            "#CFC6B6" to "#2A2825", // contorno ruas principais
            "#D9B97A" to "#6B5530", // contorno rodovias
            "#F6E3B4" to "#8A6D3A", // rodovias
            "#B5ADA0" to "#4A463F", // ferrovia
            "#A79F92" to "#5A554D", // limites
            "#4A7C99" to "#7FA9C4", // nomes de água
            "#5B564E" to "#A8A196", // nomes de ruas
            "#8A8378" to "#8F887D", // bairros
            "#2B2B2B" to "#E6E1D8", // cidades
            "#7A766E" to "#A39D92", // regiões
        )
    }
}
