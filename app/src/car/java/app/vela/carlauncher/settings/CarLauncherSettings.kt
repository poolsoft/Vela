package app.vela.carlauncher.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CoMaps_Auto_V2 ve OsmAnd Uyumlu Arac Bas Unitesi (Car Launcher) Ayarlari.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
object CarLauncherSettings {

    private const val PREFS_NAME = "vela_car_launcher_prefs"

    // Tercih anahtarlari (OsmAnd / CoMaps ile uyumlu)
    private const val KEY_CAR_MODE_ENABLED = "car_launcher_enabled"
    private const val KEY_STATUS_BAR = "car_launcher_status_bar"
    private const val KEY_IMMERSIVE_MODE = "car_launcher_immersive_mode"
    private const val KEY_DOCK_POSITION = "car_launcher_dock_position"
    private const val KEY_DOCK_SIZE = "car_launcher_dock_size"
    private const val KEY_PANEL_POSITION = "widget_panel_position"
    private const val KEY_EXPANSION_BEHAVIOR = "car_launcher_expansion_behavior"
    private const val KEY_PANEL_WIDTH_PERCENT = "widget_panel_width_percent"
    private const val KEY_PANEL_HEIGHT_PERCENT = "widget_panel_height_portrait"
    private const val KEY_AUTO_PLAY = "car_launcher_auto_play_music"
    private const val KEY_VISUALIZER_TYPE = "car_launcher_visualizer_type"
    private const val KEY_VISUALIZER_LARGE = "car_launcher_visualizer_type_large"
    private const val KEY_VISUALIZER_FPS = "car_launcher_visualizer_fps"
    private const val KEY_AMBIANCE_VISUALIZER = "car_launcher_ambiance_visualizer"
    private const val KEY_PREFERRED_MUSIC_APP = "car_launcher_music_app"
    private const val KEY_NIGHT_DIM_ENABLED = "car_launcher_night_dim_mode"
    private const val KEY_NIGHT_DIM_LEVEL = "car_launcher_night_dim_level"
    private const val KEY_DESKTOP_MODE = "car_launcher_desktop_mode"
    private const val KEY_DESKTOP_IN_MODE_CYCLE = "car_launcher_desktop_in_mode_cycle"
    private const val KEY_WORKSPACE_SWIPE_THRESHOLD = "car_launcher_workspace_swipe_threshold"
    private const val KEY_WORKSPACE_INDICATOR_SECONDS = "car_launcher_workspace_indicator_seconds"
    private const val KEY_STARTUP_SCREEN = "car_launcher_startup_screen"
    private const val KEY_WEATHER_ENABLED = "car_launcher_weather_enabled"
    private const val KEY_EQUALIZER_APP = "car_launcher_equalizer_app"

    // Widget Gorunurluk Anahtarlari (CoMaps uyumlu)
    private const val KEY_WIDGET_SPEED = "car_launcher_widget_speed"
    private const val KEY_WIDGET_COMPASS = "car_launcher_widget_compass"
    private const val KEY_WIDGET_MUSIC = "car_launcher_widget_music"
    private const val KEY_WIDGET_CLOCK = "car_launcher_widget_clock"
    private const val KEY_WIDGET_WEATHER = "car_launcher_widget_weather"
    private const val KEY_WIDGET_APPS = "car_launcher_widget_apps"

    // Ekran Yonu ve Dikey Mod
    private const val KEY_SCREEN_ORIENTATION = "car_launcher_screen_orientation"
    private const val KEY_PORTRAIT_MAP_ONLY = "car_launcher_portrait_map_only"

    // Floating Button Anahtarlari
    private const val KEY_FLOATING_BUTTON_MODE = "car_launcher_floating_button_mode" // always, background_only, never
    private const val KEY_FLOATING_BUTTON_SIZE = "car_launcher_floating_button_size" // dp, varsayilan 86
    private const val KEY_FLOATING_BUTTON_X = "car_launcher_floating_button_x"
    private const val KEY_FLOATING_BUTTON_Y = "car_launcher_floating_button_y"

    private val _workspaceSnapToGrid = MutableStateFlow(true)
    val workspaceSnapToGrid: StateFlow<Boolean> = _workspaceSnapToGrid.asStateFlow()

    fun setWorkspaceSnapToGrid(enabled: Boolean) {
        _workspaceSnapToGrid.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean("workspace_snap_to_grid", enabled).apply()
    }

    private lateinit var prefs: SharedPreferences

    // StateFlow'lar
    private val _carModeEtkin = MutableStateFlow(true)
    val carModeEtkin: StateFlow<Boolean> = _carModeEtkin.asStateFlow()

    private val _tamEkranModu = MutableStateFlow(true) // Varsayilan tam ekran (immersive)
    val tamEkranModu: StateFlow<Boolean> = _tamEkranModu.asStateFlow()

    private val _durumCubuguGoster = MutableStateFlow(true)
    val durumCubuguGoster: StateFlow<Boolean> = _durumCubuguGoster.asStateFlow()

    private val _dockKonumu = MutableStateFlow("right") // bottom, left, right
    val dockKonumu: StateFlow<String> = _dockKonumu.asStateFlow()

    private val _dockBoyutu = MutableStateFlow(50) // 0-100 (varsayilan 50)
    val dockBoyutu: StateFlow<Int> = _dockBoyutu.asStateFlow()

    private val _panelKonumu = MutableStateFlow("right") // right, left
    val panelKonumu: StateFlow<String> = _panelKonumu.asStateFlow()

    private val _panelGenislemeDavranisi = MutableStateFlow("swap") // swap, overlay
    val panelGenislemeDavranisi: StateFlow<String> = _panelGenislemeDavranisi.asStateFlow()

    private val _panelGenislikYuzdesi = MutableStateFlow(0.35f) // %15 - %65
    val panelGenislikYuzdesi: StateFlow<Float> = _panelGenislikYuzdesi.asStateFlow()

    private val _panelYukseklikYuzdesi = MutableStateFlow(0.35f)
    val panelYukseklikYuzdesi: StateFlow<Float> = _panelYukseklikYuzdesi.asStateFlow()

    private val _otomatikOynat = MutableStateFlow(false)
    val otomatikOynat: StateFlow<Boolean> = _otomatikOynat.asStateFlow()

    private val _gorsellestiriciTipi = MutableStateFlow(2) // 0: klasik ... 7: halkalar
    val gorsellestiriciTipi: StateFlow<Int> = _gorsellestiriciTipi.asStateFlow()

    private val _largeVisualizer = MutableStateFlow(2)
    val largeVisualizer: StateFlow<Int> = _largeVisualizer.asStateFlow()
    private val _visualizerFps = MutableStateFlow(30)
    val visualizerFps: StateFlow<Int> = _visualizerFps.asStateFlow()

    private val _ambiyansGorsellestirici = MutableStateFlow(true)
    val ambiyansGorsellestirici: StateFlow<Boolean> = _ambiyansGorsellestirici.asStateFlow()

    private val _tercihEdilenMuzikUygulamasi = MutableStateFlow<String?>(null)
    val tercihEdilenMuzikUygulamasi: StateFlow<String?> = _tercihEdilenMuzikUygulamasi.asStateFlow()

    private val _geceKarartmaEtkin = MutableStateFlow(false)
    val geceKarartmaEtkin: StateFlow<Boolean> = _geceKarartmaEtkin.asStateFlow()

    private val _geceKarartmaSeviyesi = MutableStateFlow(0.40f) // 0.1f - 0.8f
    val geceKarartmaSeviyesi: StateFlow<Float> = _geceKarartmaSeviyesi.asStateFlow()

    private val _desktopModu = MutableStateFlow(false)
    val desktopModu: StateFlow<Boolean> = _desktopModu.asStateFlow()

    private val _desktopDongudeEtkin = MutableStateFlow(true)
    val desktopDongudeEtkin: StateFlow<Boolean> = _desktopDongudeEtkin.asStateFlow()

    private val _workspaceSwipeThreshold = MutableStateFlow(0.35f)
    val workspaceSwipeThreshold: StateFlow<Float> = _workspaceSwipeThreshold.asStateFlow()

    private val _workspaceIndicatorSeconds = MutableStateFlow(2)
    val workspaceIndicatorSeconds: StateFlow<Int> = _workspaceIndicatorSeconds.asStateFlow()

    private val _baslangicEkrani = MutableStateFlow("normal") // normal, map_only, desktop
    val baslangicEkrani: StateFlow<String> = _baslangicEkrani.asStateFlow()
    private val _homeScreenRequest = MutableStateFlow(0L)
    val homeScreenRequest: StateFlow<Long> = _homeScreenRequest.asStateFlow()

    fun requestHomeScreen() {
        setDesktopModu(_baslangicEkrani.value == "desktop")
        _homeScreenRequest.value++
    }

    private val _floatingButtonModu = MutableStateFlow("always") // always, background_only, never
    val floatingButtonModu: StateFlow<String> = _floatingButtonModu.asStateFlow()

    private val _floatingButtonBoyutu = MutableStateFlow(86) // dp
    val floatingButtonBoyutu: StateFlow<Int> = _floatingButtonBoyutu.asStateFlow()

    private val _havaDurumuEtkin = MutableStateFlow(true)
    val havaDurumuEtkin: StateFlow<Boolean> = _havaDurumuEtkin.asStateFlow()

    private val _ekolayzirPaketi = MutableStateFlow<String?>(null)
    val ekolayzirPaketi: StateFlow<String?> = _ekolayzirPaketi.asStateFlow()

    // Widget Gorunurluk StateFlow'lari
    private val _widgetHizGoster = MutableStateFlow(true)
    val widgetHizGoster: StateFlow<Boolean> = _widgetHizGoster.asStateFlow()

    private val _widgetPusulaGoster = MutableStateFlow(true)
    val widgetPusulaGoster: StateFlow<Boolean> = _widgetPusulaGoster.asStateFlow()

    private val _widgetMuzikGoster = MutableStateFlow(true)
    val widgetMuzikGoster: StateFlow<Boolean> = _widgetMuzikGoster.asStateFlow()

    private val _widgetSaatGoster = MutableStateFlow(true)
    val widgetSaatGoster: StateFlow<Boolean> = _widgetSaatGoster.asStateFlow()

    private val _widgetHavaDurumuGoster = MutableStateFlow(true)
    val widgetHavaDurumuGoster: StateFlow<Boolean> = _widgetHavaDurumuGoster.asStateFlow()

    private val _widgetSistemGoster = MutableStateFlow(true)
    val widgetSistemGoster: StateFlow<Boolean> = _widgetSistemGoster.asStateFlow()

    // Ekran Yonu
    private val _ekranYonu = MutableStateFlow("landscape") // landscape, portrait, sensor
    val ekranYonu: StateFlow<String> = _ekranYonu.asStateFlow()

    private val _dikeydeSadeceHarita = MutableStateFlow(false)
    val dikeydeSadeceHarita: StateFlow<Boolean> = _dikeydeSadeceHarita.asStateFlow()

    fun baslat(context: Context, force: Boolean = false) {
        if (force || !::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _maplessPreference.value = prefs.getBoolean("car_launcher_mapless_mode", false)
            if (!sessionInitialized || _maplessPreference.value) {
                _maplessMode.value = _maplessPreference.value
                sessionInitialized = true
            }
            _maplessMainScreen.value = prefs.getString("car_launcher_mapless_main", "music")
                ?.takeIf { it in setOf("music", "speedometer", "dashboard") } ?: "music"
            _maplessScreen.value = prefs.getString("car_launcher_mapless_screen", "speedometer")
                ?.takeIf { it in setOf("speedometer", "dashboard") } ?: "speedometer"
            _carModeEtkin.value = prefs.getBoolean(KEY_CAR_MODE_ENABLED, true)
            _tamEkranModu.value = prefs.getBoolean(KEY_IMMERSIVE_MODE, true)
            _durumCubuguGoster.value = !_tamEkranModu.value && prefs.getBoolean(KEY_STATUS_BAR, true)
            _dockKonumu.value = prefs.getString(KEY_DOCK_POSITION, "right") ?: "right"
            _dockBoyutu.value = prefs.getInt(KEY_DOCK_SIZE, 50)
            _panelKonumu.value = prefs.getString(KEY_PANEL_POSITION, "right") ?: "right"
            val savedExpansion = prefs.getString(KEY_EXPANSION_BEHAVIOR, "swap")
            _panelGenislemeDavranisi.value = if (savedExpansion == "overlay") "overlay" else "swap"
            if (savedExpansion == "compact") prefs.edit().putString(KEY_EXPANSION_BEHAVIOR, "swap").apply()
            _panelGenislikYuzdesi.value = prefs.getFloat(KEY_PANEL_WIDTH_PERCENT, 0.35f)
            _panelYukseklikYuzdesi.value = prefs.getFloat(KEY_PANEL_HEIGHT_PERCENT, _panelGenislikYuzdesi.value).coerceIn(0.15f, 0.65f)
            _otomatikOynat.value = prefs.getBoolean(KEY_AUTO_PLAY, false)
            _gorsellestiriciTipi.value = prefs.getInt(KEY_VISUALIZER_TYPE, 2)
            _largeVisualizer.value = prefs.getInt(KEY_VISUALIZER_LARGE, _gorsellestiriciTipi.value).coerceIn(0, 7)
            _visualizerFps.value = prefs.getInt(KEY_VISUALIZER_FPS, 30).coerceIn(15, 60)
            _ambiyansGorsellestirici.value = prefs.getBoolean(KEY_AMBIANCE_VISUALIZER, true)
            _tercihEdilenMuzikUygulamasi.value = prefs.getString(KEY_PREFERRED_MUSIC_APP, null)
            _geceKarartmaEtkin.value = prefs.getBoolean(KEY_NIGHT_DIM_ENABLED, false)
            _geceKarartmaSeviyesi.value = prefs.getFloat(KEY_NIGHT_DIM_LEVEL, 0.40f)
            _workspaceSnapToGrid.value = prefs.getBoolean("workspace_snap_to_grid", true)
            _desktopModu.value = prefs.getBoolean(KEY_DESKTOP_MODE, false)
            _desktopDongudeEtkin.value = prefs.getBoolean(KEY_DESKTOP_IN_MODE_CYCLE, true)
            _workspaceSwipeThreshold.value = prefs.getFloat(KEY_WORKSPACE_SWIPE_THRESHOLD, 0.35f).coerceIn(0f, 0.6f)
            _workspaceIndicatorSeconds.value = prefs.getInt(KEY_WORKSPACE_INDICATOR_SECONDS, 2).coerceIn(1, 5)
            _baslangicEkrani.value = prefs.getString(KEY_STARTUP_SCREEN, "normal") ?: "normal"
            _floatingButtonModu.value = prefs.getString(KEY_FLOATING_BUTTON_MODE, "always") ?: "always"
            _floatingButtonBoyutu.value = prefs.getInt(KEY_FLOATING_BUTTON_SIZE, 86)
            _havaDurumuEtkin.value = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
            _ekolayzirPaketi.value = prefs.getString(KEY_EQUALIZER_APP, null)
            _widgetHizGoster.value = prefs.getBoolean(KEY_WIDGET_SPEED, true)
            _widgetPusulaGoster.value = prefs.getBoolean(KEY_WIDGET_COMPASS, true)
            _widgetMuzikGoster.value = prefs.getBoolean(KEY_WIDGET_MUSIC, true)
            _widgetSaatGoster.value = prefs.getBoolean(KEY_WIDGET_CLOCK, true)
            _widgetHavaDurumuGoster.value = prefs.getBoolean(KEY_WIDGET_WEATHER, true)
            _widgetSistemGoster.value = prefs.getBoolean(KEY_WIDGET_APPS, true)
            _ekranYonu.value = prefs.getString(KEY_SCREEN_ORIENTATION, "landscape") ?: "landscape"
            _dikeydeSadeceHarita.value = prefs.getBoolean(KEY_PORTRAIT_MAP_ONLY, false)
        }
    }

    private var sessionInitialized = false
    private val _maplessPreference = MutableStateFlow(false)
    val maplessPreference = _maplessPreference.asStateFlow()
    private val _maplessMainScreen = MutableStateFlow("music")
    val maplessMainScreen = _maplessMainScreen.asStateFlow()
    fun setMaplessMainScreen(screen: String) {
        require(screen in setOf("music", "speedometer", "dashboard"))
        _maplessMainScreen.value = screen
        if (::prefs.isInitialized) prefs.edit().putString("car_launcher_mapless_main", screen).apply()
    }
    fun disableFailedMap(reason: String) {
        app.vela.util.FileLogger.w("MapStartup", "Map disabled for this and future sessions: $reason")
        setMaplessMode(true)
    }
    private val _maplessMode = MutableStateFlow(false)
    val maplessMode: StateFlow<Boolean> = _maplessMode.asStateFlow()
    private val _maplessScreen = MutableStateFlow("speedometer")
    val maplessScreen: StateFlow<String> = _maplessScreen.asStateFlow()

    fun setMaplessMode(enabled: Boolean) {
        _maplessPreference.value = enabled
        // Disabling is immediate; enabling the renderer is deferred until a fresh app process.
        if (enabled) _maplessMode.value = true
        if (::prefs.isInitialized) prefs.edit().putBoolean("car_launcher_mapless_mode", enabled).commit()
    }
    fun setMaplessScreen(screen: String) {
        require(screen in setOf("speedometer", "dashboard"))
        _maplessScreen.value = screen
        if (::prefs.isInitialized) prefs.edit().putString("car_launcher_mapless_screen", screen).apply()
    }

    fun setCarModeEtkin(etkin: Boolean) {
        _carModeEtkin.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_CAR_MODE_ENABLED, etkin).apply()
    }

    fun setTamEkranModu(tamEkran: Boolean) {
        _tamEkranModu.value = tamEkran
        if (tamEkran) _durumCubuguGoster.value = false
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_IMMERSIVE_MODE, tamEkran).apply {
            putBoolean(KEY_STATUS_BAR, _durumCubuguGoster.value)
        }.apply()
    }

    fun setDurumCubuguGoster(goster: Boolean) {
        _durumCubuguGoster.value = goster
        if (goster) _tamEkranModu.value = false
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_STATUS_BAR, goster).apply {
            if (goster) putBoolean(KEY_IMMERSIVE_MODE, false)
        }.apply()
    }

    fun setDockKonumu(konum: String) {
        _dockKonumu.value = konum
        if (::prefs.isInitialized) prefs.edit().putString(KEY_DOCK_POSITION, konum).apply()
    }

    fun setDockBoyutu(boyut: Int) {
        _dockBoyutu.value = boyut.coerceIn(0, 100)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_DOCK_SIZE, _dockBoyutu.value).apply()
    }

    fun setPanelKonumu(konum: String) {
        _panelKonumu.value = konum
        if (::prefs.isInitialized) prefs.edit().putString(KEY_PANEL_POSITION, konum).apply()
    }

    fun setPanelGenislemeDavranisi(davranis: String) {
        _panelGenislemeDavranisi.value = if (davranis == "overlay") "overlay" else "swap"
        if (::prefs.isInitialized) prefs.edit().putString(KEY_EXPANSION_BEHAVIOR, _panelGenislemeDavranisi.value).apply()
    }

    fun setPanelGenislikYuzdesi(yuzde: Float) {
        _panelGenislikYuzdesi.value = yuzde.coerceIn(0.15f, 0.65f)
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_PANEL_WIDTH_PERCENT, _panelGenislikYuzdesi.value).apply()
    }

    fun setPanelYukseklikYuzdesi(yuzde: Float) {
        _panelYukseklikYuzdesi.value = yuzde.coerceIn(0.15f, 0.65f)
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_PANEL_HEIGHT_PERCENT, _panelYukseklikYuzdesi.value).apply()
    }

    fun setOtomatikOynat(otomatik: Boolean) {
        _otomatikOynat.value = otomatik
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_AUTO_PLAY, otomatik).apply()
    }

    fun setGorsellestiriciTipi(tip: Int) {
        _gorsellestiriciTipi.value = tip
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_VISUALIZER_TYPE, tip).apply()
    }

    fun setLargeVisualizer(type: Int) {
        _largeVisualizer.value = type.coerceIn(0, 7)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_VISUALIZER_LARGE, _largeVisualizer.value).apply()
    }

    fun setVisualizerFps(fps: Int) {
        _visualizerFps.value = fps.coerceIn(15, 60)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_VISUALIZER_FPS, _visualizerFps.value).apply()
    }

    fun setAmbiyansGorsellestirici(etkin: Boolean) {
        _ambiyansGorsellestirici.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_AMBIANCE_VISUALIZER, etkin).apply()
    }

    fun setTercihEdilenMuzikUygulamasi(paket: String?) {
        _tercihEdilenMuzikUygulamasi.value = paket
        if (::prefs.isInitialized) prefs.edit().putString(KEY_PREFERRED_MUSIC_APP, paket).apply()
    }

    fun setGeceKarartmaEtkin(etkin: Boolean) {
        _geceKarartmaEtkin.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_NIGHT_DIM_ENABLED, etkin).apply()
    }

    fun setGeceKarartmaSeviyesi(seviye: Float) {
        _geceKarartmaSeviyesi.value = seviye.coerceIn(0.1f, 0.8f)
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_NIGHT_DIM_LEVEL, _geceKarartmaSeviyesi.value).apply()
    }

    fun setDesktopModu(etkin: Boolean) {
        _desktopModu.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_DESKTOP_MODE, etkin).apply()
    }

    fun setDesktopDongudeEtkin(etkin: Boolean) {
        _desktopDongudeEtkin.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_DESKTOP_IN_MODE_CYCLE, etkin).apply()
    }

    fun setWorkspaceSwipeThreshold(threshold: Float) {
        _workspaceSwipeThreshold.value = threshold.coerceIn(0f, 0.6f)
        if (::prefs.isInitialized) prefs.edit().putFloat(KEY_WORKSPACE_SWIPE_THRESHOLD, _workspaceSwipeThreshold.value).apply()
    }

    fun setWorkspaceIndicatorSeconds(seconds: Int) {
        _workspaceIndicatorSeconds.value = seconds.coerceIn(1, 5)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_WORKSPACE_INDICATOR_SECONDS, _workspaceIndicatorSeconds.value).apply()
    }

    fun setBaslangicEkrani(ekran: String) {
        _baslangicEkrani.value = ekran
        if (::prefs.isInitialized) prefs.edit().putString(KEY_STARTUP_SCREEN, ekran).apply()
        requestHomeScreen()
    }

    fun setFloatingButtonModu(mod: String) {
        _floatingButtonModu.value = mod
        if (::prefs.isInitialized) prefs.edit().putString(KEY_FLOATING_BUTTON_MODE, mod).apply()
    }

    fun setFloatingButtonBoyutu(boyut: Int) {
        _floatingButtonBoyutu.value = boyut.coerceIn(60, 120)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_FLOATING_BUTTON_SIZE, _floatingButtonBoyutu.value).apply()
    }

    fun saveFloatingButtonPosition(x: Int, y: Int) {
        if (::prefs.isInitialized) {
            prefs.edit().putInt(KEY_FLOATING_BUTTON_X, x).putInt(KEY_FLOATING_BUTTON_Y, y).apply()
        }
    }

    fun getFloatingButtonPosition(): Pair<Int, Int>? {
        if (!::prefs.isInitialized) return null
        val x = prefs.getInt(KEY_FLOATING_BUTTON_X, -1)
        val y = prefs.getInt(KEY_FLOATING_BUTTON_Y, -1)
        return if (x != -1 && y != -1) Pair(x, y) else null
    }

    fun shouldShowFloatingButton(isForeground: Boolean): Boolean {
        return when (_floatingButtonModu.value) {
            "always" -> true
            "background_only" -> !isForeground
            else -> false
        }
    }

    fun setHavaDurumuEtkin(etkin: Boolean) {
        _havaDurumuEtkin.value = etkin
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WEATHER_ENABLED, etkin).apply()
    }

    fun setEkolayzirPaketi(paket: String?) {
        _ekolayzirPaketi.value = paket
        if (::prefs.isInitialized) prefs.edit().putString(KEY_EQUALIZER_APP, paket).apply()
    }

    fun setWidgetHizGoster(goster: Boolean) {
        _widgetHizGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_SPEED, goster).apply()
    }

    fun setWidgetPusulaGoster(goster: Boolean) {
        _widgetPusulaGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_COMPASS, goster).apply()
    }

    fun setWidgetMuzikGoster(goster: Boolean) {
        _widgetMuzikGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_MUSIC, goster).apply()
    }

    fun setWidgetSaatGoster(goster: Boolean) {
        _widgetSaatGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_CLOCK, goster).apply()
    }

    fun setWidgetHavaDurumuGoster(goster: Boolean) {
        _widgetHavaDurumuGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_WEATHER, goster).apply()
    }

    fun setWidgetSistemGoster(goster: Boolean) {
        _widgetSistemGoster.value = goster
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_WIDGET_APPS, goster).apply()
    }

    fun setEkranYonu(yon: String) {
        _ekranYonu.value = yon
        if (::prefs.isInitialized) prefs.edit().putString(KEY_SCREEN_ORIENTATION, yon).apply()
    }

    fun setDikeydeSadeceHarita(sadeceHarita: Boolean) {
        _dikeydeSadeceHarita.value = sadeceHarita
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_PORTRAIT_MAP_ONLY, sadeceHarita).apply()
    }

    // --- OsmAnd CarLayoutManager Uyumluluk Metodlari (Turkce karakter yok) ---

    fun getInstance(context: Context): CarLauncherSettings {
        baslat(context)
        return this
    }

    fun isDesktopInModeCycleEnabled(): Boolean {
        return _desktopDongudeEtkin.value
    }

    fun getStartupScreen(): String {
        return _baslangicEkrani.value
    }

    fun isAmbianceVisualizerEnabled(): Boolean {
        return _ambiyansGorsellestirici.value
    }

    fun getEffectiveDockPosition(isPortrait: Boolean): String {
        return if (isPortrait) "bottom" else _dockKonumu.value
    }

    fun getEffectiveDockSize(isPortrait: Boolean): Int {
        return _dockBoyutu.value
    }

    fun getWidgetPanelWidthPercent(): Float {
        return _panelGenislikYuzdesi.value
    }

    fun getWidgetPanelHeightPortrait(): Float = _panelYukseklikYuzdesi.value

    fun getPortraitExpansion(): String {
        return "expand_up"
    }

    fun getLandscapeExpansion(): String {
        return if (_panelKonumu.value == "left") "expand_right" else "expand_left"
    }

    fun setWidgetPanelWidthPercent(percent: Float, persist: Boolean) {
        val clamped = percent.coerceIn(0.15f, 0.65f)
        _panelGenislikYuzdesi.value = clamped
        if (persist && ::prefs.isInitialized) {
            prefs.edit().putFloat(KEY_PANEL_WIDTH_PERCENT, clamped).apply()
        }
    }

    fun setWidgetPanelHeightPortrait(percent: Float, persist: Boolean) {
        val clamped = percent.coerceIn(0.15f, 0.65f)
        _panelYukseklikYuzdesi.value = clamped
        if (persist && ::prefs.isInitialized) prefs.edit().putFloat(KEY_PANEL_HEIGHT_PERCENT, clamped).apply()
    }
}
