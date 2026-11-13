package app.vela.carlauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import app.vela.ui.VelaWindowAlertDialog as AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import app.vela.carlauncher.desktop.CarAppWidgetHostController
import app.vela.carlauncher.desktop.CellGrid
import app.vela.carlauncher.desktop.DesktopPlacementEngine
import app.vela.carlauncher.desktop.DesktopWidgetLayoutStore
import app.vela.carlauncher.desktop.HostedAppWidgetView
import app.vela.carlauncher.desktop.LocalCarAppWidgetHost
import app.vela.carlauncher.desktop.MovableWidget
import app.vela.carlauncher.desktop.StatusWidgetView
import app.vela.carlauncher.desktop.WidgetIds
import app.vela.carlauncher.desktop.WidgetPlacement
import app.vela.carlauncher.desktop.WidgetSpanLimits
import app.vela.carlauncher.desktop.WidgetSpanSizing
import app.vela.carlauncher.desktop.spanRuleFor
import app.vela.carlauncher.desktop.WorkspaceGrid
import app.vela.carlauncher.settings.CarLauncherSettings
import app.vela.carlauncher.apps.CarAppManager
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.InternalApp
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val LocalDesktopCardColor = staticCompositionLocalOf { Color(0xF0141624) }
private val ClCardBg: Color
    @Composable get() = LocalDesktopCardColor.current
private val ClBorder = Color(0x33FFFFFF)
private val ClPrimary = Color(0xFF00E5FF)
private val ClSuccess = Color(0xFF30D158)

/**
 * UmainLauncher Tabanli Tam Kapsamli Masaustu (CarDesktopWorkspaceView).
 * - Yalnizca tutamac ile tasima (govde dokunmalari %100 serbest, kazara tasima yok).
 * - Sistem Duvar Kagidi destegi (windowShowWallpaper ve transparan zemin).
 * - Kullanicinin 7-8 araba widget'inin hepsi (Hiz, Saat, Dashboard, Muzik, Hava, Pusula, OBD, Durum, Dock).
 * - Sistem AppWidget secici ("Choose widget" - cihazdaki tum widget'lari gorsel listeleyen secici).
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarDesktopWorkspaceView(
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onKapat: (() -> Unit)? = null,
    onLaunchApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val layoutStore = remember { DesktopWidgetLayoutStore.getInstance(context) }
    val layout by layoutStore.layout.collectAsState()
    val activeWidgets by layoutStore.activeWidgets.collectAsState()
    val pageCount by layoutStore.pageCount.collectAsState()
    val snapToGrid by CarLauncherSettings.workspaceSnapToGrid.collectAsState()
    val swipeThresholdFraction by CarLauncherSettings.workspaceSwipeThreshold.collectAsState()
    val indicatorSeconds by CarLauncherSettings.workspaceIndicatorSeconds.collectAsState()
    // Perf: hiz telemetrisi ve medya durumu sik degisir; workspace kokunde okunurlarsa
    // tum pager ve widget'lar recompose olurdu. rememberUpdatedState ile deger yalnizca
    // icerigini cizen widget lambda'sinda okunur; recompose o widget'la sinirli kalir.
    val latestTelemetri by rememberUpdatedState(telemetri)
    val latestMedya by rememberUpdatedState(medya)

    // Sistem Duvar Kagidinin arkadan gorunebilmesi icin Activity pencere bayragi
    LaunchedEffect(Unit) {
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    }

    val hostController = remember { CarAppWidgetHostController(context) }
    DisposableEffect(Unit) {
        hostController.startListening()
        onDispose {
            hostController.stopListening()
        }
    }

    var isEditMode by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showPageDeleteConfirm by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var draggingWidgetId by remember { mutableStateOf<String?>(null) }
    var selectedWidgetId by remember { mutableStateOf<String?>(null) }
    var showPageIndicator by remember { mutableStateOf(true) }
    var lastPageChangeAt by remember { mutableStateOf(0L) }

    var saatMetni by remember { mutableStateOf("12:00") }
    var tarihMetni by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val saatFormati = SimpleDateFormat("HH:mm", Locale.getDefault())
        val tarihFormati = SimpleDateFormat("d MMMM EEEE", Locale.getDefault())
        var sonSaat = ""
        var sonTarih = ""
        while (true) {
            val simdi = Date()
            // Ayni dakika icinde state yazma: her saniye tum kompozisyonu recompose ederdi.
            val saat = saatFormati.format(simdi)
            if (saat != sonSaat) { sonSaat = saat; saatMetni = saat }
            val tarih = tarihFormati.format(simdi)
            if (tarih != sonTarih) { sonTarih = tarih; tarihMetni = tarih }
            delay(1000L)
        }
    }

    // AppWidgetHost yapilandirma launcher'i
    var pendingAppWidgetId by remember { mutableStateOf(-1) }
    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (id != -1) {
            if (result.resultCode == Activity.RESULT_OK) {
                val widgetKey = "${WidgetIds.AW_PREFIX}$id"
                layoutStore.addWidget(widgetKey, WidgetPlacement(dx = 60f, dy = 160f, scale = 1.0f, page = currentPage))
            } else {
                hostController.deleteId(id)
            }
        }
    }
    fun finishWidgetBinding(id: Int) {
        val info = hostController.info(id)
        if (info == null) {
            hostController.deleteId(id)
        } else if (info.configure != null) {
            pendingAppWidgetId = id
            configureLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            })
        } else {
            layoutStore.addWidget("${WidgetIds.AW_PREFIX}$id", WidgetPlacement(60f, 160f, 1f, currentPage))
        }
    }
    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingAppWidgetId
        pendingAppWidgetId = -1
        if (id >= 0 && result.resultCode == Activity.RESULT_OK) finishWidgetBinding(id)
        else if (id >= 0) hostController.deleteId(id)
    }

    CompositionLocalProvider(LocalCarAppWidgetHost provides hostController) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isEditMode = true
                        }
                    )
                }
        ) {
            // BoxWithConstraints maxWidth/maxHeight zaten dp cinsindendir; density'ye bolmek
            // calisma alanini kuculturdu (widget'larin tek sayfaya dusmesinin ve ust uste
            // binmesinin kok nedeni buydu). Yerlesim matematigi (SNAP_STEP_DP, base boyutlar)
            // dp uzerinde calisir; burada donusum YAPILMAZ.
            val workspaceWidth = maxWidth.value
            val workspaceHeight = maxHeight.value
            val isPortrait = workspaceHeight > workspaceWidth
            LaunchedEffect(isPortrait) {
                layoutStore.updateOrientation(isPortrait)
            }
            // Grid modu: snap ayari acikken Launcher tarzi hucre grid'i kullanilir;
            // kapaliyken serbest float mod devreye girer. Hucre sayisi ayarlardan
            // yon bagimli olarak okunur (donen ekranlarda dik/yatay farkli grid).
            val gridLandscapeCols by CarLauncherSettings.workspaceGridLandscapeCols.collectAsState()
            val gridLandscapeRows by CarLauncherSettings.workspaceGridLandscapeRows.collectAsState()
            val gridPortraitCols by CarLauncherSettings.workspaceGridPortraitCols.collectAsState()
            val gridPortraitRows by CarLauncherSettings.workspaceGridPortraitRows.collectAsState()
            // Serbest mod davranisi: eski "grid'e yapis ve cakismayi onle" gorevi.
            // Grid modu kapaliyken gecerlidir.
            val freeSnap by CarLauncherSettings.workspaceFreeSnap.collectAsState()
            val gridCols = if (isPortrait) gridPortraitCols else gridLandscapeCols
            val gridRows = if (isPortrait) gridPortraitRows else gridLandscapeRows
            // Olcu henuz gelmemisse (0) hucre grid'i kurulmaz; kayitsiz garapan yazim engellenir.
            val gridHealthy = workspaceWidth > 0f && workspaceHeight > 0f
            val cellGrid = if (gridHealthy) {
                remember(workspaceWidth, workspaceHeight, gridCols, gridRows) {
                    CellGrid.build(workspaceWidth, workspaceHeight, gridCols, gridRows)
                }
            } else {
                null
            }
            // resize C: sistem widget'larinin saglayici bildiriminden gelen span kurali
            // (min/max boyut + tercih edilen hucre). hostController.info() her cagrida
            // binder'a gittigi icin widget listesi ve grid basina cache'lenir.
            val spanRules = remember(activeWidgets, cellGrid) {
                val grid = cellGrid
                if (grid == null) {
                    emptyMap()
                } else {
                    activeWidgets.mapNotNull { id ->
                        if (!id.startsWith(WidgetIds.AW_PREFIX)) return@mapNotNull null
                        val awId = id.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
                            ?: return@mapNotNull null
                        hostController.info(awId)?.let { info -> id to info.spanRuleFor(grid) }
                    }.toMap()
                }
            }
            // Perf: resolve O(hucre x widget) maliyetlidir; telemetri/medya guncellemelerinde
            // yeniden calismamasi icin girislere bagli olarak cache'lenir.
            val resolvedLayout = remember(activeWidgets, layout, workspaceWidth, workspaceHeight, snapToGrid, freeSnap, cellGrid, spanRules) {
                if (snapToGrid && cellGrid != null) {
                    CellGrid.solve(activeWidgets, layout, cellGrid, rules = { id -> spanRules[id] })
                } else {
                    // Serbest mod: freeSnap acikken eski davranis (16dp yapisma + cakisma onleme),
                    // kapaliyken tamamen serbest yerlesim.
                    WorkspaceGrid.resolve(activeWidgets, layout, workspaceWidth, workspaceHeight, snapToGrid = freeSnap)
                }
            }
            val lastPage = maxOf(pageCount - 1, resolvedLayout.values.maxOfOrNull { it.page } ?: 0)
            LaunchedEffect(lastPage) { layoutStore.ensurePageCount(lastPage + 1) }
            // Grid modunda birakma kurallari (Launcher davranisi) saf motorda:
            // DesktopPlacementEngine (birim testli). Burada yalnizca o anki cozulmus
            // yerlesim ve grid aktarilir.
            val engineGrid = if (snapToGrid) cellGrid else null

            fun placementAllowed(id: String, target: WidgetPlacement): Boolean =
                DesktopPlacementEngine.allowed(resolvedLayout, id, target, engineGrid)

            /** Bu sayfada bu boyutta widget icin (swap dahil) yer var mi? */
            fun pageHasRoom(id: String, page: Int, spanX: Int, spanY: Int): Boolean =
                DesktopPlacementEngine.hasRoom(resolvedLayout, id, page, spanX, spanY, engineGrid)

            fun commitPlacement(id: String, target: WidgetPlacement): Boolean {
                if (!snapToGrid) {
                    layoutStore.setPlacement(id, target)
                    return true
                }
                if (!placementAllowed(id, target)) return false
                DesktopPlacementEngine.swapPlan(resolvedLayout, id, target, cellGrid).forEach { (wid, placement) ->
                    layoutStore.setPlacement(wid, placement)
                }
                return true
            }
            LaunchedEffect(lastPage, isEditMode) {
                if (!isEditMode && currentPage > lastPage) currentPage = lastPage
            }
            LaunchedEffect(currentPage, pageCount, isEditMode, indicatorSeconds) {
                showPageIndicator = true
                if (!isEditMode) {
                    delay(indicatorSeconds * 1000L)
                    showPageIndicator = false
                }
            }
            // 0. MASAUSTU ARKA PLANI (Sistem Duvar Kagidi veya Tema)
            DesktopWallpaperView(modifier = Modifier.fillMaxSize())

            // Edit modunda yerlesim kilavuzunu gorunur kilar. Grid modunda cizgiler hucre
            // kenarlaridir; serbest snap'te 16dp adim cizgileri cizilir.
            if (isEditMode && (snapToGrid || freeSnap)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0.5f)
                ) {
                    val gridStep = if (snapToGrid && cellGrid != null) cellGrid.cellWidth.dp.toPx() else 16.dp.toPx()
                    val gridStepY = if (snapToGrid && cellGrid != null) cellGrid.cellHeight.dp.toPx() else gridStep
                    val lineColor = Color(0x12FFFFFF)
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += gridStep
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += gridStepY
                    }
                }
            }

            val totalPages = (lastPage + 1).coerceAtLeast(1)
            val pagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, totalPages - 1)) { totalPages }

            // PROGRAMATIK GEZINME (tek yon: currentPage -> pager).
            //
            // Widget suruklerken sayfa ANINDA degisir (animasyonsuz). Neden:
            //  - animateScrollToPage uzun mesafede ara sayfalara on-atlama yapar ve
            //    animasyon surerken pager'in currentPage'i degisir. Asagidaki iki yonlu
            //    senkron bu ara degeri currentPage'e yazip animasyonu iptal ediyordu;
            //    sonuc pager'in yari sayfa konumunda kalmasi (ornegin 1 ve 2. sayfanin
            //    yarisi gorunur) ve birakmanin yanlis yere denk gelmesiydi.
            //  - Surukleme sirasinda ara konumun anlami yok: widget zaten parmagin
            //    altindadir ve sayfa dilimi telafisi (pageShiftDp) tam sayfa kaymasiyla
            //    birebir ayni oldugu icin ani gecis gorunmez.
            LaunchedEffect(pagerState, draggingWidgetId) {
                snapshotFlow { currentPage to draggingWidgetId }
                    .collectLatest { (target, draggingId) ->
                        if (target !in 0 until totalPages) return@collectLatest
                        if (draggingId != null) {
                            pagerState.scrollToPage(target)
                            return@collectLatest
                        }
                        if (pagerState.currentPage != target ||
                            pagerState.currentPageOffsetFraction != 0f
                        ) {
                            pagerState.animateScrollToPage(target)
                            // Animasyon tamamlanmadan iptal edilmis olabilir; hedefe
                            // her durumda tam otur (yarim sayfa kalmasin).
                            if (pagerState.currentPage != target ||
                                pagerState.currentPageOffsetFraction != 0f
                            ) {
                                pagerState.scrollToPage(target)
                            }
                        }
                    }
            }
            // PAGER -> currentPage: yalnizca pager DURDUGUNDA ve surukleme yokken.
            // Animasyon/parmak kaydirmasi surerken ara sayfalar currentPage'e yazilmaz;
            // yoksa programatik gezinme kendi kendini iptal eder.
            LaunchedEffect(pagerState, draggingWidgetId) {
                if (draggingWidgetId != null) return@LaunchedEffect
                snapshotFlow {
                    Triple(
                        pagerState.isScrollInProgress,
                        pagerState.currentPage,
                        pagerState.currentPageOffsetFraction
                    )
                }
                    .distinctUntilChanged()
                    .collect { (scrolling, page, fraction) ->
                        if (scrolling) return@collect
                        if (page != currentPage) currentPage = page
                        // Guvenlik agi: pager durdugu halde tam sayfa konumunda degilse
                        // (iptal edilmis animasyon artigi) en yakin sayfaya ANINDA otur.
                        if (fraction != 0f) pagerState.scrollToPage(page)
                    }
            }
            // Guvenlik agi: edit modundan cikilinca takili kalan surukleme bayragi
            // pager'i kalici olarak kilitlemesin.
            LaunchedEffect(isEditMode, lastPage) {
                if (!isEditMode) draggingWidgetId = null
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = draggingWidgetId == null,
                // Surukleme sirasinda butun sayfalar canli tutulur: widget kendi sayfasi
                // icinde cizilmeye devam ettigi icin surukleme durumu kaybolmaz, pager ise
                // hedef sayfayi gostererek bos alanlari gorunur kilir.
                beyondViewportPageCount = if (draggingWidgetId == null) 1 else maxOf(lastPage, 1),
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val draggingHere = draggingWidgetId != null &&
                    resolvedLayout[draggingWidgetId]?.page == pageIndex
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Suruklenen widget'in sayfasi ustte cizilmeye calisilir; pager
                        // sayfalar arasi cizim sirasini garanti etmedigi icin asil koruma
                        // birakmanin yalnizca bos hucreye yapabilmesidir.
                        .zIndex(if (draggingHere) 1f else 0f)
                ) {
                    activeWidgets.forEach { widgetId ->
                        key(widgetId) {
                            val placement = resolvedLayout[widgetId]
                            val isDraggingThis = draggingWidgetId == widgetId
                            // Taban span widget turunun dogal boyutundan; sistem widget'larinda
                            // saglayicinin bildirdigi sinirlar onu ezer (resize C).
                            val spanRule = spanRules[widgetId]
                            val baseSpan = cellGrid?.let { CellGrid.spanOf(WidgetIds.type(widgetId), it) } ?: (1 to 1)
                            if (placement != null && placement.page == pageIndex) {
                                MovableWidget(
                                    placement = placement,
                                    // NOT: Surukleme sirasinda diger widget'lara alpha/graphicsLayer
                                    // uygulamayin: AppWidgetHostView (AndroidView) icerigi
                                    // alpha < 1 ile bazi surumlerde hic cizilmiyor.
                                    modifier = Modifier.zIndex(if (draggingWidgetId == widgetId) 100f else 0f),
                                    // Saglayici "resizeMode = yok" diyorsa tutamaclar gereksiz.
                                    resizable = spanRule == null ||
                                        spanRule.limits.maxX > spanRule.limits.minX ||
                                        spanRule.limits.maxY > spanRule.limits.minY,
                                    dragViaHandle = true,
                                    showControls = isEditMode,
                                    snapToGrid = snapToGrid || freeSnap,
                                    grid = if (snapToGrid) cellGrid else null,
                                    minSpanX = spanRule?.limits?.minX ?: baseSpan.first,
                                    minSpanY = spanRule?.limits?.minY ?: baseSpan.second,
                                    maxSpanX = spanRule?.limits?.maxX ?: Int.MAX_VALUE,
                                    maxSpanY = spanRule?.limits?.maxY ?: Int.MAX_VALUE,
                                    onEnterEdit = { isEditMode = true },
                                    onDragging = { isDragging ->
                                        draggingWidgetId = if (isDragging) widgetId else null
                                        if (isDragging) showPageIndicator = true
                                    },
                                    // Parmak kenarda beklenince hedef sayfa adimlar; sayfa
                                    // gercekten kaydirilir ve widget parmagin altinda kalir.
                                    // Dolu sayfalar atlanir: motor yondaki ilk bos sayfayi
                                    // doner. Hicbir sayfada yer yoksa null doner ve adim
                                    // atlanmaz (kirmizi cerceve).
                                    onPageEdge = { from, direction, sx, sy ->
                                        DesktopPlacementEngine.stepPageWithRoom(
                                            layout = resolvedLayout,
                                            id = widgetId,
                                            fromPage = from,
                                            direction = direction,
                                            lastPage = lastPage,
                                            spanX = sx,
                                            spanY = sy,
                                            grid = engineGrid
                                        )
                                    },
                                    onPageTarget = { page -> currentPage = page },
                                    // Suruklenen widget kendi sayfasinin diliminde cizilir.
                                    // Pager hedef sayfaya kayarken widget'in ekranda sabit
                                    // kalmasi icin pager'in GERCEK ofseti kadar (dp) geri
                                    // kaydirilir. Ofset pagerState'ten okunur: dongunun
                                    // verdigi hedef degil, animasyonun o anki degeri
                                    // kullanilir; boylece widget parmaktan kopmaz.
                                    pageShiftDp = if (isDraggingThis) {
                                        {
                                            // Sayfa i'nin ekrandaki x'i (i - pos) * genislik'tir.
                                            // Widget'in ekranda sabit kalmasi icin:
                                            //   (placement.page - pos) * w + dx + shift = dx
                                            //   => shift = (pos - placement.page) * w
                                            val pos = pagerState.currentPage.toFloat() +
                                                pagerState.currentPageOffsetFraction
                                            (pos - placement.page) * workspaceWidth
                                        }
                                    } else {
                                        { 0f }
                                    },
                                    workspaceWidth = workspaceWidth,
                                    workspaceHeight = workspaceHeight,
                                    onRemove = {
                                        if (widgetId.startsWith(WidgetIds.AW_PREFIX)) {
                                            val awId = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
                                            if (awId != null) hostController.deleteId(awId)
                                        }
                                        layoutStore.removePlacement(widgetId)
                                    },
                                    onSettings = { selectedWidgetId = widgetId },
                                    onCommit = { newPlacement ->
                                        // Kabul edilirse yerlesir (farkli sayfaya tasindiysa oraya
                                        // gecilir); reddedilirse widget kendi konumunda kalir ve
                                        // kirmizi cerceve yanip soner.
                                        val accepted = commitPlacement(widgetId, newPlacement)
                                        if (accepted && newPlacement.page != currentPage) {
                                            currentPage = newPlacement.page
                                        }
                                        accepted
                                    },
                                    canDrop = { page, cellX, cellY, spanX, spanY ->
                                        placementAllowed(
                                            widgetId,
                                            (resolvedLayout[widgetId] ?: WidgetPlacement()).copy(
                                                page = page,
                                                dx = 0f,
                                                dy = 0f,
                                                cellX = cellX,
                                                cellY = cellY,
                                                spanX = spanX,
                                                spanY = spanY
                                            )
                                        )
                                    }
                                ) {
                                    CompositionLocalProvider(LocalDesktopCardColor provides Color(placement.backgroundColor).let { it.copy(alpha = it.alpha * placement.opacity) }) {
                                        RenderDesktopWidgetContent(
                                            widgetId = WidgetIds.type(widgetId),
                                            saatMetni = saatMetni,
                                            tarihMetni = tarihMetni,
                                            telemetri = latestTelemetri,
                                            medya = latestMedya,
                                            onOynatDuraklat = onOynatDuraklat,
                                            onSonraki = onSonraki,
                                            onOnceki = onOnceki,
                                            onMuzikPaneliAc = onMuzikPaneliAc,
                                            onLaunchApp = onLaunchApp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Surukleme sirasinda hedef sayfa nokta gostergesinde isaretlenir:
            // camgobegi = oraya birakilabilir, kirmizi = yer yok (o sayfaya gecilmez).
            AnimatedVisibility(
                visible = showPageIndicator,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)
            ) {
                Surface(color = Color(0x99141624), shape = RoundedCornerShape(20.dp)) {
                    LazyRow(
                        modifier = Modifier.widthIn(max = 360.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(lastPage + 1) { page ->
                            Box(
                                Modifier
                                    .size(if (page == currentPage) 10.dp else 7.dp)
                                    .clip(CircleShape)
                                    .background(if (page == currentPage) Color.White else Color(0x66FFFFFF))
                                    .clickable(enabled = isEditMode && draggingWidgetId == null) { currentPage = page }
                            )
                        }
                        if (isEditMode) item {
                            Icon(
                                Icons.Default.Add, "Yeni sayfa", tint = ClPrimary,
                                modifier = Modifier.size(22.dp).clickable(enabled = draggingWidgetId == null) {
                                    currentPage = layoutStore.addPage()
                                }
                            )
                        }
                        if (isEditMode && lastPage > 0) item {
                            Icon(
                                Icons.Default.Delete, "Sayfayı sil", tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(22.dp).clickable { showPageDeleteConfirm = true }
                            )
                        }
                    }
                }
            }

            // Sayfa silme onayi: kazara dokunmada widget'larin dagilmamasi icin once sorulur.
            if (showPageDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showPageDeleteConfirm = false },
                    title = { Text("Sayfa silinsin mi?") },
                    text = { Text("Bu sayfadaki widget'lar bir onceki sayfaya tasinir. Islem geri alinamaz.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showPageDeleteConfirm = false
                            val nextPage = (currentPage - 1).coerceAtLeast(0)
                            if (layoutStore.removePage(currentPage)) currentPage = nextPage
                        }) { Text("Sil") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPageDeleteConfirm = false }) { Text("Vazgec") }
                    }
                )
            }

            // 2. YUZEN KONTROL CUBUGU (Sadece Edit Modunda Gorunur: Ekle / Duvar Kagidi / Sifirla / Kaydet)
            if (isEditMode) {
                FloatingDesktopControls(
                    onExitEditMode = { isEditMode = false },
                    onOpenWidgetPicker = { showWidgetPicker = true },
                    onOpenWallpaperPicker = { showWallpaperDialog = true },
                    onResetLayout = { layoutStore.resetLayout(); currentPage = 0 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(200f)
                )
            }

            // 3. WIDGET EKLEME DIYALOGU (Kullanicinin 7-8 Widget'i)
            if (showWidgetPicker) {
                DesktopWidgetPicker(
                    activeWidgets = activeWidgets,
                    onDismiss = { showWidgetPicker = false },
                    onAddWidget = { id ->
                        layoutStore.addWidget(id, (layout[id] ?: DesktopWidgetLayoutStore.DEFAULT_PLACEMENTS[id] ?: WidgetPlacement()).copy(page = currentPage))
                        showWidgetPicker = false
                    },
                    onAddShortcut = { app ->
                        layoutStore.addWidget("${WidgetIds.APP_PREFIX}${app.paketAdi}", WidgetPlacement(page = currentPage))
                        showWidgetPicker = false
                    },
                    onAddSystemWidget = { providerInfo ->
                        showWidgetPicker = false
                        val appWidgetId = hostController.allocateId()
                        val canBind = runCatching {
                            hostController.manager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
                        }.getOrDefault(false)
                        if (!canBind) {
                            pendingAppWidgetId = appWidgetId
                            bindLauncher.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                            })
                        } else {
                            finishWidgetBinding(appWidgetId)
                        }
                    }
                )
            }

            selectedWidgetId?.let { widgetId ->
                DesktopWidgetSettingsDialog(
                    widgetId = widgetId,
                    placement = resolvedLayout[widgetId] ?: layout[widgetId] ?: WidgetPlacement(page = currentPage),
                    pageCount = lastPage + 1,
                    gridMode = snapToGrid,
                    grid = if (snapToGrid) cellGrid else null,
                    // Hazir boyutlar ve +/- adimlari icin sinirlar: sistem widget'ta
                    // saglayici bildirimi, yerlesik widget'ta taban boyut ve tahta olcusu.
                    spanLimits = if (snapToGrid) {
                        cellGrid?.let { g ->
                            spanRules[widgetId]?.limits
                                ?: CellGrid.spanOf(WidgetIds.type(widgetId), g)
                                    .let { (baseX, baseY) -> WidgetSpanLimits(baseX, baseY, g.cols, g.rows) }
                        }
                    } else {
                        null
                    },
                    spanFits = { sx, sy ->
                        val pl = resolvedLayout[widgetId]
                        val g = cellGrid
                        if (pl == null || g == null) {
                            true
                        } else {
                            // Hucre sabit kalip yalnizca span degisir: sag/alt tasma ve komsu
                            // cakismasi burada kontrol edilir.
                            pl.cellX + sx <= g.cols && pl.cellY + sy <= g.rows &&
                                placementAllowed(widgetId, pl.copy(spanX = sx, spanY = sy))
                        }
                    },
                    pageHasRoom = { page ->
                        val pl = resolvedLayout[widgetId]
                        pl == null || pageHasRoom(
                            widgetId,
                            page,
                            pl.spanX.coerceAtLeast(1),
                            pl.spanY.coerceAtLeast(1)
                        )
                    },
                    providerInfo = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()?.let(hostController::info),
                    onDismiss = { selectedWidgetId = null },
                    onSave = { placement ->
                        // Sayfa/boyut degisikligi de birakma kurallarindan gecer: hedef
                        // sayfada yer yoksa yazilmaz (widget eski sayfasinda kalir).
                        if (commitPlacement(widgetId, placement) && placement.page != currentPage) {
                            currentPage = placement.page
                        }
                        selectedWidgetId = null
                    },
                    onConfigure = { info, id ->
                        runCatching {
                            context.startActivity(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                component = info.configure
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            })
                        }
                        selectedWidgetId = null
                    },
                    onRemove = {
                        widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()?.let(hostController::deleteId)
                        layoutStore.removePlacement(widgetId)
                        selectedWidgetId = null
                    }
                )
            }

            // 5. DUVAR KAGIDI DIYALOGU
            if (showWallpaperDialog) {
                WallpaperDialog(
                    onDismiss = { showWallpaperDialog = false }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WIDGET CERIK RENDERLARI (KULLANICININ 7-8 WIDGET'I)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Cerceve olcusune gore icerik olcegi.
 *
 * Hucre modunda MovableWidget icerige olcek uygulamaz, gercek cerceve olcusunu verir;
 * kartlar da bu olcegi taban tasarim olculerine gore hesaplayip yazi/simbol boyutlarini
 * yeniden duzenler. Boylece widget buyudugunde buyutulmus bulanik bir kopya degil,
 * yeni boyuta gore yerlesen icerik gorunur.
 *
 * Serbest (grid'siz) modda icerik sinirsiz olculur (olceklenerek cizilir); bu durumda
 * taban olcek (1f) kullanilir.
 */
private fun androidx.compose.foundation.layout.BoxWithConstraintsScope.cardScale(
    baseWidthDp: Float,
    baseHeightDp: Float
): Float {
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return 1f
    return minOf(maxWidth.value / baseWidthDp, maxHeight.value / baseHeightDp).coerceIn(0.75f, 2.2f)
}

@Composable
private fun RenderDesktopWidgetContent(
    widgetId: String,
    saatMetni: String,
    tarihMetni: String,
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    when {
        // 1. DIJITAL SAAT & TARIH
        widgetId == WidgetIds.CLOCK -> {
            ClockWidgetCard(saatMetni = saatMetni, tarihMetni = tarihMetni)
        }
        // 2. SISTEM / CIHAZ DURUMU (PIL, RAM, HAFIZA)
        widgetId == WidgetIds.STATUS -> {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val s = cardScale(280f, 140f)
                StatusWidgetView(
                    modifier = Modifier.fillMaxSize(),
                    backgroundColor = ClCardBg,
                    scale = s
                )
            }
        }
        // 3. CANLI HIZ GOSTERGESI
        widgetId == WidgetIds.SPEEDOMETER -> {
            SpeedometerWidgetCard(telemetri = telemetri)
        }
        // 4. MUZIK CALAR KARTI
        widgetId == WidgetIds.MUSIC -> {
            MusicPlayerWidgetCard(
                medya = medya,
                onOynatDuraklat = onOynatDuraklat,
                onSonraki = onSonraki,
                onOnceki = onOnceki,
                onMuzikPaneliAc = onMuzikPaneliAc
            )
        }
        // 5. BIRLESIK DASHBOARD (SAAT + HIZ)
        widgetId == WidgetIds.COMBINED -> {
            CombinedDashboardCard(saatMetni = saatMetni, tarihMetni = tarihMetni, telemetri = telemetri)
        }
        // 6. HAVA DURUMU
        widgetId == WidgetIds.WEATHER -> {
            WeatherWidgetCard()
        }
        // 7. PUSULA & YON
        widgetId == WidgetIds.COMPASS -> {
            CompassWidgetCard(telemetri = telemetri)
        }
        // 8. OBD2 / TELEMETRI VERILERI
        widgetId == WidgetIds.OBD -> {
            ObdWidgetCard()
        }
        // 9. UYGULAMA KISAYOLLARI DOCK'U
        widgetId == WidgetIds.DOCK -> {
            DockWidgetCard(onLaunchApp = onLaunchApp)
        }
        widgetId.startsWith(WidgetIds.APP_PREFIX) -> {
            DesktopAppShortcut(widgetId.removePrefix(WidgetIds.APP_PREFIX), onLaunchApp)
        }
        // 10. ANDROID 3. PARTI APP WIDGETLARI
        widgetId.startsWith(WidgetIds.AW_PREFIX) -> {
            val awId = widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()
            if (awId != null) {
                // Gercek cerceve olcusu verilir: HostedAppWidgetView olcuyu onSizeChanged ile
                // AppWidgetManager'a bildirir, saglayici widget'i yeni boyuta gore cizer.
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    HostedAppWidgetView(appWidgetId = awId, modifier = Modifier.fillMaxSize())
                }
            }
        }
        else -> {
            Surface(
                color = ClCardBg,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = widgetId, color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// WIDGET KART DETAYLARI
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ClockWidgetCard(saatMetni: String, tarihMetni: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Taban tasarim 260x100 dp; cerceve buyudukce yazilar orantili buyur.
        val s = cardScale(260f, 100f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = (16 * s).dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = saatMetni,
                    color = Color.White,
                    fontSize = (42 * s).sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
                Text(
                    text = tarihMetni.replaceFirstChar { it.uppercase() },
                    color = Color(0xFFB0B3C6),
                    fontSize = (13 * s).sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SpeedometerWidgetCard(telemetri: HizTelemetrisi) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(240f, 160f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding((14 * s).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${telemetri.anlikHizKmh}",
                    color = Color.White,
                    fontSize = (52 * s).sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "KM / S",
                    color = ClPrimary,
                    fontSize = (11 * s).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height((4 * s).dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy((12 * s).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pusula: ${telemetri.pusulaYonu.roundToInt()}°",
                        color = Color(0xFF8E92A8),
                        fontSize = (11 * s).sp,
                        maxLines = 1
                    )
                    Text(
                        text = "İrtifa: ${telemetri.irtifaMetre.roundToInt()}m",
                        color = Color(0xFF8E92A8),
                        fontSize = (11 * s).sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CombinedDashboardCard(saatMetni: String, tarihMetni: String, telemetri: HizTelemetrisi) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(340f, 120f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding((16 * s).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = saatMetni, color = Color.White, fontSize = (38 * s).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(text = tarihMetni, color = Color.LightGray, fontSize = (11 * s).sp, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${telemetri.anlikHizKmh}", color = ClPrimary, fontSize = (44 * s).sp, fontWeight = FontWeight.Black)
                    Text(text = "KM / S", color = Color.Gray, fontSize = (10 * s).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerWidgetCard(
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(300f, 160f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onMuzikPaneliAc() }
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding((14 * s).dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size((46 * s).dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0072FF)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "♪", color = Color.White, fontSize = (24 * s).sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width((12 * s).dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medya.baslik.ifBlank { "Müzik Çalınmıyor" },
                            color = Color.White,
                            fontSize = (14 * s).sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = medya.sanatci.ifBlank { "Vela Audio" },
                            color = Color(0xFFB0B3C6),
                            fontSize = (12 * s).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0x33FFFFFF),
                        shape = CircleShape,
                        modifier = Modifier.size((36 * s).dp).clickable { onOnceki() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "◀◀", color = Color.White, fontSize = (11 * s).sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = ClPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size((46 * s).dp).clickable { onOynatDuraklat() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (medya.caliyorMu) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size((24 * s).dp)
                            )
                        }
                    }

                    Surface(
                        color = Color(0x33FFFFFF),
                        shape = CircleShape,
                        modifier = Modifier.size((36 * s).dp).clickable { onSonraki() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "▶▶", color = Color.White, fontSize = (11 * s).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherWidgetCard() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(170f, 90f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding((12 * s).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size((28 * s).dp)) {
                    drawCircle(color = Color(0xFFFFCC00), radius = size.minDimension / 2.2f)
                }
                Spacer(modifier = Modifier.width((12 * s).dp))
                Column {
                    Text(text = "22°C", color = Color.White, fontSize = (22 * s).sp, fontWeight = FontWeight.Bold)
                    Text(text = "Güneşli", color = Color.LightGray, fontSize = (11 * s).sp)
                }
            }
        }
    }
}

@Composable
private fun CompassWidgetCard(telemetri: HizTelemetrisi) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(170f, 90f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding((10 * s).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size((28 * s).dp)) {
                    drawCircle(
                        color = Color(0xFF32ADE6),
                        radius = size.minDimension / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
                Spacer(modifier = Modifier.height((4 * s).dp))
                Text(text = "YÖN: ${telemetri.pusulaYonu.roundToInt()}°", color = Color.White, fontSize = (12 * s).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ObdWidgetCard() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(280f, 90f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding((12 * s).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "RPM", color = Color.Gray, fontSize = (10 * s).sp)
                    Text(text = "2200", color = Color.White, fontSize = (16 * s).sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "HARARET", color = Color.Gray, fontSize = (10 * s).sp)
                    Text(text = "90°C", color = ClSuccess, fontSize = (16 * s).sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "VOLTAJ", color = Color.Gray, fontSize = (10 * s).sp)
                    Text(text = "14.2V", color = ClPrimary, fontSize = (16 * s).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DockWidgetCard(onLaunchApp: (String) -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager

    val dockApps = listOf(
        "com.google.android.apps.maps" to "Harita",
        "net.osmand.plus" to "OsmAnd",
        "com.spotify.music" to "Spotify",
        "com.android.settings" to "Ayarlar"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(340f, 76f)
        Surface(
            color = ClCardBg,
            shape = RoundedCornerShape(22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ClBorder),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = (14 * s).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockApps.forEach { (pkg, fallbackLabel) ->
                    val icon = remember(pkg) {
                        try { pm.getApplicationIcon(pkg).toBitmap(64, 64).asImageBitmap() } catch (e: Exception) { null }
                    }

                    Box(
                        modifier = Modifier
                            .size((48 * s).dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { onLaunchApp(pkg) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = fallbackLabel, modifier = Modifier.size((36 * s).dp))
                        } else {
                            Text(text = fallbackLabel.take(1), color = Color.White, fontSize = (18 * s).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopAppShortcut(packageName: String, onLaunchApp: (String) -> Unit) {
    val context = LocalContext.current
    val label = remember(packageName) {
        InternalApp.fromUri(packageName)?.getAd(context) ?: runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }
    val icon = remember(packageName) {
        runCatching {
            (InternalApp.fromUri(packageName)?.getIkon(context)
                ?: context.packageManager.getApplicationIcon(packageName))
                .toBitmap(64, 64).asImageBitmap()
        }.getOrNull()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val s = cardScale(80f, 96f)
        Column(
            modifier = Modifier.fillMaxSize().clickable { onLaunchApp(packageName) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) Image(icon, contentDescription = label, modifier = Modifier.size((56 * s).dp))
            else Text(label.take(1), color = Color.White, fontSize = (30 * s).sp)
            Text(label, color = Color.White, fontSize = (11 * s).sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChooseShortcutDialog(onDismiss: () -> Unit, onSelect: (AracUygulamasi) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AracUygulamasi>>(emptyList()) }
    LaunchedEffect(Unit) { apps = CarAppManager.getInstance(context).yukluUygulamalariGetir() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uygulama seç") },
        text = {
            LazyColumn {
                items(apps) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(app) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = remember(app.paketAdi) { app.ikon?.toBitmap(48, 48)?.asImageBitmap() }
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(app.ad, color = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
private fun DesktopWidgetSettingsDialog(
    widgetId: String,
    placement: WidgetPlacement,
    pageCount: Int,
    gridMode: Boolean,
    grid: CellGrid.Grid?,
    spanLimits: WidgetSpanLimits?,
    spanFits: (Int, Int) -> Boolean,
    pageHasRoom: (Int) -> Boolean,
    providerInfo: AppWidgetProviderInfo?,
    onDismiss: () -> Unit,
    onSave: (WidgetPlacement) -> Unit,
    onConfigure: (AppWidgetProviderInfo, Int) -> Unit,
    onRemove: () -> Unit
) {
    var draft by remember(widgetId, placement) { mutableStateOf(placement) }
    val title = when {
        widgetId.startsWith(WidgetIds.APP_PREFIX) -> "Uygulama kısayolu"
        widgetId.startsWith(WidgetIds.AW_PREFIX) -> providerInfo?.loadLabel(LocalContext.current.packageManager) ?: "Sistem widget'ı"
        else -> mapOf(
            WidgetIds.CLOCK to "Dijital saat", WidgetIds.MUSIC to "Müzik çalar",
            WidgetIds.SPEEDOMETER to "Hız göstergesi", WidgetIds.STATUS to "Sistem durumu",
            WidgetIds.DOCK to "Uygulama dock'u", WidgetIds.COMBINED to "Saat + Hız",
            WidgetIds.WEATHER to "Hava durumu", WidgetIds.COMPASS to "Pusula", WidgetIds.OBD to "OBD2"
        )[WidgetIds.type(widgetId)] ?: widgetId
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1D2E),
        title = { Text("$title ayarları", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (gridMode) {
                    // Grid modunda boyut hucre span'i ile belirlenir. Tutamaca ek olarak
                    // hazir boyutlar ve +/- adimlari: tutamacin zor ulazilabildigi
                    // (ust/kenar) veya dokunmatik olmayan durumlarda boyut degistirmek icin.
                    Text("Boyut", color = Color.White, fontWeight = FontWeight.SemiBold)
                    val limits = spanLimits
                    val board = grid
                    val spanX = draft.spanX.coerceAtLeast(1)
                    val spanY = draft.spanY.coerceAtLeast(1)
                    if (limits != null && board != null) {
                        /** Aday boyut uygulanabilir mi? Saglayici siniri + tasma kontrolu. */
                        fun usable(nx: Int, ny: Int): Boolean =
                            limits.accepts(nx, ny) && nx in 1..board.cols && ny in 1..board.rows && spanFits(nx, ny)

                        fun applySpan(nx: Int, ny: Int) {
                            if (usable(nx, ny)) draft = draft.copy(spanX = nx, spanY = ny)
                        }
                        Text(
                            "${spanX} x ${spanY} hucre (en fazla ${limits.maxX} x ${limits.maxY})",
                            color = Color(0xFFB0B3C6),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Genislik", color = Color(0xFFB0B3C6), style = MaterialTheme.typography.bodySmall)
                            SizeStepButton("-") { applySpan(spanX - 1, spanY) }
                            Text("$spanX", color = Color.White, fontWeight = FontWeight.Bold)
                            SizeStepButton("+") { applySpan(spanX + 1, spanY) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Yukseklik", color = Color(0xFFB0B3C6), style = MaterialTheme.typography.bodySmall)
                            SizeStepButton("-") { applySpan(spanX, spanY - 1) }
                            Text("$spanY", color = Color.White, fontWeight = FontWeight.Bold)
                            SizeStepButton("+") { applySpan(spanX, spanY + 1) }
                        }
                        val presets = WidgetSpanSizing.presets(limits, board, spanX, spanY)
                        if (presets.size > 1) {
                            Text("Hazir boyutlar", color = Color(0xFFB0B3C6), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                presets.forEach { (px, py) ->
                                    val selected = px == spanX && py == spanY
                                    TextButton(
                                        enabled = usable(px, py),
                                        onClick = { applySpan(px, py) },
                                        modifier = Modifier.background(
                                            if (selected) ClPrimary.copy(alpha = 0.25f) else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                    ) {
                                        Text(
                                            "${px}x${py}",
                                            color = if (usable(px, py)) Color.White else Color(0xFF6B6F85)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "Grid modunda widget boyutu hücre dilimleriyle belirlenir. Değiştirmek için sağ alt / kenar resize tutamaçlarını sürükleyin.",
                            color = Color(0xFFB0B3C6),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text("Boyut", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f to "Küçük", 1f to "Normal", 1.35f to "Büyük").forEach { (scale, label) ->
                            TextButton(
                                onClick = { draft = draft.copy(scale = scale, widthScale = scale, heightScale = scale) },
                                modifier = Modifier.background(
                                    if (kotlin.math.abs(draft.widthScale - scale) < 0.05f && kotlin.math.abs(draft.heightScale - scale) < 0.05f) ClPrimary.copy(alpha = 0.25f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                            ) { Text(label, color = Color.White) }
                        }
                    }
                }
                Text("Arka plan opaklığı: ${(draft.opacity * 100).roundToInt()}%", color = Color.White)
                Slider(value = draft.opacity, onValueChange = { draft = draft.copy(opacity = it) }, valueRange = 0.1f..1f)
                Text("Arka plan rengi", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xF0141624L, 0xFF000000L, 0xFF263238L, 0xFF16354AL, 0xFF49304FL, 0x00000000L).forEach { color ->
                        Box(Modifier.size(32.dp).background(Color(color), CircleShape)
                            .border(if (draft.backgroundColor == color) 3.dp else 1.dp, Color.White, CircleShape)
                            .clickable { draft = draft.copy(backgroundColor = color) })
                    }
                }
                Text("Sayfa", color = Color.White, fontWeight = FontWeight.SemiBold)
                LazyColumn(modifier = Modifier.height(120.dp)) {
                    items(pageCount) { page ->
                        val room = pageHasRoom(page)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = room) { draft = draft.copy(page = page) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sayfa ${page + 1}",
                                color = when {
                                    !room -> Color(0xFF6B6F85)
                                    draft.page == page -> ClPrimary
                                    else -> Color.White
                                }
                            )
                            // Yer yoksa sayfa secilemez: widget oraya tasinamaz.
                            if (!room) Text("yer yok", color = Color(0xFFFF8A80), fontSize = 11.sp)
                        }
                    }
                }
                if (providerInfo?.configure != null) {
                    TextButton(onClick = {
                        widgetId.removePrefix(WidgetIds.AW_PREFIX).toIntOrNull()?.let { onConfigure(providerInfo, it) }
                    }) { Text("Widget yapılandırmasını aç", color = ClPrimary) }
                }
                TextButton(onClick = onRemove) { Text("Masaüstünden kaldır", color = Color(0xFFFF6B6B)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Uygula", color = ClPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç", color = Color.White) } }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// KONTROLLER VE DIYALOGLAR
// ═══════════════════════════════════════════════════════════════════════════

/** Kucuk +/- boyut adim dugmesi (widget ayarlarinda hazir boyut kontrolu). */
@Composable
private fun SizeStepButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .background(Color(0xFF262A3D), RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FloatingDesktopControls(
    onExitEditMode: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onOpenWallpaperPicker: () -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Edit modu kontrolleri: ana yuzucu buton "duzenlemeyi bitir", diger eylemler
    // simgeli acilir menude. Boylece masaustunde cok daha az yer kaplar.
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box {
            FloatingActionButton(
                onClick = { menuOpen = true },
                containerColor = Color(0xCC1A1D2E),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Daha fazla", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Widget ekle") },
                    leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenWidgetPicker() }
                )
                DropdownMenuItem(
                    text = { Text("Duvar kâğıdı") },
                    leadingIcon = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenWallpaperPicker() }
                )
                DropdownMenuItem(
                    text = { Text("Düzeni sıfırla") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = { menuOpen = false; onResetLayout() }
                )
            }
        }

        FloatingActionButton(
            onClick = onExitEditMode,
            containerColor = Color(0xFF00E5FF),
            contentColor = Color.Black,
            shape = CircleShape,
            modifier = Modifier.size(52.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Düzenlemeyi bitir", modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * Kullanicinin tum 7-8 widget'ini eklemesini saglayan ana dialog.
 */
@Composable
private fun AddWidgetDialog(
    activeWidgets: Set<String>,
    onDismiss: () -> Unit,
    onAddWidget: (String) -> Unit,
    onOpenSystemWidgetPicker: () -> Unit,
    onOpenShortcutPicker: () -> Unit
) {
    val items = listOf(
        WidgetIds.COMBINED to "⏱️ Dashboard (Saat + Hız)",
        WidgetIds.SPEEDOMETER to "🏎️ Canlı Hız Göstergesi",
        WidgetIds.CLOCK to "🕒 Dijital Saat & Tarih",
        WidgetIds.MUSIC to "🎵 Müzik Çalar Kartı",
        WidgetIds.WEATHER to "⛅ Hava Durumu",
        WidgetIds.COMPASS to "🧭 Pusula & Yön",
        WidgetIds.OBD to "🚗 OBD2 / Araç Telemetrisi",
        WidgetIds.STATUS to "📊 Sistem / Pil / RAM Durumu",
        WidgetIds.DOCK to "🚀 Uygulama Kısayolları Dock'u"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Surface(color = Color(0xFF0072FF), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenShortcutPicker() }) {
                        Text("Uygulama kısayolu ekle", color = Color.White, modifier = Modifier.padding(14.dp))
                    }
                }
                items(items) { (id, title) ->
                    val isAlreadyActive = id in activeWidgets
                    Surface(
                        color = if (isAlreadyActive) Color(0x22FFFFFF) else Color(0x442A2D40),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAlreadyActive) { onAddWidget(id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                color = if (isAlreadyActive) Color.Gray else Color.White,
                                fontSize = 14.sp
                            )
                            if (isAlreadyActive) {
                                Text("Masaüstünde", color = Color(0xFF30D158), fontSize = 12.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = Color(0xFF0072FF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSystemWidgetPicker() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📱 Android Sistem Widget'ı Ekle (Choose Widget)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1A1D2E)
    )
}

/**
 * UmainLauncher 3. Ekran Goruntusundeki "Choose widget" Dialogunun Birebir Karsiligi.
 * Cihazdaki tum yuklu Android AppWidget'larini uygulama ikonu ve adiyla listeler.
 */
@Composable
private fun ChooseSystemWidgetDialog(
    onDismiss: () -> Unit,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val providers = remember {
        try {
            appWidgetManager.installedProviders.sortedBy { it.loadLabel(context.packageManager) }
        } catch (e: Exception) {
            emptyList<AppWidgetProviderInfo>()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose widget", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            if (providers.isEmpty()) {
                Text("Cihazda kullanılabilir widget bulunamadı.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(providers) { info ->
                        val pm = context.packageManager
                        val label = remember(info) { info.loadLabel(pm) }
                        val iconBmp = remember(info) {
                            try {
                                val d: Drawable? = info.loadIcon(context, context.resources.displayMetrics.densityDpi)
                                    ?: pm.getApplicationIcon(info.provider.packageName)
                                d?.toBitmap(64, 64)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Surface(
                            color = Color(0x332A2D40),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProvider(info) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (iconBmp != null) {
                                    Image(
                                        bitmap = iconBmp,
                                        contentDescription = label,
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.provider.packageName,
                                        color = Color(0xFF8E92A8),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF161824)
    )
}

@Composable
private fun WallpaperDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_desktop_wallpaper", Context.MODE_PRIVATE) }
    val options = listOf(
        "system" to "Sistem Duvar Kağıdı (Canlı)",
        "carbon" to "Karbon Fiber",
        "cockpit" to "Gece Kokpiti (Koyu Mavi)",
        "pure_black" to "Saf Siyah (OLED)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duvar Kağıdı Seçimi", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (type, label) ->
                    Surface(
                        color = Color(0x442A2D40),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.edit().putString("wallpaper_type", type).apply()
                                onDismiss()
                            }
                    ) {
                        Box(modifier = Modifier.padding(14.dp)) {
                            Text(text = label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat", color = Color(0xFF00E5FF))
            }
        },
        containerColor = Color(0xFF1A1D2E)
    )
}

@Composable
private fun DesktopWallpaperView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_desktop_wallpaper", Context.MODE_PRIVATE) }
    val type = remember { prefs.getString("wallpaper_type", "system") ?: "system" }

    when (type) {
        "system" -> {
            // Pencere transparan oldugu icin arkadaki Spider-Man gibi sistem duvar kagidi dogrudan gorunur!
            Box(modifier = modifier.background(Color.Transparent))
        }
        "pure_black" -> {
            Box(modifier = modifier.background(Color.Black))
        }
        "cockpit" -> {
            Box(
                modifier = modifier.background(
                    Brush.verticalGradient(listOf(Color(0xFF0A0E1A), Color(0xFF020408)))
                )
            )
        }
        else -> {
            // Carbon fiber gradyan
            Box(
                modifier = modifier.background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF181B28), Color(0xFF0A0C14)),
                        radius = 1200f
                    )
                )
            )
        }
    }
}
