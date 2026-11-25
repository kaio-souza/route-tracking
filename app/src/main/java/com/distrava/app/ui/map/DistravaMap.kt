package com.distrava.app.ui.map

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.distrava.app.data.db.PoiEntity
import com.distrava.app.recording.TrackPoint
import com.distrava.app.share.RouteProjection
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

data class LatLon(val lat: Double, val lon: Double)

private const val ROUTE_SOURCE = "distrava-route"
private const val ME_SOURCE = "distrava-me"
private const val ENDPOINTS_SOURCE = "distrava-endpoints"
private const val HISTORY_SOURCE = "distrava-history"
private const val OVERLAY_SOURCE = "distrava-overlay"
private const val OVERLAY_COLOR = "#1E88E5"
private const val POI_SOURCE = "distrava-pois"
private const val POI_LAYER = "distrava-pois-circle"
/** Pontos de interesse translúcidos para não cobrir a rota e o mapa. */
private const val POI_OPACITY = 0.8f
private const val HISTORY_COLOR = "#7B61FF"
private const val ORANGE = "#FC6A03"
private const val GRAPHITE = "#2B2B2B"
private const val MAX_ROUTE_POINTS = 5000
private const val FOLLOW_ZOOM = 16.0
private const val FOLLOW_MIN_ZOOM = 14.0
private const val FOLLOW_MIN_MOVE_PX = 12.0
private const val FOLLOW_ANIMATION_MS = 300

/**
 * MapView do MapLibre dentro do Compose.
 *
 * @param follow câmera acompanha [myLocation] (modo gravação).
 * @param recenterSignal cada mudança de valor centraliza na posição atual.
 * @param fitRoute enquadra a rota inteira uma vez (tela de detalhe).
 * @param showEndpoints marca início e fim da rota.
 */
@Composable
fun DistravaMap(
    styleJson: String,
    modifier: Modifier = Modifier,
    route: List<TrackPoint> = emptyList(),
    myLocation: LatLon? = null,
    approximateCenter: LatLon? = null,
    follow: Boolean = false,
    recenterSignal: Int = 0,
    fitRoute: Boolean = false,
    showEndpoints: Boolean = false,
    interactive: Boolean = true,
    bottomPaddingPx: Int = 0,
    historyRoutes: List<List<TrackPoint>> = emptyList(),
    overlayRoute: List<TrackPoint> = emptyList(),
    pois: List<PoiEntity> = emptyList(),
    onLongPress: ((LatLon) -> Unit)? = null,
    onPoiClick: ((Long) -> Unit)? = null,
    onUserGesture: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val gesture by rememberUpdatedState(onUserGesture)
    val longPress by rememberUpdatedState(onLongPress)
    val poiClick by rememberUpdatedState(onPoiClick)

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var centeredOnce by remember { mutableStateOf(false) }
    var fittedOnce by remember { mutableStateOf(false) }
    var approxCentered by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.uiSettings.setAllGesturesEnabled(interactive)
                m.uiSettings.isAttributionEnabled = true
                m.uiSettings.setAttributionTintColor(Color.parseColor("#7A766E"))
                // Brasil inteiro como posição inicial, até chegar o GPS.
                m.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(-8.3, -35.6)).zoom(7.0).build()
                m.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) gesture()
                }
                m.addOnMapLongClickListener { latLng ->
                    val cb = longPress ?: return@addOnMapLongClickListener false
                    cb(LatLon(latLng.latitude, latLng.longitude))
                    true
                }
                m.addOnMapClickListener { latLng ->
                    val cb = poiClick ?: return@addOnMapClickListener false
                    val screen = m.projection.toScreenLocation(latLng)
                    val box = android.graphics.RectF(screen.x - 24, screen.y - 24, screen.x + 24, screen.y + 24)
                    val hit = m.queryRenderedFeatures(box, POI_LAYER).firstOrNull()
                        ?: return@addOnMapClickListener false
                    cb(hit.getNumberProperty("id").toLong())
                    true
                }
                map = m
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Área coberta por painéis: o "centro" do mapa é o centro da parte visível.
    val bottomPadding by rememberUpdatedState(bottomPaddingPx)
    fun cameraTo(target: LatLng, zoom: Double?) = CameraUpdateFactory.newCameraPosition(
        CameraPosition.Builder()
            .target(target)
            .apply { if (zoom != null) zoom(zoom) }
            .padding(0.0, 0.0, 0.0, bottomPadding.toDouble())
            .build(),
    )

    // Estilo: recarrega quando as regiões instaladas mudam.
    LaunchedEffect(map, styleJson) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromJson(styleJson)) { loaded ->
            addOverlay(loaded)
            style = loaded
        }
    }

    LaunchedEffect(style, route, showEndpoints) {
        val s = style ?: return@LaunchedEffect
        val sampled = RouteProjection.downsample(route, MAX_ROUTE_POINTS)
        val coords = sampled.map { Point.fromLngLat(it.lon, it.lat) }
        s.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(
            if (coords.size >= 2) {
                FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(coords)))
            } else {
                FeatureCollection.fromFeatures(emptyList())
            },
        )
        val endpoints = if (showEndpoints && coords.isNotEmpty()) {
            listOf(
                Feature.fromGeometry(coords.first()).also { it.addStringProperty("kind", "start") },
                Feature.fromGeometry(coords.last()).also { it.addStringProperty("kind", "end") },
            )
        } else {
            emptyList()
        }
        s.getSourceAs<GeoJsonSource>(ENDPOINTS_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(endpoints))
    }

    // Percursos anteriores: linhas finas abaixo da rota atual.
    LaunchedEffect(style, historyRoutes) {
        val s = style ?: return@LaunchedEffect
        val lines = historyRoutes.filter { it.size >= 2 }.map { r ->
            Feature.fromGeometry(LineString.fromLngLats(r.map { Point.fromLngLat(it.lon, it.lat) }))
        }
        s.getSourceAs<GeoJsonSource>(HISTORY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(lines))
    }

    // Percurso escolhido pelo usuário para sobrepor (azul, acima do histórico).
    LaunchedEffect(style, overlayRoute) {
        val s = style ?: return@LaunchedEffect
        val feature = overlayRoute.takeIf { it.size >= 2 }?.let {
            Feature.fromGeometry(LineString.fromLngLats(it.map { p -> Point.fromLngLat(p.lon, p.lat) }))
        }
        s.getSourceAs<GeoJsonSource>(OVERLAY_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(feature)))
    }

    LaunchedEffect(style, pois) {
        val s = style ?: return@LaunchedEffect
        val features = pois.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).also {
                it.addNumberProperty("id", p.id)
                it.addStringProperty("name", p.name)
                it.addStringProperty("color", p.poiKind.color)
            }
        }
        s.getSourceAs<GeoJsonSource>(POI_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    LaunchedEffect(style, myLocation) {
        val s = style ?: return@LaunchedEffect
        val features = myLocation?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat))) }.orEmpty()
        s.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Enquanto o GPS não pega, centraliza na última posição conhecida (pode ser aproximada).
    LaunchedEffect(map, approximateCenter) {
        val m = map ?: return@LaunchedEffect
        val c = approximateCenter ?: return@LaunchedEffect
        if (centeredOnce || approxCentered || fitRoute || myLocation != null) return@LaunchedEffect
        m.moveCamera(cameraTo(LatLng(c.lat, c.lon), 13.0))
        approxCentered = true
    }

    // Câmera
    LaunchedEffect(map, myLocation, follow) {
        val m = map ?: return@LaunchedEffect
        val loc = myLocation ?: return@LaunchedEffect
        val target = LatLng(loc.lat, loc.lon)
        when {
            !centeredOnce && !fitRoute -> {
                m.moveCamera(cameraTo(target, 15.0))
                centeredOnce = true
            }
            // Seguindo: posição sempre no centro da área visível; se o zoom estiver muito afastado,
            // volta para nível de rua. Também roda quando volta a seguir (botão ou retorno automático).
            follow -> {
                val zoom = if (m.cameraPosition.zoom < FOLLOW_MIN_ZOOM) FOLLOW_ZOOM else null
                // Economia: cada animação deixa o mapa redesenhando a 60 fps. Se a posição mal mudou
                // na tela (GPS parado oscilando), não move a câmera.
                val current = m.cameraPosition.target
                val movedPx = current?.let {
                    val a = m.projection.toScreenLocation(it)
                    val b = m.projection.toScreenLocation(target)
                    kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())
                } ?: Double.MAX_VALUE
                if (zoom != null || movedPx > FOLLOW_MIN_MOVE_PX) {
                    m.animateCamera(cameraTo(target, zoom), FOLLOW_ANIMATION_MS)
                }
            }
        }
    }

    LaunchedEffect(recenterSignal) {
        if (recenterSignal == 0) return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val loc = myLocation ?: return@LaunchedEffect
        m.animateCamera(cameraTo(LatLng(loc.lat, loc.lon), FOLLOW_ZOOM), 600)
    }

    LaunchedEffect(map, fitRoute, route) {
        val m = map ?: return@LaunchedEffect
        if (!fitRoute || fittedOnce || route.isEmpty()) return@LaunchedEffect
        val lats = route.map { it.lat }
        val lons = route.map { it.lon }
        if (lats.max() - lats.min() < 1e-5 && lons.max() - lons.min() < 1e-5) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lats.first(), lons.first()), 16.0))
        } else {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(lats.max(), lons.max()))
                .include(LatLng(lats.min(), lons.min()))
                .build()
            m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        }
        fittedOnce = true
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun addOverlay(style: Style) {
    style.addSource(GeoJsonSource(HISTORY_SOURCE))
    style.addSource(GeoJsonSource(OVERLAY_SOURCE))
    style.addSource(GeoJsonSource(POI_SOURCE))
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    style.addSource(GeoJsonSource(ENDPOINTS_SOURCE))
    style.addSource(GeoJsonSource(ME_SOURCE))

    // Ordem: histórico < sobreposição < rota atual < início/fim < pontos de interesse < posição atual.
    style.addLayer(
        LineLayer(HISTORY_SOURCE, HISTORY_SOURCE).withProperties(
            PropertyFactory.lineColor(HISTORY_COLOR),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineOpacity(0.55f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(OVERLAY_SOURCE, OVERLAY_SOURCE).withProperties(
            PropertyFactory.lineColor(OVERLAY_COLOR),
            PropertyFactory.lineWidth(4.5f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("$ROUTE_SOURCE-casing", ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(ROUTE_SOURCE, ROUTE_SOURCE).withProperties(
            PropertyFactory.lineColor(ORANGE),
            PropertyFactory.lineWidth(5.5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer(ENDPOINTS_SOURCE, ENDPOINTS_SOURCE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(
                org.maplibre.android.style.expressions.Expression.match(
                    org.maplibre.android.style.expressions.Expression.get("kind"),
                    org.maplibre.android.style.expressions.Expression.literal("#FFFFFF"),
                    org.maplibre.android.style.expressions.Expression.stop("end", GRAPHITE),
                ),
            ),
            PropertyFactory.circleStrokeColor(GRAPHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        CircleLayer(POI_LAYER, POI_SOURCE).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleOpacity(POI_OPACITY),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeOpacity(POI_OPACITY),
        ),
    )
    style.addLayer(
        SymbolLayer("$POI_SOURCE-label", POI_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("NotoSans-Medium")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textOffset(arrayOf(0f, 1f)),
            PropertyFactory.textColor(GRAPHITE),
            PropertyFactory.textOpacity(POI_OPACITY),
            PropertyFactory.textHaloColor("#FFFFFF"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textHaloBlur(0.5f),
            PropertyFactory.textOptional(true),
        ),
    )
    style.addLayer(
        CircleLayer("$ME_SOURCE-halo", ME_SOURCE).withProperties(
            PropertyFactory.circleRadius(18f),
            PropertyFactory.circleColor(ORANGE),
            PropertyFactory.circleOpacity(0.2f),
        ),
    )
    style.addLayer(
        CircleLayer(ME_SOURCE, ME_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(ORANGE),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}
