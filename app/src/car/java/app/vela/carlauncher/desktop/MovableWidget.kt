package app.vela.carlauncher.desktop

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

private const val SNAP_STEP_DP = 16f
private const val MIN_WIDTH_DP = 120f
private const val MIN_HEIGHT_DP = 60f
// Hucre modunda komsu cerceveler birbirine degmesin diye birakilan ic bosluk (dp).
private const val CELL_GAP_DP = 4f

/** Surukleme sirasinda hedeflenen hucre onizlemesi. ok=false: buraya birakilamaz. */
private data class DropPreview(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val ok: Boolean
)

/**
 * UmainLauncher tabanli serbest tasinabilir ve boyutlandirilabilir widget saricisi (MovableWidget).
 *
 * - dragViaHandle: true yapildiginda widget govdesi tum dokunmalari icerige birakir
 *   (muzik dugmeleri, tiklamalar, harita vb. kisisel dokunmalarini kaybetmez).
 * - Edit modunda: sol ust tutamac tasi, sag ust X kaldirir, sol alt gear ayarlari acar;
 *   boyutlandirma tutamaclari (kose/sag kenar/alt kenar) varsayilan gorunurdur.
 * - Widget'a dokunus veya uzun basma: Launcher tarzi popup menu (Boyutlandir ac/kapa,
 *   Ayarlar, Kaldir).
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun MovableWidget(
    placement: WidgetPlacement,
    resizable: Boolean = true,
    dragViaHandle: Boolean = true,
    showControls: Boolean = true,
    workspaceWidth: Float = Float.MAX_VALUE,
    workspaceHeight: Float = Float.MAX_VALUE,
    snapToGrid: Boolean = true,
    // Hucre grid modu: grid null degilse ve snap aciksa Launcher tarzi hucre yerlesimi
    // kullanilir; aksi halde serbest float mod (eski davranis) gecerlidir.
    grid: CellGrid.Grid? = null,
    minSpanX: Int = 1,
    minSpanY: Int = 1,
    // Saglayici bildiriminden gelen ust sinirlar (resize C): sinir disi boyuta buyutulemez.
    maxSpanX: Int = Int.MAX_VALUE,
    maxSpanY: Int = Int.MAX_VALUE,
    onEnterEdit: () -> Unit = {},
    onDragging: (Boolean) -> Unit = {},
    // Parmak kenarda beklenince hedef sayfa adimlar; bu callback SAF hesaplamadir:
    // (mevcut hedef, yon, genislik, yukseklik) -> gidilecek sayfa. Doner deger:
    //  - sayfa: gidilecek sayfa; dolu sayfalar atlanmis olabilir (fark > 1 => atlandi)
    //  - null : bu yonde hicbir sayfada yer yok -> adim yok, kirmizi cerceve
    //  - kaynak sayfa: araligin kenari, o yonde baska sayfa yok -> sessiz adim yok
    onPageEdge: (from: Int, direction: Int, spanX: Int, spanY: Int) -> Int? = { from, _, _, _ -> from },
    // Hedef sayfa kabul edildi: calisma alani o sayfayi gosterir (surukleme sirasinda
    // gorunur sayfa degisir, boylece kullanicinin bos alanlari gorebilir).
    onPageTarget: (Int) -> Unit = {},
    // Surukleme sirasinda widget'in gorunur sayfaya kaydirilma miktari (dp). Widget kendi
    // sayfasi icinde cizilmeye devam eder (surukleme durumu korunur); bu kaydirma yalnizca
    // gorseldir ve parmagin altinda yuzmesini saglar.
    pageShiftTarget: Float = 0f,
    onRemove: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    // true: birakma kabul edildi. false: hedefte yer yok, widget eski yerine doner.
    onCommit: (WidgetPlacement) -> Boolean,
    // Surukleme onizlemesi gecerli mi? (yesil/kirmizi gostergesi icin)
    canDrop: (page: Int, cellX: Int, cellY: Int, spanX: Int, spanY: Int) -> Boolean =
        { _, _, _, _, _ -> true },
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val currentPlacement by rememberUpdatedState(placement)
    val currentSnapToGrid by rememberUpdatedState(snapToGrid)
    val editing by rememberUpdatedState(showControls)
    val enterEdit by rememberUpdatedState(onEnterEdit)
    var dragging by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(0) }
    var dragPage by remember { mutableStateOf(placement.page) }
    // Edit modunda widget'a dokununca acilan Launcher tarzi popup menu. Boyutlandirma
    // tutamaclari edit modunda VARSAYILAN gorunurdur (arayuz kesfedilebilir olsun);
    // menuden kapatilabilir.
    var menuOpen by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(true) }
    // Boyutlandirma tutamaci su anda surukleniyor mu? Canli resize onizlemesi
    // (hayalet hucre + boyut rozeti + kirmizi cerceve) bunun icin gerekir.
    var resizeActive by remember { mutableStateOf(false) }
    // Son boyut adimi gecersizdi (sinir veya komsu widget): kisa kirmizi cerceve.
    var resizeBlocked by remember { mutableStateOf(false) }
    var blockedTick by remember { mutableIntStateOf(0) }
    val pageEdge by rememberUpdatedState(onPageEdge)
    val draggingCallback by rememberUpdatedState(onDragging)
    val dropAllowed by rememberUpdatedState(canDrop)
    val pageTarget by rememberUpdatedState(onPageTarget)
    // Widget composition'i surukleme sirasinda elden cikarilirsa (sayfa degisimi, edit modu
    // kapanmasi, widget silinmesi) ust katmandaki "surukleniyor" bayragi takili kalmasin;
    // aksi halde pager kalici olarak kilitlenir.
    DisposableEffect(Unit) {
        onDispose {
            if (dragging) draggingCallback(false)
        }
    }
    var dx by remember { mutableFloatStateOf(placement.dx) }
    var dy by remember { mutableFloatStateOf(placement.dy) }
    var widthScale by remember { mutableFloatStateOf(placement.widthScale) }
    var heightScale by remember { mutableFloatStateOf(placement.heightScale) }
    // Hucre modu span'i; resize tutamacindan canli guncellenir.
    var spanX by remember { mutableIntStateOf(placement.spanX.coerceAtLeast(1)) }
    var spanY by remember { mutableIntStateOf(placement.spanY.coerceAtLeast(1)) }
    // Reddedilen birakma sonrasi kisa sureli kirmizi cerceve (sozel bildirim yerine).
    var rejectFlash by remember { mutableStateOf(false) }
    // Hedef sayfa dolu oldugu icin kenar adimlamasi kilitlendi mi? (parmak kenardan
    // uzaklasinca tekrar denenir)
    var edgeBlocked by remember { mutableStateOf(false) }
    var widgetWidth by remember { mutableFloatStateOf(0f) }
    var widgetHeight by remember { mutableFloatStateOf(0f) }
    var initialBaseWidth by remember { mutableFloatStateOf(0f) }
    var initialBaseHeight by remember { mutableFloatStateOf(0f) }
    val latestGrid by rememberUpdatedState(grid)
    val latestMinSpanX by rememberUpdatedState(minSpanX)
    val latestMinSpanY by rememberUpdatedState(minSpanY)
    val latestMaxSpanX by rememberUpdatedState(maxSpanX)
    val latestMaxSpanY by rememberUpdatedState(maxSpanY)

    // Edit moda giriste boyutlandirma tutamaclari yeniden acilir; cikista menu kapatilir.
    var dragAccumulatorX by remember { mutableFloatStateOf(0f) }
    var dragAccumulatorY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(showControls) {
        if (showControls) {
            resizing = true
        } else {
            menuOpen = false
            resizing = true
        }
    }

    /** Grid modu: snap ayari aciksa ve grid bilgisi geldiyse hucre modeli kullanilir. */
    fun activeGrid(): CellGrid.Grid? = if (currentSnapToGrid) latestGrid else null

    // Kenarda bekleme ile sayfa adimlama (Launcher davranisi).
    //
    // DONGU: parmak kenarda kaldigi surece adimlama SURER. Onceki surumde her adimdan
    // sonra edge sifirlandigi icin kullanici 4. sayfadan 2. sayfaya gecince (dolu, kirmizi)
    // takili kaldi: parmak kimmadikca yeni adim tetiklenmiyordu. Artik parmak kenarda
    // durdugu muddetce ~520ms'de bir sonraki sayfaya gecer.
    //
    // Yon boyunca dolu sayfalar ATLANIR (ilk bos sayfaya kadar); atlanan varsa kirmizi
    // cerceve yanar. O yonde hicbir sayfada yer yoksa adim atlanmaz, kirmizi cerceve
    // yanar ve parmak kenardan cekilene kadar tekrar denenmez.
    LaunchedEffect(dragging) {
        if (!dragging) return@LaunchedEffect
        var dir = 0
        while (dragging) {
            val current = edge
            if (current == 0) {
                // Parmak kenarda degil: yeni kenara gelmesini bekle.
                dir = 0
                delay(90)
                continue
            }
            if (current != dir) {
                // Kenara yeni geldi: kullaniciya tepki suresi birak.
                dir = current
                delay(650)
                continue
            }
            val sx = spanX.coerceAtLeast(1)
            val sy = spanY.coerceAtLeast(1)
            val target = pageEdge(dragPage, dir, sx, sy)
            when {
                target == null -> {
                    // Yon boyunca hicbir sayfada bu boyutta yer yok: sayfaya gecme,
                    // kirmizi cerceveyle bildir. Parmak kenardan cekilince tekrar denenir.
                    rejectFlash = true
                    edge = 0
                    edgeBlocked = true
                    delay(500)
                }
                target == dragPage -> {
                    // Araligin kenari: o yonde baska sayfa yok. Kilitle ki dongu bosuna
                    // donmesin; parmak kenardan cekilince tekrar serbest kalir.
                    edge = 0
                    edgeBlocked = true
                    delay(400)
                }
                else -> {
                    // Birden fazla sayfa atlandiysa bunu kirmizi cerceveyle goster:
                    // kullanici atlanan sayfalarin dolu oldugunu anlasin.
                    if (kotlin.math.abs(target - dragPage) > 1) rejectFlash = true
                    dragPage = target
                    // Gorunur sayfa degisir; widget parmagin altinda kaldigi icin konumu
                    // degistirilmez (parmak hareketi yeni sayfanin koordinatlarinda surer).
                    pageTarget(target)
                    // Yeni sayfada da kenardayiz; gerekirse kaldigimiz yerden devam eder.
                    delay(520)
                }
            }
        }
    }

    LaunchedEffect(rejectFlash) {
        if (rejectFlash) {
            delay(900)
            rejectFlash = false
        }
    }

    // Gecersiz boyut adimi: cerceve kisa sure kirmizi yanip soner. Her yeni adim
    // sayaci artirdigi icin sure yeniden baslar (parmak sinirda beklerken cerceve
    // kirmizi kalir, buyume olmaya devam etmez).
    LaunchedEffect(blockedTick) {
        if (blockedTick == 0) return@LaunchedEffect
        resizeBlocked = true
        delay(700)
        resizeBlocked = false
    }

    LaunchedEffect(placement) {
        if (dragging) return@LaunchedEffect
        dragPage = placement.page
        dx = placement.dx
        dy = placement.dy
        widthScale = placement.widthScale
        heightScale = placement.heightScale
        spanX = placement.spanX.takeIf { it > 0 } ?: 1
        spanY = placement.spanY.takeIf { it > 0 } ?: 1
    }

    val baseWidth = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
    val baseHeight = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
    val gridForCompose = activeGrid()
    val currentPixelWidth = if (gridForCompose != null) {
        (spanX * gridForCompose.cellWidth).coerceAtLeast(MIN_WIDTH_DP)
    } else {
        (baseWidth * widthScale).coerceAtLeast(MIN_WIDTH_DP)
    }
    val currentPixelHeight = if (gridForCompose != null) {
        (spanY * gridForCompose.cellHeight).coerceAtLeast(MIN_HEIGHT_DP)
    } else {
        (baseHeight * heightScale).coerceAtLeast(MIN_HEIGHT_DP)
    }
    // Hucre modunda cerceveler birbirine degmesin: hucre alaninin icine kucuk bir bosluk girilir.
    val cellGap = if (gridForCompose != null) CELL_GAP_DP else 0f
    val frameWidth = (currentPixelWidth - cellGap * 2f).coerceAtLeast(1f)
    val frameHeight = (currentPixelHeight - cellGap * 2f).coerceAtLeast(1f)
    // Sayfa degisiminde widget parmagin altinda kalsin: kendi sayfasi icinde cizilmeye devam
    // eder (surukleme durumu korunur) ama gorunur sayfaya gorsel olarak kaydirilir. Pager'in
    // kendi yay animasyonuyla benzer davranir ki iki hareket ust uste binmesin.
    val pageShift by animateFloatAsState(
        targetValue = pageShiftTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    val renderX = (dx + cellGap).coerceIn(0f, (workspaceWidth - frameWidth).coerceAtLeast(0f)) + pageShift
    val renderY = (dy + cellGap).coerceIn(0f, (workspaceHeight - frameHeight).coerceAtLeast(0f))
    // Serbest (grid'siz) modda icerik taban olcusunde cizilir ve olceklenir.
    // Hucre modunda olcekleme YAPILMAZ: icerige gercek cerceve olcusu verilir ki widget
    // yeni boyuta gore yeniden yerlessin (buyuk cerceve + kucuk/bulanik icerik bitisi).
    val contentScale = minOf(widthScale, heightScale)

    // Surukleme VEYA boyutlandirma sirasinda hedeflenen hucre ve buraya
    // birakilip birakilamayacagi (canli geri bildirim).
    val previewActive = gridForCompose != null && (dragging || resizeActive)
    val dropPreview = if (previewActive && gridForCompose != null) {
        val sx = spanX.coerceIn(1, gridForCompose.cols)
        val sy = spanY.coerceIn(1, gridForCompose.rows)
        val cx = (dx / gridForCompose.cellWidth).roundToInt().coerceIn(0, gridForCompose.cols - sx)
        val cy = (dy / gridForCompose.cellHeight).roundToInt().coerceIn(0, gridForCompose.rows - sy)
        DropPreview(cx, cy, sx, sy, dropAllowed(dragPage, cx, cy, sx, sy))
    } else null
    // Kirmizi cerceve: hedefte yer yok (suruklerken/boyutlandirirken), son boyut adimi
    // gecersizdi veya birakma reddedildi (kisa yanip sonme).
    val blocked = rejectFlash || resizeBlocked || (dropPreview != null && !dropPreview.ok)

    /** Basarisiz birakmayi geri al: widget eski kayitli yerine/sayfasina doner. */
    fun revertToPlacement() {
        dx = currentPlacement.dx
        dy = currentPlacement.dy
        widthScale = currentPlacement.widthScale
        heightScale = currentPlacement.heightScale
        spanX = currentPlacement.spanX.takeIf { it > 0 } ?: 1
        spanY = currentPlacement.spanY.takeIf { it > 0 } ?: 1
        val originPage = currentPlacement.page
        if (dragPage != originPage) {
            // Gorunur sayfayi widget'in geri dondugu sayfaya getir; aksi halde kullanici
            // bos bir sayfaya bakarken widget baska sayfada kalirdi.
            dragPage = originPage
            pageTarget(originPage)
        }
        edgeBlocked = false
        // Kirmizi cerceve yanip soner: "buraya yerlesemedi" bildirimi.
        rejectFlash = true
    }

    fun commitSnapped() {
        val g = activeGrid()
        if (g != null) {
            // Hucre modu: konumu ve boyutu hucre agina oturt; span'i kaydet.
            val sx = spanX.coerceIn(latestMinSpanX, minOf(g.cols, latestMaxSpanX).coerceAtLeast(latestMinSpanX))
            val sy = spanY.coerceIn(latestMinSpanY, minOf(g.rows, latestMaxSpanY).coerceAtLeast(latestMinSpanY))
            val cx = (dx / g.cellWidth).roundToInt().coerceIn(0, g.cols - sx)
            val cy = (dy / g.cellHeight).roundToInt().coerceIn(0, g.rows - sy)
            val snappedDx = cx * g.cellWidth
            val snappedDy = cy * g.cellHeight
            dx = snappedDx
            dy = snappedDy
            spanX = sx
            spanY = sy
            val accepted = onCommit(currentPlacement.copy(
                page = dragPage,
                dx = snappedDx,
                dy = snappedDy,
                scale = 1f,
                widthScale = 1f,
                heightScale = 1f,
                cellX = cx,
                cellY = cy,
                spanX = sx,
                spanY = sy
            ))
            if (!accepted) revertToPlacement()
        } else {
            val snappedDx = (if (currentSnapToGrid) (dx / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dx)
                .coerceIn(0f, (workspaceWidth - currentPixelWidth).coerceAtLeast(0f))
            val snappedDy = (if (currentSnapToGrid) (dy / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP else dy)
                .coerceIn(0f, (workspaceHeight - currentPixelHeight).coerceAtLeast(0f))
            dx = snappedDx
            dy = snappedDy
            val accepted = onCommit(currentPlacement.copy(
                page = dragPage,
                dx = snappedDx,
                dy = snappedDy,
                scale = minOf(widthScale, heightScale),
                widthScale = widthScale,
                heightScale = heightScale
            ))
            if (!accepted) revertToPlacement()
        }
    }

    fun finishDrag() {
        dragging = false
        edge = 0
        edgeBlocked = false
        commitSnapped()
        draggingCallback(false)
    }

    fun moveBy(x: Float, y: Float) {
        dx = (dx + x / density).coerceIn(0f, (workspaceWidth - currentPixelWidth).coerceAtLeast(0f))
        dy = (dy + y / density).coerceIn(0f, (workspaceHeight - currentPixelHeight).coerceAtLeast(0f))
        val nextEdge = when {
            dx < 12f -> -1
            dx + currentPixelWidth > workspaceWidth - 12f -> 1
            else -> 0
        }
        // Kenardan uzaklasinca adimlama kilidi (dolu sayfa) yeniden denenebilir.
        if (nextEdge == 0) edgeBlocked = false
        edge = if (edgeBlocked) 0 else nextEdge
    }

    Box(
        modifier = modifier
            .offset(renderX.dp, renderY.dp)
            .requiredSize(frameWidth.dp, frameHeight.dp)
            .pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!editing) {
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            val held = pointer != null && pointer.pressed &&
                                (pointer.position - down.position).getDistance() < viewConfiguration.touchSlop
                        } while (held)
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    enterEdit()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        val held = event.changes.any { it.pressed }
                    } while (held)
                }
            }
        }
    }) {
        // B: hucre modunda icerik gercek cerceve olcusu ile olculur ve olceklenmez;
        // boylece widget yeni boyuta gore yeniden yerlesir. Serbest (grid'siz) modda
        // eski davranis korunur: icerik taban olcusunde cizilip olceklenir.
        val contentModifier = if (gridForCompose != null) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        }
        Box(
            modifier = contentModifier.onSizeChanged {
                if (it.width > 0 && it.height > 0) {
                    val w = it.width / density
                    val h = it.height / density
                    if (initialBaseWidth <= 0f) {
                        initialBaseWidth = w
                        widgetWidth = w
                    }
                    if (initialBaseHeight <= 0f) {
                        initialBaseHeight = h
                        widgetHeight = h
                    }
                }
            }
        ) {
            content()
        }

        if (showControls) {
            // Hucre modunda surukleme/boyutlandirma sirasinda hedef hucre bolgesi
            // vurgulanir (Launcher davranisi).
            val previewGrid = if (previewActive) gridForCompose else null
            val preview = dropPreview
            if (previewGrid != null && preview != null) {
                // Yer varsa camgobegi "buraya oturabilir" cercevesi; yer yoksa kirmizi.
                val previewFill = if (preview.ok) Color(0x2200E5FF) else Color(0x33FF5252)
                val previewLine = if (preview.ok) Color(0x8800E5FF) else Color(0xCCFF5252)
                Box(
                    Modifier
                        .offset((preview.cellX * previewGrid.cellWidth - dx).dp, (preview.cellY * previewGrid.cellHeight - dy).dp)
                        .requiredSize(
                            (preview.spanX * previewGrid.cellWidth - cellGap * 2f).coerceAtLeast(1f).dp,
                            (preview.spanY * previewGrid.cellHeight - cellGap * 2f).coerceAtLeast(1f).dp
                        )
                        .background(previewFill, RoundedCornerShape(10.dp))
                        .border(1.dp, previewLine, RoundedCornerShape(10.dp))
                )
            }
            // Boyut rozeti: hedef hucre boyutu (ornek "3x2"). Kirmizi ise bu olcuye
            // yerlesilemiyor (ekran kenari veya komsu widget engeli).
            if (previewGrid != null && preview != null) {
                Surface(
                    color = if (preview.ok) Color(0xE6141624) else Color(0xE6B3261E),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Ust kenar resize tutamaci ortada durur; rozet onun sagina kayar.
                        .offset(x = 34.dp, y = (-13).dp)
                        .zIndex(20f)
                ) {
                    Text(
                        text = "${preview.spanX}x${preview.spanY}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    // Cerceve rengi ayni zamanda bildirimdir: kirmizi = buraya birakilamaz.
                    .background(if (blocked) Color(0x2EFF5252) else Color.Transparent, RoundedCornerShape(10.dp))
                    .border(
                        width = if (dragging || blocked) 2.dp else 1.dp,
                        color = when {
                            blocked -> Color(0xFFE53935)
                            dragging -> Color(0xFF00E5FF)
                            else -> Color(0xAA00E5FF)
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true; draggingCallback(true) },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() }
                        ) { change, drag ->
                            change.consume()
                            moveBy(drag.x, drag.y)
                        }
                    }
                    .pointerInput(editing) {
                        // Edit modunda kisa dokunus veya uzun basma popup menuyu acar;
                        // surukleme drag dedektoru tarafindan tuketildigi icin tap olarak algilanmaz.
                        if (editing) {
                            detectTapGestures(
                                onTap = { menuOpen = true },
                                onLongPress = { menuOpen = true }
                            )
                        }
                    }
            )
            if (dragViaHandle) {
                HandleSurface(
                    icon = Icons.Default.OpenWith,
                    description = "Tasi",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        // 28dp tutamacin yarisi: cizgi uzerine ortalanir.
                        .offset((-14).dp, (-14).dp)
                        .zIndex(10f)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { dragging = true; draggingCallback(true) },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() }
                            ) { change, drag ->
                                change.consume()
                                moveBy(drag.x, drag.y)
                            }
                        }
                )
            }

            if (onRemove != null) {
                HandleSurface(
                    icon = Icons.Default.Close,
                    description = "Kaldir",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(14.dp, (-14).dp)
                        .zIndex(10f)
                        .clickable { onRemove() }
                )
            }

            if (onRemove != null || onSettings != null || resizable) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (resizable) {
                        DropdownMenuItem(
                            text = { Text(if (resizing) "Boyutlandirmayi bitir" else "Boyutlandir") },
                            onClick = { resizing = !resizing; menuOpen = false }
                        )
                    }
                    if (onSettings != null) {
                        DropdownMenuItem(
                            text = { Text("Ayarlar") },
                            onClick = { menuOpen = false; onSettings() }
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Kaldir") },
                            onClick = { menuOpen = false; onRemove() }
                        )
                    }
                }
            }

            if (resizable && resizing) {
                fun baseDims(): Pair<Float, Float> {
                    val baseW = (if (initialBaseWidth > 0f) initialBaseWidth else widgetWidth).coerceAtLeast(MIN_WIDTH_DP)
                    val baseH = (if (initialBaseHeight > 0f) initialBaseHeight else widgetHeight).coerceAtLeast(MIN_HEIGHT_DP)
                    return baseW to baseH
                }
                // Serbest mod: olcek yerine resulting boyut 16dp'nin katina yuvarlanir.
                // Sinir asiminda clamp devreye girer. Tamamen serbest modda (16dp kilavuzu
                // kapali) yuvarlama yapilmaz; boyut surekli degisir.
                fun snapScale(base: Float, raw: Float, minS: Float, maxS: Float): Float {
                    if (!currentSnapToGrid) return raw.coerceIn(minS, maxS)
                    val snapped = ((base * raw / SNAP_STEP_DP).roundToInt() * SNAP_STEP_DP) / base
                    return snapped.coerceIn(minS, maxS)
                }
                // Hucre modunda span degisimi. Sinirlar: min span, saglayicinin max span'i
                // (maxSpanX/Y) ve tutamacin kenarina kalan hucre sayisi. Tutamacin kenari
                // onemlidir: sol/ust tutamac sabit kenarin tersine buyutur, yani span artarken
                // hucre koordinati ve dx/dy azalir (kenara yapisik widget da buyuyebilir).
                // Birikimli delta yalnizca ilgili eksende toplanir; boylece kullanilmayan
                // eksendeki artik deger sonraki bir resize'ta span'i ziplatmaz.
                // Gecersiz adim (komsu widget veya min/sinir asmasi) UYGULANMAZ: cerceve
                // kirmizi yanar ve boyut son gecersiz kilmadan onceki degerinde kalir.
                fun resizeSpan(anchor: ResizeAnchor, dragX: Float, dragY: Float) {
                    val g = activeGrid() ?: return
                    val cellXNow = (dx / g.cellWidth).roundToInt().coerceIn(0, (g.cols - 1).coerceAtLeast(0))
                    val cellYNow = (dy / g.cellHeight).roundToInt().coerceIn(0, (g.rows - 1).coerceAtLeast(0))

                    val stepX = (g.cellWidth * 0.25f).coerceIn(16f, 32f)
                    val stepY = (g.cellHeight * 0.25f).coerceIn(16f, 32f)

                    /** Aday yerlesim bu sayfada uygulanabilir mi? (komsu cakismasi yok mu) */
                    fun fits(cx: Int, cy: Int, sx: Int, sy: Int): Boolean =
                        cx >= 0 && cy >= 0 && sx in 1..g.cols && sy in 1..g.rows &&
                            dropAllowed(dragPage, cx, cy, sx, sy)

                    /** Sol/ust tutamakta parmagin ters yonu buyutme yonudur. */
                    fun outwardX(value: Float): Float =
                        (value / density) * (if (anchor.growLeft) -1f else 1f)

                    fun outwardY(value: Float): Float =
                        (value / density) * (if (anchor.growTop) -1f else 1f)

                    /** Genislik adimi (+1 buyur, -1 kuculur); gecersizse kirmizi yanip soner. */
                    fun stepWidth(grow: Int) {
                        val nextSpan = spanX + grow
                        val nextCellX = if (anchor.growLeft) cellXNow - grow else cellXNow
                        // Sag/alt tutamakta sag kenar, sol tutamakta sol kenar (0) hedef:
                        val limit = if (anchor.growLeft) cellXNow + spanX else g.cols - cellXNow
                        val cap = minOf(limit, latestMaxSpanX)
                        if (nextSpan < latestMinSpanX || nextSpan > cap || nextCellX < 0 ||
                            !fits(nextCellX, cellYNow, nextSpan, spanY)
                        ) {
                            blockedTick++
                        } else {
                            spanX = nextSpan
                            if (anchor.growLeft) dx = nextCellX * g.cellWidth
                        }
                    }

                    /** Yukseklik adimi (+1 buyur, -1 kuculur); gecersizse kirmizi yanip soner. */
                    fun stepHeight(grow: Int) {
                        val nextSpan = spanY + grow
                        val nextCellY = if (anchor.growTop) cellYNow - grow else cellYNow
                        val limit = if (anchor.growTop) cellYNow + spanY else g.rows - cellYNow
                        val cap = minOf(limit, latestMaxSpanY)
                        if (nextSpan < latestMinSpanY || nextSpan > cap || nextCellY < 0 ||
                            !fits(cellXNow, nextCellY, spanX, nextSpan)
                        ) {
                            blockedTick++
                        } else {
                            spanY = nextSpan
                            if (anchor.growTop) dy = nextCellY * g.cellHeight
                        }
                    }

                    when (anchor.axis) {
                        ResizeAxis.WIDTH -> {
                            dragAccumulatorX += outwardX(dragX)
                            if (kotlin.math.abs(dragAccumulatorX) >= stepX) {
                                val grow = if (dragAccumulatorX > 0) 1 else -1
                                dragAccumulatorX = 0f
                                stepWidth(grow)
                            }
                        }
                        ResizeAxis.HEIGHT -> {
                            dragAccumulatorY += outwardY(dragY)
                            if (kotlin.math.abs(dragAccumulatorY) >= stepY) {
                                val grow = if (dragAccumulatorY > 0) 1 else -1
                                dragAccumulatorY = 0f
                                stepHeight(grow)
                            }
                        }
                        ResizeAxis.BOTH -> {
                            dragAccumulatorX += outwardX(dragX)
                            dragAccumulatorY += outwardY(dragY)
                            val cellsX = kotlin.math.abs(dragAccumulatorX) / stepX
                            val cellsY = kotlin.math.abs(dragAccumulatorY) / stepY
                            // Baskin eksen: parmak iki eksende de kaysa bile once hangi
                            // eksende bir hucre dolmussa yalnizca o eksen degisir. Boylece
                            // dikey cekerken genislik istemsiz 1 hucre kaymaz.
                            if (cellsX < 1f && cellsY < 1f) return
                            if (cellsX >= cellsY) {
                                val growX = if (dragAccumulatorX > 0) 1 else -1
                                dragAccumulatorX = 0f
                                stepWidth(growX)
                            } else {
                                val growY = if (dragAccumulatorY > 0) 1 else -1
                                dragAccumulatorY = 0f
                                stepHeight(growY)
                            }
                        }
                    }
                }

                // Serbest (hucre disi) modda olcek degisimi. Sol/ust tutamakta sabit kenar
                // sag/alt kenar oldugu icin ofset de geri kaydirilir.
                fun resizeScale(anchor: ResizeAnchor, dragX: Float, dragY: Float) {
                    val (baseW, baseH) = baseDims()
                    val minWScale = maxOf(PlacementBounds.MIN_SCALE, MIN_WIDTH_DP / baseW)
                    val minHScale = maxOf(PlacementBounds.MIN_SCALE, MIN_HEIGHT_DP / baseH)
                    when (anchor.axis) {
                        ResizeAxis.WIDTH -> {
                            if (anchor.growLeft) {
                                val rightEdge = dx + baseW * widthScale
                                val maxScale = minOf(
                                    PlacementBounds.MAX_SCALE,
                                    (rightEdge / baseW).coerceAtLeast(minWScale)
                                )
                                val next = snapScale(baseW, widthScale - (dragX / density) / baseW, minWScale, maxScale)
                                widthScale = next
                                dx = (rightEdge - baseW * next).coerceIn(
                                    0f,
                                    (workspaceWidth - baseW * next).coerceAtLeast(0f)
                                )
                            } else {
                                val maxScale = minOf(
                                    PlacementBounds.MAX_SCALE,
                                    (workspaceWidth - dx).coerceAtLeast(baseW) / baseW
                                )
                                widthScale = snapScale(baseW, widthScale + (dragX / density) / baseW, minWScale, maxScale)
                            }
                        }
                        ResizeAxis.HEIGHT -> {
                            if (anchor.growTop) {
                                val bottomEdge = dy + baseH * heightScale
                                val maxScale = minOf(
                                    PlacementBounds.MAX_SCALE,
                                    (bottomEdge / baseH).coerceAtLeast(minHScale)
                                )
                                val next = snapScale(baseH, heightScale - (dragY / density) / baseH, minHScale, maxScale)
                                heightScale = next
                                dy = (bottomEdge - baseH * next).coerceIn(
                                    0f,
                                    (workspaceHeight - baseH * next).coerceAtLeast(0f)
                                )
                            } else {
                                val maxScale = minOf(
                                    PlacementBounds.MAX_SCALE,
                                    (workspaceHeight - dy).coerceAtLeast(baseH) / baseH
                                )
                                heightScale = snapScale(baseH, heightScale + (dragY / density) / baseH, minHScale, maxScale)
                            }
                        }
                        ResizeAxis.BOTH -> {
                            val minCommonScale = maxOf(minWScale, minHScale)
                            val maxWScale = minOf(PlacementBounds.MAX_SCALE, (workspaceWidth - dx).coerceAtLeast(baseW) / baseW)
                            val maxHScale = minOf(PlacementBounds.MAX_SCALE, (workspaceHeight - dy).coerceAtLeast(baseH) / baseH)
                            val maxCommonScale = minOf(maxWScale, maxHScale).coerceAtLeast(minCommonScale)
                            val delta = ((dragX / density) / baseW + (dragY / density) / baseH) / 2f
                            val newScale = snapScale(baseW, widthScale + delta, minCommonScale, maxCommonScale)
                            widthScale = newScale
                            heightScale = newScale
                        }
                    }
                }

                /** Tutamac hareketi: hucre modu span, serbest mod olcek kullanir. */
                fun applyResize(anchor: ResizeAnchor, dragX: Float, dragY: Float) {
                    if (activeGrid() != null) resizeSpan(anchor, dragX, dragY) else resizeScale(anchor, dragX, dragY)
                }

                fun beginResize() {
                    resizeActive = true
                    dragAccumulatorX = 0f
                    dragAccumulatorY = 0f
                }

                fun endResize() {
                    resizeActive = false
                    commitSnapped()
                }

                // Sag alt kose: en + boy birlikte (Launcher tarzi dolu daire / fill circle tutamac).
                ResizeHandle(
                    description = "Boyutlandir",
                    anchor = ResizeAnchor.BOTTOM_END,
                    sizeDp = 24.dp,
                    onStart = ::beginResize,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Dokunma kutusu 44dp; yarisi (22dp) kadar kaysin ki beyaz daire
                        // cerceve cizgisinin tam ortada dursun.
                        .offset(22.dp, 22.dp)
                        .zIndex(20f),
                    onResize = ::applyResize,
                    onEnd = ::endResize
                )
                // Sag kenar ortasi: yalnizca genislik (Launcher tarzi dolu daire).
                ResizeHandle(
                    description = "Genislik",
                    anchor = ResizeAnchor.END,
                    sizeDp = 20.dp,
                    onStart = ::beginResize,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(22.dp, 0.dp)
                        .zIndex(20f),
                    onResize = ::applyResize,
                    onEnd = ::endResize
                )
                // Sol kenar ortasi: sabit sag kenardan buyutur. Kenara yapisik widget'lar
                // boylece genisleyebilir (eskiden maxSpanX = cols - cellX yuzunden kilitleyenirlerdi).
                ResizeHandle(
                    description = "Genislik (soldan)",
                    anchor = ResizeAnchor.START,
                    sizeDp = 20.dp,
                    onStart = ::beginResize,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset((-22).dp, 0.dp)
                        .zIndex(20f),
                    onResize = ::applyResize,
                    onEnd = ::endResize
                )
                // Alt kenar ortasi: yalnizca yukseklik (Launcher tarzi dolu daire).
                ResizeHandle(
                    description = "Yukseklik",
                    anchor = ResizeAnchor.BOTTOM,
                    sizeDp = 20.dp,
                    onStart = ::beginResize,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(0.dp, 22.dp)
                        .zIndex(20f),
                    onResize = ::applyResize,
                    onEnd = ::endResize
                )
                // Ust kenar ortasi: sabit alt kenardan asagiya/ yukariya buyutur.
                ResizeHandle(
                    description = "Yukseklik (ustten)",
                    anchor = ResizeAnchor.TOP,
                    sizeDp = 20.dp,
                    onStart = ::beginResize,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(0.dp, (-22).dp)
                        .zIndex(20f),
                    onResize = ::applyResize,
                    onEnd = ::endResize
                )
            }

            if (onSettings != null) {
                HandleSurface(
                    icon = Icons.Default.Settings,
                    description = "Widget ayarlari",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset((-14).dp, 14.dp)
                        .zIndex(10f)
                        .clickable { onSettings() }
                )
            }
        }
    }
}

@Composable
private fun HandleSurface(
    icon: ImageVector,
    description: String,
    rotation: Float = 0f,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xCC1A1D2E),
        contentColor = Color(0xFF00E5FF),
        shape = CircleShape,
        shadowElevation = 4.dp,
        modifier = modifier.size(28.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

/** Resize tutamacinin hangi eksen(ler)i etkiledigi. */
private enum class ResizeAxis { WIDTH, HEIGHT, BOTH }

/**
 * Tutamacin hangi kenarda durdugu. Sol/ust tutamaklarda sabit kenar sag/alt kenardir:
 * buyurken span artar, hucre koordinati (ve dx/dy) azalir. Boylece `maxSpanX = cols - cellX`
 * siniri yuzunden kenara yapisik widget'lar da buyuyebilir (resize D).
 */
private enum class ResizeAnchor(
    val axis: ResizeAxis,
    val growLeft: Boolean = false,
    val growTop: Boolean = false
) {
    BOTTOM_END(ResizeAxis.BOTH),
    END(ResizeAxis.WIDTH),
    START(ResizeAxis.WIDTH, growLeft = true),
    BOTTOM(ResizeAxis.HEIGHT),
    TOP(ResizeAxis.HEIGHT, growTop = true)
}

/**
 * Suruklenerek boyutlandirma tutamacı (Launcher tarzi dolu daire / fill circle).
 * Drag px cinsinden onResize'a iletilir.
 * Birakildiginda onEnd ile grid snap'li commit yapilir.
 */
@Composable
private fun ResizeHandle(
    description: String,
    anchor: ResizeAnchor,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    onStart: () -> Unit = {},
    onResize: (ResizeAnchor, Float, Float) -> Unit,
    onEnd: () -> Unit
) {
    // Dokunma alanini artirmak icin en az 44dp'lik dokunma hedefi (touch target)
    val touchTargetSize = maxOf(44.dp, sizeDp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touchTargetSize)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onStart() },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() }
                ) { change, drag ->
                    change.consume()
                    onResize(anchor, drag.x, drag.y)
                }
            }
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF141624)),
            modifier = Modifier.size(sizeDp)
        ) {
            // Beyaz Dolu daire (Fill circle): Icine ikon konulmaz, konumu yonunu bildirir.
            Box(modifier = Modifier.size(sizeDp))
        }
    }
}
