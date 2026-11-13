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
        grid: Grid,
        // Widget basina cozumleme kurali (resize C): saglayici bildiriminden gelen
        // tercih edilen span ve min/max sinirlar. null ise eski davranis (taban boyut).
        rules: ((String) -> WidgetSpanRule?)? = null
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

        /** Tercih edilen hucreye en yakin bos yer; yoksa null. */
        fun nearestFree(
            b: BooleanArray,
            fromX: Int,
            fromY: Int,
            sx: Int,
            sy: Int
        ): Pair<Int, Int>? = sequence {
            for (y in 0 until grid.rows) {
                for (x in 0 until grid.cols) {
                    yield(x to y)
                }
            }
        }.sortedBy { (x, y) ->
            val dx = x - fromX
            val dy = y - fromY
            dx * dx + dy * dy
        }.firstOrNull { (x, y) -> fits(b, x, y, sx, sy) }

        for (id in ordered) {
            val preferred = saved[id] ?: WidgetPlacement()
            val rule = rules?.invoke(id)
            val (baseSx, baseSy) = if (rule != null) {
                rule.defaultX.coerceIn(1, grid.cols) to rule.defaultY.coerceIn(1, grid.rows)
            } else {
                spanOf(WidgetIds.type(id), grid)
            }
            // Bozuk kayit: span eksi/sifir ya da tahtanin tamamini kaplayan deger (eski hatali
            // donemde yazilan "tum sayfa" span'leri). Tam genislik/yukseklik (tek eksen) ise
            // gecerli bir kullanici secimidir, bozuk sayilmaz.
            val savedUsable = trustSavedCells &&
                preferred.spanX > 0 && preferred.spanY > 0 &&
                !(preferred.spanX >= grid.cols && preferred.spanY >= grid.rows)
            // Saglayici sinirlari kayitli span'e de uygulanir: bir zamanlar sinir disi
            // yazilmis boyutlar (veya saglayicinin artik kabul etmedigi boyut) duzeltilir.
            val minX = rule?.limits?.minX?.coerceIn(1, grid.cols) ?: 1
            val minY = rule?.limits?.minY?.coerceIn(1, grid.rows) ?: 1
            val maxX = (rule?.limits?.maxX ?: grid.cols).coerceIn(minX, grid.cols)
            val maxY = (rule?.limits?.maxY ?: grid.rows).coerceIn(minY, grid.rows)
            val spanX = (if (savedUsable) preferred.spanX else baseSx).coerceIn(minX, maxX)
            val spanY = (if (savedUsable) preferred.spanY else baseSy).coerceIn(minY, maxY)

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

            // Yerlesim sirasi (Launcher davranisi):
            // 1) Tercih edilen sayfada tam istenen hucre bossa dogrudan oraya (savrulma yok).
            // 2) Degilse ayni sayfada en yakin bos hucre.
            // 3) O sayfa doluysa sonraki sayfalara tasinir (kalici ust uste binme olmaz).
            // 4) Hic yer yoksa tercih edilen hucreye zorlanir; widget ASLA kaybolmaz.
            var placedPage = -1
            var placedX = 0
            var placedY = 0
            val prefBoard = board(prefPage)
            val exactSpot = if (fits(prefBoard, prefCellX, prefCellY, spanX, spanY)) {
                prefCellX to prefCellY
            } else {
                nearestFree(prefBoard, prefCellX, prefCellY, spanX, spanY)
            }
            if (exactSpot != null) {
                placedPage = prefPage
                placedX = exactSpot.first
                placedY = exactSpot.second
                mark(prefBoard, placedX, placedY, spanX, spanY)
            } else {
                for (page in prefPage + 1..prefPage + ids.size) {
                    val b = board(page)
                    val spot = nearestFree(b, prefCellX, prefCellY, spanX, spanY) ?: continue
                    placedPage = page
                    placedX = spot.first
                    placedY = spot.second
                    mark(b, placedX, placedY, spanX, spanY)
                    break
                }
            }
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
