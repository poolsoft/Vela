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
import kotlinx.coroutines.*
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
        fun refreshIfCreated() { instance?.let { manager ->
            manager.senkronizeEtMedyaDurumu()
            MediaNotificationListener.refreshAndReconnect(manager.context)
        } }
        fun onSpeechStateChanged(speaking: Boolean) { instance?.let { manager ->
            manager.ttsSpeaking = speaking
            manager.senkronizeEtMedyaDurumu()
        } }
    }

    interface MusicUIListener {
        fun onParcaDegisti(medya: MedyaParcasi)
        fun onCalmaDurumuDegisti(caliyor: Boolean)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val anaHandler = Handler(Looper.getMainLooper())

    val internalPlayer: InternalMusicPlayer = InternalMusicPlayer.getInstance(context)
    val internalAdapter: InternalPlayerAdapter = InternalPlayerAdapter(context, internalPlayer) { senkronizeEtMedyaDurumu() }
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

    // Lazy: the same 3.4 s first-parse cost as InternalMusicPlayer applies to this file, and the
    // ctor runs on the media service's main thread. VelaApp pre-warms it on an IO thread.
    private val focusPrefs by lazy { context.getSharedPreferences("vela_music_focus", Context.MODE_PRIVATE) }
    private val focusScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val focusWriteDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var focusState: Map<String, Any?> = emptyMap()
    private val focusLoaded = focusScope.async(Dispatchers.IO) { focusPrefs.all }
    private val focusPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        focusScope.launch {
            val updated = withContext(Dispatchers.IO) { prefs.all }
            focusState = updated
            senkronizeEtMedyaDurumu()
        }
    }
    private fun saveFocus(vararg values: Pair<String, Any>) {
        if (values.all { (key, value) -> focusState[key] == value }) return
        focusState = focusState + values
        focusScope.launch(focusWriteDispatcher) {
            val editor = focusPrefs.edit()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.apply()
        }
    }
    private val playbackOwnership = PlaybackOwnership()
    private val focusPolicy = SmartFocusPolicy()
    private val focusRecheck = Runnable { senkronizeEtMedyaDurumu() }
    private var ttsSpeaking = runCatching { (context.applicationContext as? app.vela.VelaApp)?.voiceGuide?.get()?.isSpeaking == true }.getOrDefault(false)
    private var phoneCall = false
    private val callReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
            val state = intent?.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)
            phoneCall = state != null && state != android.telephony.TelephonyManager.EXTRA_STATE_IDLE
            senkronizeEtMedyaDurumu()
        }
    }
    private fun isCallActive(): Boolean = phoneCall || audioManager.mode in setOf(
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE)

    fun isSourceTrackingEnabled(key: String): Boolean {
        return focusState["follow:$key"] as? Boolean ?: true
    }
    fun setSourceTracking(key: String, enabled: Boolean) { saveFocus("follow:$key" to enabled); _sourcesRevision.value++; senkronizeEtMedyaDurumu() }
    fun isSourceLocked(): Boolean = manualSelection
    fun isAutomaticTracking(): Boolean = !manualSelection && focusState["auto_follow"] != false
    private val _sourcesRevision = MutableStateFlow(0)
    val sourcesRevision = _sourcesRevision.asStateFlow()
    fun selectedSourceKey(): String = sourceKey(selectedAdapter)

    private fun sourceSnapshots(): List<SmartFocusPolicy.Source> = buildList {
        adapters.filter { it !== mediaSessionAdapter }.forEach { adapter ->
            val key = sourceKey(adapter)
            val linked = sourcePackage(adapter)?.let { pkg -> controllers.firstOrNull { it.packageName == pkg } }
            val state = linked?.playbackState?.state
            add(SmartFocusPolicy.Source(key, if (linked != null) state == android.media.session.PlaybackState.STATE_PLAYING else adapter.caliyorMu(),
                isSourceTrackingEnabled(key), adapter === internalAdapter || adapter.aktifMi() || linked != null,
                state == android.media.session.PlaybackState.STATE_BUFFERING || state == android.media.session.PlaybackState.STATE_CONNECTING))
        }
        val dedicated = adapters.filter { it !== mediaSessionAdapter }.mapNotNull { sourcePackage(it) }.toSet()
        controllers.filter { it.packageName !in dedicated }.forEach { controller ->
            val state = controller.playbackState?.state
            val key = "session:${controller.packageName}"
            add(SmartFocusPolicy.Source(key, state == android.media.session.PlaybackState.STATE_PLAYING,
                isSourceTrackingEnabled(key), true,
                state == android.media.session.PlaybackState.STATE_BUFFERING || state == android.media.session.PlaybackState.STATE_CONNECTING))
        }
    }

    private fun adoptSource(key: String) {
        val source = when {
            key == "internal" -> internalAdapter
            key == "bluetooth" -> bluetoothAdapter
            key == "xy_music" -> xyAutoAdapter
            key == "hcn_music" -> hcnMusicAdapter
            key == "hcn_radio" -> hcnRadioAdapter
            key == "xy_radio" -> xyRadioAdapter
            key.startsWith("session:") -> mediaSessionAdapter
            else -> return
        }
        pinnedSessionPackage = if (source === mediaSessionAdapter) key.removePrefix("session:") else sourcePackage(source)
        mediaSessionAdapter.setController(controllers.firstOrNull { it.packageName == pinnedSessionPackage })
        selectedAdapter = source
        aktifKaynak = if (source === internalAdapter) KaynakTipi.INTERNAL else KaynakTipi.EXTERNAL
    }

    private val sourceLabels = mutableMapOf<String, String>()
    private fun sourceLabel(source: BaseMediaAdapter): String {
        val pkg = sourcePackage(source) ?: return source.kaynakAdi()
        return sourceLabels.getOrPut(pkg) { runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(source.kaynakAdi()) }
    }
    private var manualSelection = false
    private var publishing = false
    private var controllers = emptyList<MediaController>()
    private val sessionCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var pinnedSessionPackage: String? = null
    private var userCommandVersion = 0
    private var startupScheduled = false
    private var userPaused = false
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
        runCatching {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION")
                val state = runCatching { (context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)?.callState }.getOrNull()
                phoneCall = state != null && state != android.telephony.TelephonyManager.CALL_STATE_IDLE
            }
            val filter = android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            if (android.os.Build.VERSION.SDK_INT >= 33) context.registerReceiver(callReceiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(callReceiver, filter)
        }
        MediaNotificationListener.refreshAndReconnect(context)
        internalPlayer.setListener(this)
        senkronizeEtMedyaDurumu()
        focusScope.launch {
            focusState = focusLoaded.await() + focusState
            focusPrefs.registerOnSharedPreferenceChangeListener(focusPreferenceListener)
            senkronizeEtMedyaDurumu()
        }
    }

    fun addListener(l: MusicUIListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: MusicUIListener) {
        listeners.remove(l)
    }

    fun onOturumlarYenilendi(sessions: List<MediaController>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            anaHandler.post { onOturumlarYenilendi(sessions) }
            return
        }
        controllers = sessions.filterNot { LauncherMediaPeers.contains(context, it.packageName) }
        _sourcesRevision.value++
        app.vela.util.FileLogger.i(TAG, "Media sessions: ${controllers.map { it.packageName + ":" + it.playbackState?.state }}")

        sessionCallbacks.keys.filter { it !in controllers }.forEach { controller ->
            sessionCallbacks.remove(controller)?.let { controller.unregisterCallback(it) }
        }
        controllers.filter { it !in sessionCallbacks }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    app.vela.util.FileLogger.i(TAG, "Media state package=${controller.packageName} state=${state?.state} actions=${state?.actions}")
                    senkronizeEtMedyaDurumu()
                }
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) { senkronizeEtMedyaDurumu() }
                override fun onSessionDestroyed() { onOturumlarYenilendi(controllers.filter { it != controller }) }
            }
            sessionCallbacks[controller] = callback
            controller.registerCallback(callback, anaHandler)
            if (manualSelection) callback.onPlaybackStateChanged(controller.playbackState)
        }
        // Keep the chosen session stable; source policy observes every controller separately.
        val selectedPackage = if (selectedAdapter === mediaSessionAdapter) pinnedSessionPackage else sourcePackage(selectedAdapter)
        mediaSessionAdapter.setController(controllers.firstOrNull { it.packageName == selectedPackage })
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
        mediaSessionAdapter -> mediaSessionAdapter.getPackageName().takeIf { it.isNotBlank() } ?: pinnedSessionPackage
        bluetoothAdapter -> controllers.firstOrNull { it.packageName.contains("bluetooth", ignoreCase = true) }?.packageName
        hcnMusicAdapter -> hcnMusicAdapter.packageName
        hcnRadioAdapter -> hcnRadioAdapter.packageName
        xyRadioAdapter -> xyRadioAdapter.packageName
        else -> null
    }

    /** One owner for source changes, including a player still preparing asynchronously. */
    private fun releaseOwnerFor(target: String) {
        playbackOwnership.start(target, sourceKey(selectedAdapter)) { previous ->
            if (previous == "internal") {
                // Includes asynchronous preparation and a pending transient-focus resume.
                internalPlayer.duraklat()
            } else {
                val adapter = when (previous) {
                    "bluetooth" -> bluetoothAdapter
                    "xy_music" -> xyAutoAdapter
                    "hcn_music" -> hcnMusicAdapter
                    "hcn_radio" -> hcnRadioAdapter
                    "xy_radio" -> xyRadioAdapter
                    else -> null
                }
                val pkg = if (previous.startsWith("session:")) previous.removePrefix("session:")
                    else adapter?.let { sourcePackage(it) }
                val controller = controllers.firstOrNull { it.packageName == pkg }
                if (controller != null && controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) {
                    val actions = controller.playbackState?.actions ?: 0L
                    if ((actions and (android.media.session.PlaybackState.ACTION_PAUSE or
                            android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0L) controller.transportControls.pause()
                    else _sourceStatus.value = context.getString(app.vela.R.string.car_source_conflict)
                } else if (adapter?.caliyorMu() == true) adapter.duraklat()
            }
        }
    }

    private fun requestSmartFocus(source: BaseMediaAdapter) {
        val wasPublishing = publishing
        publishing = true
        try {
        // Only release the previous music owner, never all system/call/navigation sessions.
        releaseOwnerFor(sourceKey(source))
        selectedAdapter = source
        aktifKaynak = if (source === internalAdapter) KaynakTipi.INTERNAL else KaynakTipi.EXTERNAL
        } finally { publishing = wasPublishing }
    }

    private fun userCommand() {
        userCommandVersion++
        userPaused = false
        _sourceStatus.value = null
    }

    fun cancelPendingAutomaticPlayback() {
        userCommandVersion++
    }

    fun allowAutomaticSourceTracking() {
        userCommandVersion++
        saveFocus("auto_follow" to true)
        manualSelection = false
        focusPolicy.resumeAutomatic()
        onOturumlarYenilendi(controllers)
    }

    fun oynatDahiliParca(parca: SesParcasi, liste: List<SesParcasi> = emptyList()) {
        userCommand()
        manualSelection = false
        focusPolicy.selected("internal", sourceSnapshots())
        pinnedSessionPackage = null
        requestSmartFocus(internalAdapter)
        internalPlayer.parcaAtaVeOynat(parca, liste)
        senkronizeEtMedyaDurumu()
    }

    suspend fun restoreSavedPlayback(library: List<SesParcasi>, autoPlay: Boolean): Boolean {
        if (app.vela.BuildConfig.DIAGNOSTIC_MUSIC_DISABLED) return true
        // Restore metadata without racing a phone which connects after launcher startup.
        val restored = internalPlayer.restoreSavedPlayback(library, false)
        val savedFocus = focusLoaded.await()
        if (!startupScheduled) {
            startupScheduled = true
            val version = userCommandVersion
            if (autoPlay && savedFocus["was_playing"] == true && version == 0) {
                anaHandler.postDelayed({
                    if (version == userCommandVersion && app.vela.carlauncher.settings.CarLauncherSettings.otomatikOynat.value &&
                        adapters.none { it.caliyorMu() }) {
                        val profile = savedFocus["startup_source"] as? String ?: "last"
                        val key = if (profile == "last") savedFocus["last_source"] as? String ?: "internal" else profile
                        selectSource(key, play = true, fromUser = false)
                    }
                }, (savedFocus["bt_wait_seconds"] as? Int ?: 8).coerceIn(3, 15) * 1000L)
            }
        }
        return restored
    }

    data class SourceOption(val key: String, val label: String, val packageName: String?, val kind: String, val playing: Boolean)

    fun sourceOptions(): List<SourceOption> {
        val states = sourceSnapshots().associateBy { it.id }
        return availableSources().map { (key, label) ->
            val adapter = adapters.firstOrNull { sourceKey(it) == key }
            val pkg = if (key.startsWith("session:")) key.removePrefix("session:") else adapter?.let { sourcePackage(it) }
            SourceOption(key, label, pkg, MediaSourceIdentity.kind(key, pkg), states[key]?.playing == true && !isCallActive())
        }
    }

    fun availableSources(): List<Pair<String, String>> = buildList {
        add("internal" to context.getString(app.vela.R.string.car_source_internal))
        val dedicated = adapters.filter { it !== mediaSessionAdapter }.mapNotNull { sourcePackage(it) }.toSet()
        controllers.distinctBy { it.packageName }.filter { it.packageName !in dedicated }.forEach { controller ->
            val label = sourceLabels.getOrPut(controller.packageName) { runCatching { context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(controller.packageName, 0)).toString() }.getOrDefault(controller.packageName) }
            add("session:${controller.packageName}" to label)
        }
        if (bluetoothAdapter.aktifMi() || sourcePackage(bluetoothAdapter) != null) add("bluetooth" to bluetoothAdapter.kaynakAdi())
        if (xyAutoAdapter.aktifMi()) add("xy_music" to xyAutoAdapter.kaynakAdi())
        if (hcnMusicAdapter.installed()) add("hcn_music" to hcnMusicAdapter.kaynakAdi())
        if (hcnRadioAdapter.packageName != null) add("hcn_radio" to hcnRadioAdapter.kaynakAdi())
        if (xyRadioAdapter.packageName != null) add("xy_radio" to xyRadioAdapter.kaynakAdi())
    }

    fun selectSource(key: String, play: Boolean = false, fromUser: Boolean = true, lock: Boolean = false) {
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
            else -> source.aktifMi() || sourcePackage(source)?.let { pkg -> controllers.any { it.packageName == pkg } } == true
        }
        if (!ready || (play && source === bluetoothAdapter && !bluetoothAdapter.canControl && sourcePackage(bluetoothAdapter) == null) || (play && isCallActive())) {
            _sourceStatus.value = context.getString(app.vela.R.string.car_source_unavailable)
            return
        }
        // A normal selection is not a permanent lock. Consume already-playing stale events.
        focusPolicy.selected(key, sourceSnapshots())
        manualSelection = lock
        if (lock) saveFocus("auto_follow" to false)
        publishing = true
        if (play) releaseOwnerFor(key)
        if (source === mediaSessionAdapter) {
            pinnedSessionPackage = controller?.packageName
            mediaSessionAdapter.setController(controller)
        } else {
            pinnedSessionPackage = sourcePackage(source)
            if (pinnedSessionPackage != null)
                mediaSessionAdapter.setController(controllers.firstOrNull { it.packageName == pinnedSessionPackage })
        }
        publishing = true
        try {
            if (play) requestSmartFocus(source)
            else {
                selectedAdapter = source
                aktifKaynak = if (source === internalAdapter) KaynakTipi.INTERNAL else KaynakTipi.EXTERNAL
            }
        } finally { publishing = false }
        saveFocus("last_source" to key)
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
        manualSelection = false
        focusPolicy.selected("internal", sourceSnapshots())
        pinnedSessionPackage = null
        requestSmartFocus(internalAdapter)
        internalPlayer.indexeGoreOynat(index)
        senkronizeEtMedyaDurumu()
    }

    fun audioSessionId(): Int = if (isInternalPlayback()) internalPlayer.audioSessionId else 0

    private fun playbackAdapter(): BaseMediaAdapter = if (selectedAdapter !== mediaSessionAdapter &&
        sourcePackage(selectedAdapter) != null && sourcePackage(selectedAdapter) == mediaSessionAdapter.getPackageName())
        mediaSessionAdapter else selectedAdapter

    private fun sessionAction(action: Long): Boolean =
        ((mediaSessionAdapter.getController()?.playbackState?.actions ?: 0L) and action) != 0L

    fun canControl(): Boolean = !isCallActive() && when (playbackAdapter()) {
        bluetoothAdapter -> bluetoothAdapter.aktifMi() && bluetoothAdapter.canControl
        mediaSessionAdapter -> sessionAction(android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
            if (mediaSessionAdapter.caliyorMu()) android.media.session.PlaybackState.ACTION_PAUSE
            else android.media.session.PlaybackState.ACTION_PLAY)
        else -> true
    }
    fun canSkipNext(): Boolean = !isCallActive() && if (playbackAdapter() === mediaSessionAdapter)
        sessionAction(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT) else canControl()
    fun canSkipPrevious(): Boolean = !isCallActive() && if (playbackAdapter() === mediaSessionAdapter)
        sessionAction(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS) else canControl()

    fun canSeek(): Boolean = isInternalPlayback() ||
        (playbackAdapter() === mediaSessionAdapter &&
            ((mediaSessionAdapter.getController()?.playbackState?.actions ?: 0L) and
                android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L)

    fun currentPlayback(): MedyaParcasi = _medyaDurumu.value.copy(
        anlikKonumMs = playbackAdapter().anlikKonumMs(), toplamSureMs = playbackAdapter().toplamSureMs(),
        caliyorMu = playbackAdapter().caliyorMu() && !isCallActive(), canControl = canControl(),
        canSkipNext = canSkipNext(), canSkipPrevious = canSkipPrevious()
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
        if (selectedAdapter !== internalAdapter && !isCallActive()) playbackAdapter().duraklat()
        saveFocus("was_playing" to false)
        senkronizeEtMedyaDurumu()
    }
    fun oynatDuraklat() { if (playbackAdapter().caliyorMu()) duraklat() else oynat() }
    fun sonraki() { if (canSkipNext()) { userCommand(); playbackAdapter().sonraki(); senkronizeEtMedyaDurumu() } }
    fun onceki() { if (canSkipPrevious()) { userCommand(); playbackAdapter().onceki(); senkronizeEtMedyaDurumu() } }
    fun konumaGit(konumMs: Long) { if (!isCallActive() && canSeek()) playbackAdapter().konumaGit(konumMs.coerceAtLeast(0L)) }

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
        val snapshots = sourceSnapshots()
        val current = sourceKey(selectedAdapter)
        val target = focusPolicy.choose(snapshots, current, focusState["auto_follow"] != false,
            manualSelection, isCallActive() || ttsSpeaking || duckingAktifMi, android.os.SystemClock.elapsedRealtime())
        anaHandler.removeCallbacks(focusRecheck)
        focusPolicy.nextCheckInMs?.let { anaHandler.postDelayed(focusRecheck, it) }
        if (target != current) {
            userCommandVersion++
            val targetPlaying = snapshots.firstOrNull { it.id == target }?.playing == true
            if (targetPlaying) releaseOwnerFor(target)
            app.vela.util.FileLogger.i(TAG, "SmartFocus $current -> $target playing=$targetPlaying")
            adoptSource(target)
        }
        // A dedicated OEM source may publish its truthful state through MediaSession.
        val aktifAdaptor = playbackAdapter()
        val yeniMedya = MedyaParcasi(
            baslik = aktifAdaptor.baslik().ifBlank { "Müzik Seçilmedi" },
            sanatci = aktifAdaptor.sanatci().ifBlank { "Sanatçı" },
            albumKapagi = aktifAdaptor.albumKapagi(),
            caliyorMu = aktifAdaptor.caliyorMu() && !isCallActive(),
            toplamSureMs = aktifAdaptor.toplamSureMs(),
            anlikKonumMs = aktifAdaptor.anlikKonumMs(),
            paketAdi = sourcePackage(selectedAdapter).orEmpty(),
            sourceId = sourceKey(selectedAdapter),
            sourceKind = MediaSourceIdentity.kind(sourceKey(selectedAdapter), sourcePackage(selectedAdapter)),
            sourceLabel = sourceLabel(selectedAdapter),
            canControl = canControl(), canSkipNext = canSkipNext(), canSkipPrevious = canSkipPrevious()
        )

        if (yeniMedya.caliyorMu) playbackOwnership.observedPlaying(yeniMedya.sourceId)
        val mediaChanged = _medyaDurumu.value != yeniMedya
        _medyaDurumu.value = yeniMedya
        if (userCommandVersion > 0 || yeniMedya.caliyorMu) {
            saveFocus("last_source" to sourceKey(selectedAdapter), "was_playing" to (yeniMedya.caliyorMu && !userPaused))
        }
        if (mediaChanged) listeners.forEach { listener ->
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
        anaHandler.postDelayed(focusRecheck, 850)
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
        // A dismissed notification stopped the service; local playback needs it again.
        if (caliyor) runCatching { CarMediaService.baslat(context) }
            .onFailure { app.vela.util.FileLogger.w(TAG, "Media service restart failed", it) }
        senkronizeEtMedyaDurumu()
    }

    override fun onKuyrukGuncellendi(kuyruk: List<SesParcasi>) {
        // UI kuyruk guncellendi
    }
}
