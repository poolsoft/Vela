package app.vela.carlauncher.media

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.vela.carlauncher.media.adapters.AndroidMediaSessionAdapter
import app.vela.carlauncher.media.adapters.BaseMediaAdapter
import app.vela.carlauncher.media.adapters.InternalPlayerAdapter
import app.vela.carlauncher.media.adapters.UniversalBluetoothAdapter
import app.vela.carlauncher.media.adapters.XyAutoMusicAdapter
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Merkezi Otomotiv Muzik Yoneticisi (MusicManager).
 * OsmAnd ve CoMaps ekosisteminin merkezidir.
 * - Dahili (InternalMusicPlayer) ve Harici (Spotify, Bluetooth, Teyp) kaynaklari yonetir.
 * - TTS Ducking: Navigasyon sesli yonlendirmesi konusurken muzigin sesini kisar, bitince eski haline getirir.
 * - Reactive StateFlow ile Compose ve AndroidView UI'larina anlik akis saglar.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class MusicManager private constructor(private val context: Context) : InternalMusicPlayer.PlaybackListener {

    companion object {
        private const val TAG = "MusicManager"

        @Volatile
        private var instance: MusicManager? = null

        fun getInstance(context: Context): MusicManager {
            return instance ?: synchronized(this) {
                instance ?: MusicManager(context.applicationContext).also { instance = it }
            }
        }
    }

    interface MusicUIListener {
        fun onParcaDegisti(medya: MedyaParcasi)
        fun onCalmaDurumuDegisti(caliyor: Boolean)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val anaHandler = Handler(Looper.getMainLooper())

    val internalPlayer: InternalMusicPlayer = InternalMusicPlayer.getInstance(context)
    val internalAdapter: InternalPlayerAdapter = InternalPlayerAdapter(context, internalPlayer)
    val mediaSessionAdapter: AndroidMediaSessionAdapter = AndroidMediaSessionAdapter { senkronizeEtMedyaDurumu() }
    val bluetoothAdapter: UniversalBluetoothAdapter = UniversalBluetoothAdapter(context) { senkronizeEtMedyaDurumu() }
    val xyAutoAdapter: XyAutoMusicAdapter = XyAutoMusicAdapter(context) { senkronizeEtMedyaDurumu() }

    val hcnMusicAdapter = app.vela.carlauncher.media.adapters.HcnMusicAdapter(context)
    val hcnRadioAdapter = app.vela.carlauncher.media.adapters.HeadUnitRadioAdapter(context, false) { senkronizeEtMedyaDurumu() }
    val xyRadioAdapter = app.vela.carlauncher.media.adapters.HeadUnitRadioAdapter(context, true) { senkronizeEtMedyaDurumu() }

    private val adapters = listOf<BaseMediaAdapter>(
        internalAdapter,
        mediaSessionAdapter,
        bluetoothAdapter,
        xyAutoAdapter, hcnMusicAdapter, hcnRadioAdapter, xyRadioAdapter
    )

    private val listeners = CopyOnWriteArrayList<MusicUIListener>()

    enum class KaynakTipi {
        INTERNAL,
        EXTERNAL
    }

    private val focusPrefs = context.getSharedPreferences("vela_music_focus", Context.MODE_PRIVATE)
    private var manualSelection = false
    private var publishing = false
    private var controllers = emptyList<MediaController>()
    private val sessionCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private val competingPlaying = mutableSetOf<String>()
    private var pinnedSessionPackage: String? = null
    private var userCommandVersion = 0
    private var startupScheduled = false
    private var userPaused = false
    private val startupSource = focusPrefs.getString("last_source", "internal") ?: "internal"
    private val startupWasPlaying = focusPrefs.getBoolean("was_playing", false)
    private val _sourceStatus = MutableStateFlow<String?>(null)
    val sourceStatus = _sourceStatus.asStateFlow()
    private var selectedAdapter: BaseMediaAdapter = internalAdapter
    private var aktifKaynak: KaynakTipi = KaynakTipi.INTERNAL

    private val _medyaDurumu = MutableStateFlow(
        MedyaParcasi(
            baslik = "Müzik Seçilmedi",
            sanatci = "Dokunun veya Çalın",
            caliyorMu = false
        )
    )
    val medyaDurumu: StateFlow<MedyaParcasi> = _medyaDurumu.asStateFlow()

    // TTS Ducking (Navigasyon sirasinda ses kisma)
    private var duckingAktifMi = false
    private var oncekiMuzikSesi = -1
    private var duckedVolume = -1
    private val duckGeriYuklemeGorevi = Runnable {
        sesiEskiHalineGetir()
    }

    init {
        internalPlayer.setListener(this)
        senkronizeEtMedyaDurumu()
    }

    fun addListener(l: MusicUIListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: MusicUIListener) {
        listeners.remove(l)
    }

    fun onOturumlarYenilendi(sessions: List<MediaController>) {
        controllers = sessions.filter { it.packageName != context.packageName }
        sessionCallbacks.keys.filter { it !in controllers }.forEach { controller ->
            sessionCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
            competingPlaying.remove(controller.packageName)
        }
        controllers.filter { it !in sessionCallbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
                    if (!playing) competingPlaying.remove(controller.packageName)
                    if (manualSelection && playing && controller.packageName != sourcePackage(selectedAdapter)) {
                        if (competingPlaying.add(controller.packageName)) {
                            if (((state?.actions ?: 0L) and android.media.session.PlaybackState.ACTION_PAUSE) != 0L)
                                controller.transportControls.pause()
                            else _sourceStatus.value = context.getString(app.vela.R.string.car_source_conflict)
                        }
                    } else if (!manualSelection) {
                        onOturumlarYenilendi(controllers)
                    }
                }
            }
            sessionCallbacks[controller] = callback
            controller.registerCallback(callback, anaHandler)
            if (manualSelection) callback.onPlaybackStateChanged(controller.playbackState)
        }
        val preferred = pinnedSessionPackage
            ?: app.vela.carlauncher.settings.CarLauncherSettings.tercihEdilenMuzikUygulamasi.value
        val candidate = if (manualSelection && pinnedSessionPackage != null) {
            controllers.firstOrNull { it.packageName == pinnedSessionPackage }
        } else controllers.firstOrNull { it.packageName == preferred && it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.packageName == preferred }
            ?: controllers.firstOrNull()
        mediaSessionAdapter.setController(candidate)
        senkronizeEtMedyaDurumu()
    }

    private fun sourceKey(source: BaseMediaAdapter): String = when (source) {
        internalAdapter -> "internal"
        mediaSessionAdapter -> "session:" + (pinnedSessionPackage ?: mediaSessionAdapter.getPackageName())
        bluetoothAdapter -> "bluetooth"
        xyAutoAdapter -> "xy_music"
        hcnMusicAdapter -> "hcn_music"
        hcnRadioAdapter -> "hcn_radio"
        xyRadioAdapter -> "xy_radio"
        else -> "internal"
    }

    private fun sourcePackage(source: BaseMediaAdapter): String? = when (source) {
        mediaSessionAdapter -> mediaSessionAdapter.getPackageName().ifBlank { null }
        hcnMusicAdapter -> hcnMusicAdapter.packageName
        hcnRadioAdapter -> hcnRadioAdapter.packageName
        xyRadioAdapter -> xyRadioAdapter.packageName
        else -> null
    }

    /** One owner for source changes, including a player still preparing asynchronously. */
    private fun requestSmartFocus(source: BaseMediaAdapter) {
        val wasPublishing = publishing
        publishing = true
        try {
        val targetPackage = sourcePackage(source)
        val paused = mutableSetOf<String>()
        if (source !== internalAdapter) internalPlayer.duraklat()
        adapters.filter { it !== source && it !== internalAdapter && it.caliyorMu() }.forEach {
            val pkg = sourcePackage(it)
            if ((pkg == null || pkg != targetPackage) && paused.add(pkg ?: sourceKey(it))) it.duraklat()
        }
        controllers.filter { it.packageName != targetPackage &&
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }.forEach { controller ->
            if (paused.add(controller.packageName)) controller.transportControls.pause()
        }
        selectedAdapter = source
        aktifKaynak = if (source === internalAdapter) KaynakTipi.INTERNAL else KaynakTipi.EXTERNAL
        hcnRadioAdapter.select(source === hcnRadioAdapter)
        xyRadioAdapter.select(source === xyRadioAdapter)
        } finally { publishing = wasPublishing }
    }

    private fun userCommand() {
        userCommandVersion++
        manualSelection = true
        userPaused = false
        _sourceStatus.value = null
    }

    fun cancelPendingAutomaticPlayback() {
        userCommandVersion++
    }

    fun allowAutomaticSourceTracking() {
        userCommandVersion++
        focusPrefs.edit().putBoolean("auto_follow", true).apply()
        manualSelection = false
        pinnedSessionPackage = null
        onOturumlarYenilendi(controllers)
    }

    fun oynatDahiliParca(parca: SesParcasi, liste: List<SesParcasi> = emptyList()) {
        userCommand()
        pinnedSessionPackage = null
        requestSmartFocus(internalAdapter)
        internalPlayer.parcaAtaVeOynat(parca, liste)
        senkronizeEtMedyaDurumu()
    }

    fun restoreSavedPlayback(library: List<SesParcasi>, autoPlay: Boolean): Boolean {
        // Restore metadata without racing a phone which connects after launcher startup.
        val restored = internalPlayer.restoreSavedPlayback(library, false)
        if (!startupScheduled) {
            startupScheduled = true
            val version = userCommandVersion
            if (autoPlay && startupWasPlaying && version == 0) {
                anaHandler.postDelayed({
                    if (version == userCommandVersion && app.vela.carlauncher.settings.CarLauncherSettings.otomatikOynat.value &&
                        adapters.none { it.caliyorMu() }) {
                        val profile = focusPrefs.getString("startup_source", "last") ?: "last"
                        val key = if (profile == "last") startupSource else profile
                        selectSource(key, play = true, fromUser = false)
                    }
                }, focusPrefs.getInt("bt_wait_seconds", 8).coerceIn(3, 15) * 1000L)
            }
        }
        return restored
    }

    fun availableSources(): List<Pair<String, String>> = buildList {
        add("internal" to context.getString(app.vela.R.string.car_source_internal))
        controllers.distinctBy { it.packageName }.forEach { controller ->
            val label = runCatching { context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(controller.packageName, 0)).toString() }.getOrDefault(controller.packageName)
            add("session:${controller.packageName}" to label)
        }
        if (bluetoothAdapter.aktifMi()) add("bluetooth" to bluetoothAdapter.kaynakAdi())
        if (xyAutoAdapter.aktifMi()) add("xy_music" to xyAutoAdapter.kaynakAdi())
        if (hcnMusicAdapter.installed()) add("hcn_music" to hcnMusicAdapter.kaynakAdi())
        if (hcnRadioAdapter.packageName != null) add("hcn_radio" to hcnRadioAdapter.kaynakAdi())
        if (xyRadioAdapter.packageName != null) add("xy_radio" to xyRadioAdapter.kaynakAdi())
    }

    fun selectSource(key: String, play: Boolean = playbackAdapter().caliyorMu(), fromUser: Boolean = true) {
        if (fromUser) userCommand()
        val source = when {
            key == "internal" -> internalAdapter
            key.startsWith("session:") -> mediaSessionAdapter
            key == "bluetooth" -> bluetoothAdapter
            key == "xy_music" -> xyAutoAdapter
            key == "hcn_music" -> hcnMusicAdapter
            key == "hcn_radio" -> hcnRadioAdapter
            key == "xy_radio" -> xyRadioAdapter
            else -> return
        }
        val controller = if (key.startsWith("session:")) controllers.firstOrNull { it.packageName == key.substringAfter(":") } else null
        val ready = when (source) {
            internalAdapter -> !play || internalPlayer.anlikParca.value != null
            mediaSessionAdapter -> controller != null
            hcnRadioAdapter -> hcnRadioAdapter.packageName != null
            xyRadioAdapter -> xyRadioAdapter.packageName != null
            else -> source.aktifMi()
        }
        if (!ready || (source === bluetoothAdapter && !bluetoothAdapter.canControl)) {
            _sourceStatus.value = context.getString(app.vela.R.string.car_source_unavailable)
            return
        }
        // Pin before callback delivery; a stale playing event cannot take the selection back.
        manualSelection = true
        if (source === mediaSessionAdapter) {
            if (playbackAdapter() === mediaSessionAdapter && mediaSessionAdapter.getController() != controller)
                mediaSessionAdapter.duraklat()
            pinnedSessionPackage = controller?.packageName
            mediaSessionAdapter.setController(controller)
        } else {
            pinnedSessionPackage = sourcePackage(source)
            if (pinnedSessionPackage != null)
                mediaSessionAdapter.setController(controllers.firstOrNull { it.packageName == pinnedSessionPackage })
        }
        publishing = true
        try { requestSmartFocus(source) } finally { publishing = false }
        focusPrefs.edit().putString("last_source", key).apply()
        if (play && !playbackAdapter().caliyorMu()) {
            playbackAdapter().oynat()
            val version = userCommandVersion
            anaHandler.postDelayed({
                if (version == userCommandVersion && selectedAdapter === source && !playbackAdapter().caliyorMu())
                    _sourceStatus.value = context.getString(app.vela.R.string.car_source_no_confirmation)
            }, 5000L)
        }
        senkronizeEtMedyaDurumu()
    }

    fun isInternalPlayback(): Boolean = selectedAdapter === internalAdapter

    fun playQueueTrack(track: SesParcasi) {
        val index = internalPlayer.kuyruk.value.indexOfFirst { it.libraryKey() == track.libraryKey() }
        if (index < 0) return
        userCommand()
        pinnedSessionPackage = null
        requestSmartFocus(internalAdapter)
        internalPlayer.indexeGoreOynat(index)
        senkronizeEtMedyaDurumu()
    }

    fun audioSessionId(): Int = if (isInternalPlayback()) internalPlayer.audioSessionId else 0

    private fun playbackAdapter(): BaseMediaAdapter = if (selectedAdapter !== mediaSessionAdapter &&
        sourcePackage(selectedAdapter) != null && sourcePackage(selectedAdapter) == mediaSessionAdapter.getPackageName())
        mediaSessionAdapter else selectedAdapter

    fun canControl(): Boolean = when (selectedAdapter) {
        bluetoothAdapter -> bluetoothAdapter.aktifMi() && bluetoothAdapter.canControl
        mediaSessionAdapter -> mediaSessionAdapter.aktifMi()
        else -> true
    }

    fun canSeek(): Boolean = isInternalPlayback() ||
        (playbackAdapter() === mediaSessionAdapter &&
            ((mediaSessionAdapter.getController()?.playbackState?.actions ?: 0L) and
                android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L)

    fun currentPlayback(): MedyaParcasi = _medyaDurumu.value.copy(
        anlikKonumMs = playbackAdapter().anlikKonumMs(), toplamSureMs = playbackAdapter().toplamSureMs(),
        caliyorMu = playbackAdapter().caliyorMu()
    )
    fun oynat() {
        userCommand()
        if (!canControl() || (isInternalPlayback() && internalPlayer.anlikParca.value == null)) {
            _sourceStatus.value = context.getString(app.vela.R.string.car_source_unavailable)
            return
        }
        publishing = true
        try { requestSmartFocus(selectedAdapter) } finally { publishing = false }
        playbackAdapter().oynat()
        senkronizeEtMedyaDurumu()
    }
    fun duraklat() {
        userCommand()
        userPaused = true
        internalPlayer.duraklat() // Also cancels a pending focus-resume from a former local source.
        if (selectedAdapter !== internalAdapter) playbackAdapter().duraklat()
        focusPrefs.edit().putBoolean("was_playing", false).apply()
        senkronizeEtMedyaDurumu()
    }
    fun oynatDuraklat() { if (playbackAdapter().caliyorMu()) duraklat() else oynat() }
    fun sonraki() { userCommand(); playbackAdapter().sonraki(); senkronizeEtMedyaDurumu() }
    fun onceki() { userCommand(); playbackAdapter().onceki(); senkronizeEtMedyaDurumu() }
    fun konumaGit(konumMs: Long) { playbackAdapter().konumaGit(konumMs.coerceAtLeast(0L)) }

    /**
     * Direksiyon kumandasi veya donanim medya tuslarini isler.
     */
    fun handleHardwareMediaKey(keyCode: Int): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                oynat()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                duraklat()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                oynatDuraklat()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                sonraki()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                onceki()
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                duraklat()
                true
            }
            else -> false
        }
    }

    fun setKarisikCal(aktif: Boolean) {
        internalPlayer.setKarisikCal(aktif)
    }

    fun setTekrarModu(mod: Int) {
        internalPlayer.setTekrarModu(mod)
    }

    private fun senkronizeEtMedyaDurumu() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            anaHandler.post { senkronizeEtMedyaDurumu() }
            return
        }
        if (publishing) return
        publishing = true
        try {
        if (!manualSelection && focusPrefs.getBoolean("auto_follow", true)) {
            // A connection/metadata event alone is never a takeover request.
            val playing = adapters.firstOrNull { it.caliyorMu() }
            if (playing != null && !selectedAdapter.caliyorMu()) {
                userCommandVersion++ // A real playback event also supersedes a delayed startup request.
                requestSmartFocus(playing)
            }
        }
        // A dedicated OEM source may publish its truthful state through MediaSession.
        val aktifAdaptor = playbackAdapter()
        val yeniMedya = MedyaParcasi(
            baslik = aktifAdaptor.baslik().ifBlank { "Müzik Seçilmedi" },
            sanatci = aktifAdaptor.sanatci().ifBlank { "Sanatçı" },
            albumKapagi = aktifAdaptor.albumKapagi(),
            caliyorMu = aktifAdaptor.caliyorMu(),
            toplamSureMs = aktifAdaptor.toplamSureMs(),
            anlikKonumMs = aktifAdaptor.anlikKonumMs(),
            paketAdi = if (aktifAdaptor is AndroidMediaSessionAdapter) aktifAdaptor.getPackageName() else ""
        )

        _medyaDurumu.value = yeniMedya
        if (userCommandVersion > 0 || yeniMedya.caliyorMu) {
            focusPrefs.edit().putString("last_source", sourceKey(selectedAdapter))
                .putBoolean("was_playing", yeniMedya.caliyorMu && !userPaused).apply()
        }
        listeners.forEach { listener ->
            listener.onParcaDegisti(yeniMedya)
            listener.onCalmaDurumuDegisti(yeniMedya.caliyorMu)
        }
        } finally { publishing = false }
    }

    // TTS Ducking (Navigasyon sesli yonlendirmesinde muzik sesini %20'ye kisip geri dondurme)
    fun onTtsBasladi() {
        try {
            anaHandler.removeCallbacks(duckGeriYuklemeGorevi)
            if (!duckingAktifMi) {
                val mevcutSes = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                oncekiMuzikSesi = mevcutSes
                val kisilmisSes = (mevcutSes * 0.2f).toInt().coerceAtLeast(0)
                duckedVolume = kisilmisSes
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, kisilmisSes, 0)
                duckingAktifMi = true
                Log.d(TAG, "TTS basladi: Muzik sesi $mevcutSes -> $kisilmisSes yapildi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS ducking hatasi: ${e.message}")
        }
    }

    fun onTtsBitti() {
        anaHandler.removeCallbacks(duckGeriYuklemeGorevi)
        // Talimat bitince 800ms sonra yumusakca geri yukle
        anaHandler.postDelayed(duckGeriYuklemeGorevi, 800)
    }

    private fun sesiEskiHalineGetir() {
        try {
            if (duckingAktifMi && oncekiMuzikSesi >= 0) {
                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == duckedVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, oncekiMuzikSesi, 0)
                duckingAktifMi = false
                Log.d(TAG, "TTS bitti: Muzik sesi $oncekiMuzikSesi seviyesine geri yuklendi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ses geri yukleme hatasi: ${e.message}")
        }
    }

    override fun onParcaDegisti(parca: SesParcasi?) {
        senkronizeEtMedyaDurumu()
    }

    override fun onCalmaDurumuDegisti(caliyor: Boolean) {
        senkronizeEtMedyaDurumu()
    }

    override fun onKuyrukGuncellendi(kuyruk: List<SesParcasi>) {
        // UI kuyruk guncellendi
    }
}
