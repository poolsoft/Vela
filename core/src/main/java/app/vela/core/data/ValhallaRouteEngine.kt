package app.vela.core.data

import android.content.Context
import android.os.Environment
import android.util.Log
import app.vela.core.model.LatLng
import app.vela.core.model.Lane
import app.vela.core.model.Maneuver
import app.vela.core.model.ManeuverType
import app.vela.core.model.Route
import app.vela.core.model.RouteLeg
import app.vela.core.model.RouteSource
import app.vela.core.model.TravelMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.valhalla.api.models.AutoCostingOptions
import com.valhalla.api.models.CostingModel
import com.valhalla.api.models.CostingOptions
import com.valhalla.api.models.DirectionsOptions
import com.valhalla.api.models.DirectionsOptions.DirectionsType
import com.valhalla.api.models.RouteManeuver
import com.valhalla.api.models.RouteRequest as ValhallaRouteRequest
import com.valhalla.api.models.RouteResponseTrip
import com.valhalla.api.models.RoutingWaypoint
import com.valhalla.config.models.ValhallaConfig
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaResponse
import com.valhalla.valhalla.config.ValhallaConfigManager
import com.valhalla.valhalla.files.ValhallaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Valhalla cevrimdisi rota motoru.
 * valhalla_tiles.tar dosyasini okuyarak internetsiz aninda rota hesaplar.
 */
@Singleton
class ValhallaRouteEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : RouteEngine {

    private val routeLock = Any()
    @Volatile private var valhalla: Valhalla? = null
    @Volatile private var tilePath: String? = null

    init {
        tryAutoInitialize()
    }

    /**
     * Cihazdaki olasi valhalla_tiles.tar dosya konumlarini kontrol edip baslatir.
     */
    fun tryAutoInitialize(): Boolean {
        if (isReady(TravelMode.DRIVE)) return true
        val tarFile = findRoutingTar() ?: return false
        return initialize(tarFile.absolutePath)
    }

    fun initialize(tarPath: String): Boolean {
        synchronized(routeLock) {
            if (this.tilePath == tarPath && valhalla != null) return true
            return try {
                val file = File(tarPath)
                if (!file.exists() || file.length() == 0L) {
                    Log.w(TAG, "Valhalla tar dosyasi bulunamadi veya bos: $tarPath")
                    return false
                }

                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val config = buildConfig(tarPath)
                val configFile = ValhallaFile(context, "valhalla.json", context.filesDir)
                val configManager = ValhallaConfigManager(context, configFile, moshi)
                configManager.writeConfig(config)

                valhalla = Valhalla(context, config, configManager, moshi)
                this.tilePath = tarPath
                Log.i(TAG, "Valhalla basariyla baslatildi: $tarPath (${file.length()} bytes)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Valhalla baslatilirken hata olustu: ${e.message}", e)
                false
            }
        }
    }

    private fun findRoutingTar(): File? {
        val candidates = listOf(
            File(context.filesDir, "valhalla/valhalla_tiles.tar"),
            File(context.filesDir, "routing/valhalla_tiles.tar"),
            File(context.getExternalFilesDir(null), "routing/valhalla_tiles.tar"),
            File(Environment.getExternalStorageDirectory(), "valhalla/valhalla_tiles.tar"),
            File(Environment.getExternalStorageDirectory(), "Download/turkey_valhalla.tar"),
            File(Environment.getExternalStorageDirectory(), "Download/valhalla_tiles.tar"),
        )
        return candidates.firstOrNull { it.isFile && it.length() > 0 }
    }

    private fun buildConfig(tarPath: String): ValhallaConfig {
        val builderClass = Class.forName(CONFIG_BUILDER_CLASS_NAME)
        var builder = builderClass.getDeclaredConstructor().newInstance()
        builder = builderClass.getMethod("withTileExtract", String::class.java).invoke(builder, tarPath)
        return builderClass.getMethod("build").invoke(builder) as ValhallaConfig
    }

    override fun isReady(mode: TravelMode): Boolean {
        if (mode == TravelMode.TRANSIT) return false
        if (valhalla != null && tilePath != null) return true
        return tryAutoInitialize()
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
        if (!isReady(mode)) return emptyList()

        return synchronized(routeLock) {
            val engine = valhalla ?: return@synchronized emptyList()
            try {
                val locations = mutableListOf<RoutingWaypoint>()
                locations.add(
                    RoutingWaypoint(
                        lat = origin.lat,
                        lon = origin.lng,
                        heading = departBearingDeg?.toInt(),
                    )
                )
                locations.add(RoutingWaypoint(destination.lat, destination.lng))

                val costing = when (mode) {
                    TravelMode.DRIVE -> CostingModel.auto
                    TravelMode.BICYCLE -> CostingModel.bicycle
                    TravelMode.WALK -> CostingModel.pedestrian
                    TravelMode.TRANSIT -> CostingModel.auto
                }

                val autoOptions = AutoCostingOptions(
                    useTolls = if (avoidTolls) 0.0 else null,
                    useHighways = if (avoidHighways) 0.0 else null,
                    useFerry = if (avoidFerries) 0.0 else null,
                )
                val costingOptions = CostingOptions(auto = autoOptions)
                val directionsOptions = DirectionsOptions(
                    directionsType = DirectionsType.instructions,
                )

                val request = ValhallaRouteRequest(
                    locations = locations,
                    costing = costing,
                    costingOptions = costingOptions,
                    directionsOptions = directionsOptions,
                )

                val response = engine.route(request)
                parseResponse(response, origin, destination)
            } catch (e: Exception) {
                Log.e(TAG, "Valhalla rota hesaplama hatasi: ${e.message}", e)
                emptyList()
            }
        }
    }

    private fun parseResponse(
        response: ValhallaResponse,
        origin: LatLng,
        destination: LatLng,
    ): List<Route> {
        val routeResponse = when (response) {
            is ValhallaResponse.Json -> response.jsonResponse
            else -> return emptyList()
        }

        val trip: RouteResponseTrip = routeResponse.trip ?: return emptyList()
        val summary = trip.summary ?: return emptyList()

        val fullPolyline = mutableListOf<LatLng>()
        val legsList = mutableListOf<RouteLeg>()

        trip.legs.forEach { leg ->
            val legPolyline = decodePolyline(leg.shape)
            fullPolyline.addAll(legPolyline)

            val maneuversList = mutableListOf<Maneuver>()
            leg.maneuvers.forEach { m ->
                val stepLocation = if (m.beginShapeIndex in legPolyline.indices) {
                    legPolyline[m.beginShapeIndex]
                } else {
                    origin
                }

                val mType = mapManeuverType(m.type)
                val streetName = m.streetNames?.firstOrNull()

                maneuversList.add(
                    Maneuver(
                        type = mType,
                        instruction = m.instruction ?: "",
                        location = stepLocation,
                        distanceMeters = m.length.toDouble() * 1000.0, // Valhalla km cinsinden verebilir, kontrol edilir
                        durationSeconds = m.time.toDouble(),
                        road = streetName,
                    )
                )
            }

            legsList.add(
                RouteLeg(
                    distanceMeters = leg.summary.length.toDouble() * 1000.0,
                    durationSeconds = leg.summary.time.toDouble(),
                    durationInTrafficSeconds = null,
                    maneuvers = maneuversList,
                )
            )
        }

        val route = Route(
            polyline = fullPolyline,
            legs = legsList,
            distanceMeters = summary.length.toDouble() * 1000.0,
            durationSeconds = summary.time.toDouble(),
            durationInTrafficSeconds = null,
            offline = true,
            source = RouteSource.VALHALLA,
        )

        return listOf(route)
    }

    private fun mapManeuverType(type: Int): ManeuverType {
        return when (type) {
            1, 2, 3 -> ManeuverType.DEPART
            4, 5, 6 -> ManeuverType.ARRIVE
            7, 8 -> ManeuverType.CONTINUE
            9 -> ManeuverType.SLIGHT_RIGHT
            10 -> ManeuverType.TURN_RIGHT
            11 -> ManeuverType.SHARP_RIGHT
            12 -> ManeuverType.UTURN
            13 -> ManeuverType.UTURN
            14 -> ManeuverType.SHARP_LEFT
            15 -> ManeuverType.TURN_LEFT
            16 -> ManeuverType.SLIGHT_LEFT
            17 -> ManeuverType.STRAIGHT
            18 -> ManeuverType.RAMP_RIGHT
            19 -> ManeuverType.RAMP_LEFT
            20 -> ManeuverType.RAMP_RIGHT
            21 -> ManeuverType.RAMP_LEFT
            22 -> ManeuverType.MERGE
            23 -> ManeuverType.FORK_RIGHT
            24 -> ManeuverType.FORK_LEFT
            25 -> ManeuverType.FORK_RIGHT
            26 -> ManeuverType.ROUNDABOUT
            27 -> ManeuverType.EXIT_ROUNDABOUT
            else -> ManeuverType.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "ValhallaRouteEngine"
        private const val CONFIG_BUILDER_CLASS_NAME = "ValhallaConfigBuilder"

        internal fun decodePolyline(encoded: String?): List<LatLng> {
            if (encoded.isNullOrEmpty()) return emptyList()
            val points = mutableListOf<LatLng>()
            var lat = 0
            var lon = 0
            var index = 0

            while (index < encoded.length) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                shift = 0
                result = 0
                do {
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                lon += if (result and 1 != 0) (result shr 1).inv() else result shr 1

                points.add(LatLng(lat = lat / 1e6, lng = lon / 1e6))
            }

            return points
        }
    }
}
