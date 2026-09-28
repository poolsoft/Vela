package app.vela.core.data

import app.vela.core.model.LatLng
import app.vela.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineDisambiguationTest {

    @Test
    fun testIlicaVsIlicaCollision() {
        val gercekIlica = Place(
            id = "p1",
            name = "Ilıca",
            location = LatLng(38.0, 36.5),
            category = "neighbourhood",
            address = "Onikişubat, Kahramanmaraş",
            distanceMeters = 80_000.0, // 80 km uzakta
        )
        val sahteIlicaDurak = Place(
            id = "p2",
            name = "İlica",
            location = LatLng(37.5, 37.0),
            category = "platform",
            address = "Battalgazi, Malatya",
            distanceMeters = 5_000.0, // 5 km yakinda ama otobus duragi
        )
        val sahteIlicaDukkan = Place(
            id = "p3",
            name = "İlica Kuaför",
            location = LatLng(37.5, 37.1),
            category = "shop",
            address = "Battalgazi, Malatya",
            distanceMeters = 2_000.0, // 2 km yakinda kisisel dukkan
        )

        val adaylar = listOf(
            OfflinePoiStore.AramaAdayi(sahteIlicaDurak, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
            OfflinePoiStore.AramaAdayi(gercekIlica, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
            OfflinePoiStore.AramaAdayi(sahteIlicaDukkan, metinPuani = 3.0, rank = 1, kelimeUyumSayisi = 1),
        )

        val userLoc = LatLng(37.5, 37.0)
        val sonuclar = OfflinePoiStore.disambiguateAndRank("Ilıca", adaylar, userLoc, limit = 10)

        // Gercek Ilica harf sadakati ve onem puaniyla 80 km uzakta olsa dahi birinci olmali
        assertEquals("p1", sonuclar.first().id)
    }

    @Test
    fun testKinikVsKinikCollision() {
        val kinikIlce = Place(
            id = "k1",
            name = "Kınık",
            location = LatLng(39.0, 27.3),
            category = "town",
            address = "İzmir",
            distanceMeters = 150_000.0, // 150 km uzakta ilce
        )
        val kinikMarket = Place(
            id = "k2",
            name = "Kinik Market",
            location = LatLng(38.0, 27.0),
            category = "shop",
            address = "Konak, İzmir",
            distanceMeters = 1_000.0, // 1 km yakinda market
        )

        val adaylar = listOf(
            OfflinePoiStore.AramaAdayi(kinikMarket, metinPuani = 5.0, rank = 1, kelimeUyumSayisi = 1),
            OfflinePoiStore.AramaAdayi(kinikIlce, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
        )

        val userLoc = LatLng(38.0, 27.0)
        val sonuclar = OfflinePoiStore.disambiguateAndRank("Kınık", adaylar, userLoc, limit = 10)

        assertEquals("k1", sonuclar.first().id)
    }

    @Test
    fun testContextDisambiguationMultiWord() {
        val marasIlica = Place(
            id = "m1",
            name = "Ilıca",
            location = LatLng(38.0, 36.5),
            category = "thermal",
            address = "Onikişubat, Kahramanmaraş",
            distanceMeters = 100_000.0,
        )
        val cesmeIlica = Place(
            id = "c1",
            name = "Ilıca",
            location = LatLng(38.3, 26.3),
            category = "thermal",
            address = "Çeşme, İzmir",
            distanceMeters = 100_000.0,
        )

        val adaylar = listOf(
            OfflinePoiStore.AramaAdayi(cesmeIlica, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
            OfflinePoiStore.AramaAdayi(marasIlica, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
        )

        // Kullanici Onikişubat belirteci girdiginde Maras Ilica one gecmeli
        val sonuclar = OfflinePoiStore.disambiguateAndRank("onikişubat ılıca", adaylar, null, limit = 10)
        assertEquals("m1", sonuclar.first().id)
    }

    @Test
    fun testDistanceTieBreakerWhenSameImportanceAndExactMatch() {
        val yakinOtel = Place(
            id = "h1",
            name = "Hilton",
            location = LatLng(38.0, 27.0),
            category = "hotel",
            distanceMeters = 2_000.0,
        )
        val uzakOtel = Place(
            id = "h2",
            name = "Hilton",
            location = LatLng(39.0, 28.0),
            category = "hotel",
            distanceMeters = 120_000.0,
        )

        val adaylar = listOf(
            OfflinePoiStore.AramaAdayi(uzakOtel, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
            OfflinePoiStore.AramaAdayi(yakinOtel, metinPuani = 5.0, rank = 0, kelimeUyumSayisi = 1),
        )

        val userLoc = LatLng(38.0, 27.0)
        val sonuclar = OfflinePoiStore.disambiguateAndRank("Hilton", adaylar, userLoc, limit = 10)

        // Ayni harf sadakati ve kategoriye sahip olundugunda yakin olan one gecmeli
        assertEquals("h1", sonuclar.first().id)
    }
}
