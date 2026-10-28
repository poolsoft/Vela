package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Route
import app.vela.core.model.TravelMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coklu cevrimdisi rota motoru:
 * 1. Oncelik: Valhalla (ultra hizli, RAM dostu hiyerarsik karo)
 * 2. Yedek: OsmAnd Obf (bolgesel .obf harita dosyalari)
 */
@Singleton
class CompositeRouteEngine @Inject constructor(
    private val valhallaEngine: ValhallaRouteEngine,
    private val obfEngine: ObfRouteEngine,
) : RouteEngine {

    override fun isReady(mode: TravelMode): Boolean {
        return valhallaEngine.isReady(mode) || obfEngine.isReady(mode)
    }

    override fun route(
        origin: LatLng,
        destination: LatLng,
        mode: TravelMode,
        avoidTolls: Boolean,
        avoidHighways: Boolean,
        avoidFerries: Boolean,
        departBearingDeg: Double?,
    ): List<Route> = routeControlled(origin, destination, mode, avoidTolls, avoidHighways,
        avoidFerries, departBearingDeg, RouteControl())

    override fun routeControlled(
        origin: LatLng, destination: LatLng, mode: TravelMode,
        avoidTolls: Boolean, avoidHighways: Boolean, avoidFerries: Boolean,
        departBearingDeg: Double?, control: RouteControl,
    ): List<Route> {
        if (control.isCanceled) return emptyList()
        if (valhallaEngine.isReady(mode)) {
            android.util.Log.i("OfflineRoute", "engine=valhalla")
            val routes = valhallaEngine.routeControlled(
                origin, destination, mode, avoidTolls, avoidHighways, avoidFerries, departBearingDeg, control
            )
            if (routes.isNotEmpty()) return routes
        }

        if (!control.isCanceled && obfEngine.isReady(mode)) {
            android.util.Log.i("OfflineRoute", "engine=obf; valhalla unavailable or returned no route")
            return obfEngine.routeControlled(
                origin, destination, mode, avoidTolls, avoidHighways, avoidFerries, departBearingDeg, control
            )
        }

        return emptyList()
    }

    override fun currentRoadLimit(lat: Double, lng: Double): Double? {
        return valhallaEngine.currentRoadLimit(lat, lng) ?: obfEngine.currentRoadLimit(lat, lng)
    }
}
