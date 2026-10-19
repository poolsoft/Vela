package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import org.junit.Assert.*
import org.junit.Test

class NearbyCategoriesTest {
    private val center = LatLng(37.575, 36.922)
    private fun place(id: String, lat: Double, lng: Double, category: String?) =
        Place(id = id, name = id, location = LatLng(lat, lng), category = category, distanceMeters = 1.0)

    @Test fun englishAndTurkishCategoryWordsShareSemantics() {
        assertEquals(NearbyCategories.values("Restaurants"), NearbyCategories.values("Restoranlar"))
        assertEquals(listOf("restaurant", "fast food"), NearbyCategories.values("Restoranlar"))
        assertTrue(NearbyCategories.values("Kahve").contains("cafe"))
        assertTrue(NearbyCategories.values("Elektrikli şarj").contains("charging station"))
        assertTrue(NearbyCategories.values("Postaneler").contains("post office"))
        assertTrue(NearbyCategories.values("Mado Kahramanmaraş").isEmpty())
    }

    @Test fun categoryCannotBecomeADistantNamedAddressOrWrongPoiType() {
        val places = listOf(
            place("Restaurants Iowa", 42.04, -93.87, "Address"),
            place("near address", 37.576, 36.922, "Address"),
            place("far restaurant", 38.1, 36.922, "Restaurant"),
            place("pharmacy", 37.576, 36.922, "Pharmacy"),
            place("near restaurant", 37.577, 36.922, "Restaurant"),
            place("nearest fast food", 37.576, 36.922, "Fast_food"),
        )
        val hits = NearbyCategories.nearby(places, center, NearbyCategories.values("Restaurants"))
        assertEquals(listOf("nearest fast food", "near restaurant"), hits.map { it.id })
        assertTrue(hits.all { it.distanceMeters!! > 1.0 && it.distanceMeters!! < 10_000.0 })
    }

    @Test fun googleCategoriesCanBeLocalizedButAddressesAndFarHitsStillDrop() {
        val hits = NearbyCategories.nearby(listOf(
            place("local", 37.576, 36.922, "Türk restoranı"),
            place("address", 37.576, 36.922, "Address"),
            place("far", 42.04, -93.87, "Restaurant")), center,
            NearbyCategories.values("Restaurants"), strictCategory = false)
        assertEquals(listOf("local"), hits.map { it.id })
    }

    @Test fun overpassUsesCategoryTagsAndARealLocalRadius() {
        val query = NearbyCategories.overpassQuery("Restoranlar", center)!!
        assertTrue(query.contains("\"amenity\"~\"^(restaurant|fast_food)$\""))
        assertTrue(query.contains("around:10000,37.575,36.922"))
        assertFalse(query.contains("Restaurants"))
        assertNull(NearbyCategories.overpassQuery("unknown business", center))
    }

    @Test fun filteringLoadedPlacesHandlesTurkishWithoutDiscardingTheSourcePool() {
        val pool = listOf(
            place("IŞIK KÖFTE", 37.576, 36.922, "Restaurant").copy(address = "Çağlayan Mahallesi"),
            place("Mado", 37.577, 36.922, "Restaurant"))
        assertEquals(listOf("IŞIK KÖFTE"), NearbyCategories.filterLoaded(pool, "ışık kofte").map { it.id })
        assertEquals(listOf("IŞIK KÖFTE"), NearbyCategories.filterLoaded(pool, "caglayan").map { it.id })
        assertTrue(NearbyCategories.filterLoaded(pool, "missing name").isEmpty())
        assertSame(pool, NearbyCategories.filterLoaded(pool, ""))
        assertEquals(2, pool.size)
    }

    @Test fun expandingRadiusIsExplicitAndStillBounded() {
        val outer = place("outer restaurant", 37.71, 36.922, "Restaurant")
        val values = NearbyCategories.values("Restaurants")
        assertTrue(NearbyCategories.nearby(listOf(outer), center, values).isEmpty())
        assertEquals(1, NearbyCategories.nearby(listOf(outer), center, values, radiusMeters = 20_000.0).size)
        assertTrue(NearbyCategories.overpassQuery("Restaurants", center, 20_000.0)!!.contains("around:20000"))
        assertFalse(NearbyCategories.overpassQuery("Restaurants", center, 100_000.0)!!.contains("around:100000"))
    }
}
