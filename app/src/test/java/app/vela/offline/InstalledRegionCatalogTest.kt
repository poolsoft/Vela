package app.vela.offline

import org.junit.Assert.*
import org.junit.Test

class InstalledRegionCatalogTest {
    @Test fun restoredRegionIsVisibleWithoutAnyServerCatalog() {
        val region = mergeInstalledRegions(emptyList(), mapOf("test_region" to doubleArrayOf(37.0, 36.0, 38.0, 37.0))).single()
        assertEquals("test_region", region.id)
        assertEquals("test region", region.name)
        assertEquals(37.0, region.s, 0.0)
        assertTrue(region.url.isBlank())
    }

    @Test fun cachedNameAndDownloadMetadataAreNotReplacedByFallback() {
        val cached = parseManifest("""{"regions":[{"id":"tr","name":"Türkiye","url":"https://example.invalid/tr.obf","sizeMb":90,"installedMb":92,"rev":7,"bbox":[35,25,43,45]}]}""").single()
        val merged = mergeInstalledRegions(listOf(cached), mapOf("tr" to doubleArrayOf(35.0, 25.0, 43.0, 45.0)))
        assertEquals(listOf(cached), merged)
        assertEquals(7, merged.single().rev)
    }

    @Test fun localOnlyMapAndPackIdsAreMergedWithoutDuplicates() {
        val known = RoutingRegion("routing", "Routing", "https://example.invalid/a", 1, 0.0, 0.0, 1.0, 1.0)
        val local = listOf("routing", "map-only", "pack-only").associateWith { doubleArrayOf(0.0, 0.0, 1.0, 1.0) }
        assertEquals(setOf("routing", "map-only", "pack-only"), mergeInstalledRegions(listOf(known), local).map { it.id }.toSet())
        assertEquals(3, mergeInstalledRegions(listOf(known), local).size)
    }
    @Test fun overlappingCachedSourcesDoNotDuplicateAnInstalledRegion() {
        val region = RoutingRegion("same", "Same", "https://example.invalid/a", 1, 0.0, 0.0, 1.0, 1.0)
        assertEquals(listOf(region), mergeInstalledRegions(listOf(region, region), emptyMap()))
    }
}
