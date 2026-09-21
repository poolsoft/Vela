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
    ): List<Route> {
        if (valhallaEngine.isReady(mode)) {
            val routes = valhallaEngine.route(
                origin, destination, mode, avoidTolls, avoidHighways, avoidFerries, departBearingDeg
            )
            if (routes.isNotEmpty()) return routes
        }

        if (obfEngine.isReady(mode)) {
            return obfEngine.route(
                origin, destination, mode, avoidTolls, avoidHighways, avoidFerries, departBearingDeg
            )
        }

        return emptyList()
    }

    override fun currentRoadLimit(lat: Double, lng: Double): Double? {
        return valhallaEngine.currentRoadLimit(lat, lng) ?: obfEngine.currentRoadLimit(lat, lng)
    }
}
