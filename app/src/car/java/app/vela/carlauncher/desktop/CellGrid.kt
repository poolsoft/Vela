package app.vela.carlauncher.desktop

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Launcher tarzi hucre grid'i (CellGrid). Masaustu sabit hucre sayisindan olusan bir
 * tahta olarak modellenir; her widget bir hucre bolgesini (cellX, cellY, spanX, spanY)
 * kaplar. Yon basina hucre sayisi farklidir: donen ekranlar icin landscape genis,
 * portrait dik grid kullanilir.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object CellGrid {
    // Varsayilan hucre sayilari; kullanicinin ayarindan override edilir.
    const val DEFAULT_LANDSCAPE_COLS = 10
    const val DEFAULT_LANDSCAPE_ROWS = 5
    const val DEFAULT_PORTRAIT_COLS = 5
    const val DEFAULT_PORTRAIT_ROWS = 8

    /** Hesaplanmis grid: hucre olculeri dp cinsindendir. */
    data class Grid(
        val cols: Int,
        val rows: Int,
        val cellWidth: Float,
        val cellHeight: Float
    ) {
        fun originDp(cellX: Int, cellY: Int): Pair<Float, Float> =
            cellX * cellWidth to cellY * cellHeight
    }

    /**
     * Grid kurulumu; hucre sayisi cagiran taraf tarafindan yon ayarina gore verilir
     * (ayarlanabilir grid yogunlugu).
     */
    fun build(workspaceWidthDp: Float, workspaceHeightDp: Float, cols: Int, rows: Int): Grid {
        val safeCols = cols.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val safeW = workspaceWidthDp.coerceAtLeast(1f)
        val safeH = workspaceHeightDp.coerceAtLeast(1f)
        return Grid(safeCols, safeRows, safeW / safeCols, safeH / safeRows)
    }

    /** Widget turunun taban boyutundan span hesabi. */
    fun spanOf(typeId: String, grid: Grid): Pair<Int, Int> {
        val (minW, minH) = WorkspaceGrid.size(typeId)
        val sx = ceil(minW / grid.cellWidth).toInt().coerceIn(1, grid.cols)
        val sy = ceil(minH / grid.cellHeight).toInt().coerceIn(1, grid.rows)
        return sx to sy
    }

    /**
     * Launcher tarzi yerlesim cozumu: kayitli hucresi bosa oturur, doluysa en yakin
     * bos hucreye itilir; sayfa doluysa sonraki sayfaya gecer (widget hicbir zaman
     * kaybolmaz). Sonuc: her id icin hucre koordinatli placement.
     */
    fun solve(
        ids: Set<String>,
        saved: Map<String, WidgetPlacement>,
        grid: Grid
    ): Map<String, WidgetPlacement> {
        val boards = HashMap<Int, BooleanArray>()
        fun board(page: Int): BooleanArray = boards.getOrPut(page) { BooleanArray(grid.cols * grid.rows) }

        fun fits(b: BooleanArray, cx: Int, cy: Int, sx: Int, sy: Int): Boolean {
            if (cx < 0 || cy < 0 || cx + sx > grid.cols || cy + sy > grid.rows) return false
            for (y in cy until cy + sy) {
                for (x in cx until cx + sx) {
                    if (b[y * grid.cols + x]) return false
                }
            }
            return true
        }

        fun mark(b: BooleanArray, cx: Int, cy: Int, sx: Int, sy: Int) {
            for (y in cy until cy + sy) {
                for (x in cx until cx + sx) {
                    b[y * grid.cols + x] = true
                }
            }
        }

        val result = linkedMapOf<String, WidgetPlacement>()
        val ordered = ids.sortedWith(
            compareBy<String>({ saved[it]?.page ?: 0 }, { saved[it]?.cellY ?: 0 }, { saved[it]?.cellX ?: 0 })
        )
        // Hucre olculeri anormal kucukse (olcu henuz gelmemis vb.) kayitli hucre alanlarina
        // guvenilmez; aksi halde span'ler tum sayfayi kaplayarak widget'lari sayfa basina
        // bir tane dusurur.
        val trustSavedCells = grid.cellWidth >= 16f && grid.cellHeight >= 16f
        for (id in ordered) {
            val preferred = saved[id] ?: WidgetPlacement()
            val (baseSx, baseSy) = spanOf(WidgetIds.type(id), grid)
            // Tum sayfayi kaplayan kayitlar bozuk kabul edilir ve taban span ile yeniden turetilir.
            val savedUsable = trustSavedCells &&
                preferred.spanX > 0 && preferred.spanY > 0 &&
                preferred.spanX < grid.cols && preferred.spanY < grid.rows
            val spanX = if (savedUsable) preferred.spanX.coerceIn(1, grid.cols) else baseSx
            val spanY = if (savedUsable) preferred.spanY.coerceIn(1, grid.rows) else baseSy

            // Hucre alani olmayan kayitlar (serbest moddan gelen, yeni eklenen veya bozuk
            // kayit) icin eski dp konumu en yakin hucreye cevrilir; boylece serbest moddan
            // grid moduna geciste widget'lar bulunduklari yerde kalir, tepeye yigilmaz.
            val prefCellX = if (savedUsable) {
                preferred.cellX.coerceIn(0, grid.cols - spanX)
            } else {
                (preferred.dx / grid.cellWidth).roundToInt().coerceIn(0, grid.cols - spanX)
            }
            val prefCellY = if (savedUsable) {
                preferred.cellY.coerceIn(0, grid.rows - spanY)
            } else {
                (preferred.dy / grid.cellHeight).roundToInt().coerceIn(0, grid.rows - spanY)
            }
            val prefPage = preferred.page.coerceAtLeast(0)

            var placedPage = -1
            var placedX = 0
            var placedY = 0
            // Tercih edilen sayfada tam istenen hucre bossa doğrudan oraya yerlesir.
            // Sayfa gecisi veya tasimada widget'in baska bir bosluga savrulmasi engellenir.
            val prefBoard = board(prefPage)
            if (fits(prefBoard, prefCellX, prefCellY, spanX, spanY)) {
                placedPage = prefPage
                placedX = prefCellX
                placedY = prefCellY
                mark(prefBoard, placedX, placedY, spanX, spanY)
            } else {
                // Eger tam o hucre doluysa, oncelikle AYNI sayfada en yakin bosluk aranir.
                val samePageCells = sequence {
                    for (y in 0 until grid.rows) {
                        for (x in 0 until grid.cols) {
                            yield(x to y)
                        }
                    }
                }.sortedBy { (x, y) ->
                    val dx = x - prefCellX
                    val dy = y - prefCellY
                    dx * dx + dy * dy
                }
                val samePageSpot = samePageCells.firstOrNull { (x, y) -> fits(prefBoard, x, y, spanX, spanY) }
                if (samePageSpot != null) {
                    placedPage = prefPage
                    placedX = samePageSpot.first
                    placedY = samePageSpot.second
                    mark(prefBoard, placedX, placedY, spanX, spanY)
                } else {
                    // Sayfada hic yer yoksa bile widget baska sayfaya atilmaz; ayni sayfada prefCell'e konur.
                    placedPage = prefPage
                    placedX = prefCellX
                    placedY = prefCellY
                }
            }
            // Sayfada hic yer yoksa bile widget ASLA kaybolmaz: tercih edilen sayfada,
            // prefCellX/prefCellY koordinatina zorla yerlesir (disari atilmaz).
            if (placedPage < 0) {
                placedPage = prefPage
                placedX = prefCellX
                placedY = prefCellY
            }

            val (originX, originY) = grid.originDp(placedX, placedY)
            result[id] = preferred.copy(
                page = placedPage,
                dx = originX,
                dy = originY,
                scale = 1f,
                cellX = placedX,
                cellY = placedY,
                spanX = spanX,
                spanY = spanY,
                widthScale = 1f,
                heightScale = 1f
            )
        }
        return result
    }}
