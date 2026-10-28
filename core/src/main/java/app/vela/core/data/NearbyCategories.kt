package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import app.vela.core.model.distanceTo
import java.util.Locale

/** Exact category words are semantic POI queries, never worldwide address geocoding. */
object NearbyCategories {
    const val RADIUS_METERS = 10_000.0
    private val aliases = mapOf(
        "restoran" to "Restaurants", "restoranlar" to "Restaurants", "lokanta" to "Restaurants",
        "kahve" to "Coffee", "kafe" to "Coffee", "benzinlik" to "Gas station",
        "market" to "Groceries", "gezilecek yerler" to "Things to do", "otel" to "Hotels", "oteller" to "Hotels",
        "postaneler" to "Post office", "elektrikli şarj" to "charging", "atm’ler" to "ATMs",
        "barlar" to "Bars", "şarj istasyonu" to "EV charging station", "elektrikli araç şarjı" to "EV charging station",
        "otopark" to "Parking", "eczane" to "Pharmacy", "parklar" to "Parks", "hastaneler" to "Hospitals",
        "hastane" to "Hospitals", "bankalar" to "Banks", "banka" to "Banks", "postane" to "Post office",
        "kamp alanları" to "Campgrounds", "kamp alanı" to "Campgrounds",
        "ev charging station" to "charging", "elektrikli araç şarj istasyonu" to "charging",
    )
    fun canonical(query: String): String = aliases[query.trim().lowercase(Locale.ROOT)] ?: query.trim()
    fun values(query: String): List<String> = OfflinePoiStore.categoryKeywords(canonical(query))

    fun matches(category: String?, values: List<String>): Boolean =
        category?.replace('_', ' ')?.lowercase(Locale.ROOT)?.trim() in values

    /** Recompute distance from the search anchor, reject geocoded addresses, and keep a hard radius. */
    fun nearby(places: List<Place>, center: LatLng, values: List<String>, strictCategory: Boolean = true, radiusMeters: Double = RADIUS_METERS): List<Place> =
        places.asSequence().filter {
            if (strictCategory) matches(it.category, values)
            else it.category?.lowercase(Locale.ROOT) !in setOf("address", "street", "city", "town", "village")
        }.map { it.copy(distanceMeters = center.distanceTo(it.location)) }
            .filter { (it.distanceMeters ?: Double.MAX_VALUE) <= radiusMeters }
            .distinctBy { it.id }.sortedBy { it.distanceMeters }.take(500).toList()

    fun filterLoaded(places: List<Place>, query: String): List<Place> {
        fun normalized(value: String) = java.text.Normalizer.normalize(value.lowercase(Locale.ROOT),
            java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')
        val words = normalized(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return places
        return places.filter { place ->
            val text = normalized(place.name + " " + place.address.orEmpty())
            words.all(text::contains)
        }
    }

    internal fun overpassQuery(query: String, center: LatLng, radiusMeters: Double = RADIUS_METERS): String? {
        val tags = values(query).map { it.replace(' ', '_') }
            .filter { it.matches(Regex("[a-z0-9_-]+")) }.distinct()
        if (tags.isEmpty()) return null
        val clauses = listOf("amenity", "shop", "tourism", "leisure", "boundary").joinToString("") { key ->
            "nwr[\"$key\"~\"^(${tags.joinToString("|")})$\"][name](around:${radiusMeters.coerceIn(1000.0, 20000.0).toInt()},${center.lat},${center.lng});"
        }
        return "[out:json][timeout:15];($clauses);out center 300;"
    }
}
