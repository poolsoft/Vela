package app.vela.carlauncher.desktop

/**
 * Masaustu birakma/yerlesim kurallari (DesktopPlacementEngine).
 *
 * Launcher davranisi:
 * - Hedef hucre bolgesi bosa birakma kabul edilir.
 * - Hedefi TAMAMEN tek bir widget kapliyorsa ve o widget bizim eski bolgemize
 *   sigiyorsa, ustelik eski bolgemizde baska kimse yoksa, yer degistirirler (swap).
 * - Bunlarin disinda birakma REDDEDILIR; cagiran taraf widget'i eski yerine dondurur.
 *   Boylece hicbir zaman ust uste binme olusmaz.
 *
 * Compose'dan bagimsiz, saf Kotlin'dir: grid modundaki "birakilabilir mi / bu sayfada
 * yer var mi" kararlari birim testleriyle dogrulanabilir (DesktopPlacementEngineTest).
 * grid == null serbest mod demektir; o modda hucre kurali islemez, her yerlesim serbesttir.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object DesktopPlacementEngine {

    /** Ayni sayfada hucre bolgeleri kesisiyor mu? (kenara degmek kesisim sayilmaz) */
    fun overlaps(a: WidgetPlacement, b: WidgetPlacement): Boolean =
        a.page == b.page &&
            a.cellX < b.cellX + b.spanX && b.cellX < a.cellX + a.spanX &&
            a.cellY < b.cellY + b.spanY && b.cellY < a.cellY + a.spanY

    /** Hedef bolgeyi kilitleyen widget'lar (kendi widget'i sayilmaz). */
    fun occupantsOf(
        layout: Map<String, WidgetPlacement>,
        id: String,
        target: WidgetPlacement
    ): List<Map.Entry<String, WidgetPlacement>> {
        if (target.cellX < 0 || target.spanX <= 0 || target.spanY <= 0) return emptyList()
        return layout.entries.filter { (wid, pl) -> wid != id && overlaps(pl, target) }
    }

    /** Bolge, istisna edilenler disinda baska widget tarafindan kaplaniyor mu? */
    fun regionBlocked(
        layout: Map<String, WidgetPlacement>,
        region: WidgetPlacement,
        exceptIds: Set<String>
    ): Boolean = layout.entries.any { (wid, pl) -> wid !in exceptIds && overlaps(pl, region) }

    /**
     * Isaretcinin tasinabilecegi hucre: bizim eski bolgemizden bu hareketle BOSALAN
     * alanda tam oturan bir dikdortgenin sol-ust kosesi; yoksa null.
     *
     * Bosalan alan = eski bolge - hedef bolge. Tamamen ayrik yer degistirmede (swap)
     * bu alan eski bolgenin tamamidir; yerinde buyumede bosalan hucre yoktur, dolayisiyla
     * komsunun uzerine buyume reddedilir. Kismi kaydirmada yalniz gercekten bosalan
     * serit kullanilabilir.
     */
    fun freedSpotFor(
        old: WidgetPlacement,
        target: WidgetPlacement,
        other: WidgetPlacement
    ): Pair<Int, Int>? {
        if (other.spanX <= 0 || other.spanY <= 0) return null
        if (other.spanX > old.spanX || other.spanY > old.spanY) return null
        for (cy in old.cellY..(old.cellY + old.spanY - other.spanY)) {
            for (cx in old.cellX..(old.cellX + old.spanX - other.spanX)) {
                var free = true
                for (y in cy until cy + other.spanY) {
                    for (x in cx until cx + other.spanX) {
                        val inOld = x >= old.cellX && x < old.cellX + old.spanX &&
                            y >= old.cellY && y < old.cellY + old.spanY
                        val inTarget = x >= target.cellX && x < target.cellX + target.spanX &&
                            y >= target.cellY && y < target.cellY + target.spanY
                        if (!inOld || inTarget) {
                            free = false
                            break
                        }
                    }
                    if (!free) break
                }
                if (free) return cx to cy
            }
        }
        return null
    }

    /**
     * Birakma kabul edilir mi? (yerlesim yazmayan saf kontrol)
     * Swap icin TEK bir isaretci sarti vardir: hedefin tamamini tek widget kaplamali ve
     * o widget eski bolgumuze sigmali. Iki veya fazla widget hedefe tasiyorsa swap
     * tanimsiz oldugu icin birakma reddedilir (aksi halde bir widget disarida kalirdi).
     */
    fun allowed(
        layout: Map<String, WidgetPlacement>,
        id: String,
        target: WidgetPlacement,
        grid: CellGrid.Grid?
    ): Boolean {
        if (grid == null) return true
        if (target.cellX < 0 || target.spanX <= 0 || target.spanY <= 0) return true
        val occupants = occupantsOf(layout, id, target)
        if (occupants.isEmpty()) return true
        val old = layout[id] ?: return false
        if (old.cellX < 0 || old.spanX <= 0 || old.spanY <= 0) return false
        if (occupants.size != 1) return false
        val other = occupants.first().value
        // Swap, isaretcinin BU HAREKETLE bosalan hucrelere sigmasiyla gecerlidir:
        // hedef bolge eski bolgemizi de kapliyorsa (yerinde buyume) o alan yeni
        // konumumuzda hala dolu olacagi icin isaretci oraya tasinamaz.
        if (freedSpotFor(old, target, other) == null) return false
        // Eski bolgemizde (biz ve swap yapacagimiz widget disinda) baska bir widget varsa
        // swap bolgeyi bosaltmaz; ust uste binme olur.
        return !regionBlocked(layout, old, setOf(id, occupants.first().key))
    }

    /**
     * Bu sayfada bu boyutta widget icin yer var mi? (swap dahil)
     * Sayfa degistirme onizlemesi icin kullanilir: yer yoksa sayfaya gecilmez.
     */
    fun hasRoom(
        layout: Map<String, WidgetPlacement>,
        id: String,
        page: Int,
        spanX: Int,
        spanY: Int,
        grid: CellGrid.Grid?
    ): Boolean {
        if (grid == null) return true
        val base = layout[id] ?: WidgetPlacement()
        val sx = spanX.coerceIn(1, grid.cols)
        val sy = spanY.coerceIn(1, grid.rows)
        for (cy in 0..(grid.rows - sy)) {
            for (cx in 0..(grid.cols - sx)) {
                val candidate = base.copy(
                    page = page,
                    cellX = cx,
                    cellY = cy,
                    dx = cx * grid.cellWidth,
                    dy = cy * grid.cellHeight,
                    spanX = sx,
                    spanY = sy
                )
                if (allowed(layout, id, candidate, grid)) return true
            }
        }
        return false
    }

    /**
     * Kabul edilen birakmanin yazilacak yerlesimleri: widget hedefe, hedefi kaplayan
     * tek widget bu hareketle bosalan hucrelere tasinir. Swap yoksa tek yazim doner.
     */
    fun swapPlan(
        layout: Map<String, WidgetPlacement>,
        id: String,
        target: WidgetPlacement,
        grid: CellGrid.Grid?
    ): List<Pair<String, WidgetPlacement>> {
        if (grid == null) return listOf(id to target)
        val old = layout[id]
        val occupant = occupantsOf(layout, id, target).singleOrNull()
        if (old == null || occupant == null || old.cellX < 0) return listOf(id to target)
        // Isaretci her zaman eski bolgenin sol-ustune degil, gercekten bosalan alana konur;
        // aksi halde kismi kaydirmada yeni konumumuzun uzerine binerdi.
        val (spotX, spotY) = freedSpotFor(old, target, occupant.value) ?: return listOf(id to target)
        val (originX, originY) = grid.originDp(spotX, spotY)
        val moved = occupant.value.copy(
            page = old.page,
            cellX = spotX,
            cellY = spotY,
            dx = originX,
            dy = originY
        )
        return listOf(id to target, occupant.key to moved)
    }
}
