package app.vela.carlauncher.media

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Collections

/**
 * Dahili Muzik Calma Motoru (InternalMusicPlayer).
 * OsmAnd ve CoMaps otomotiv dahili oynaticisi mantiginda yerel MP3/FLAC dosyalarini calar.
 * - MediaPlayer motoru
 * - Audio Focus yonetimi (GPS sesli yonlendirmesi, telefon aramasi)
 * - Calma kuyrugu, karisik calma (Shuffle), tekrar (Repeat: Kapali, Tek, Tumu)
 * - Son kalinan parca ve saniyeyi hatirlama (Persistence)
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class InternalMusicPlayer private constructor(private val context: Context) :
    MediaPlayer.OnPreparedListener,
    MediaPlayer.OnCompletionListener,
    MediaPlayer.OnErrorListener {

    companion object {
        private const val TAG = "InternalMusicPlayer"
        private const val PREFS_NAME = "vela_internal_player_prefs"
        private const val KEY_LAST_PATH = "last_track_path"
        private const val KEY_LAST_POS = "last_track_position"
        private const val KEY_SHUFFLE = "is_shuffle"
        private const val KEY_REPEAT = "repeat_mode"

        @Volatile
        private var instance: InternalMusicPlayer? = null

        fun getInstance(context: Context): InternalMusicPlayer {
            return instance ?: synchronized(this) {
                instance ?: InternalMusicPlayer(context.applicationContext).also { instance = it }
            }
        }
    }

    interface PlaybackListener {
        fun onParcaDegisti(parca: SesParcasi?)
        fun onCalmaDurumuDegisti(caliyor: Boolean)
        fun onKuyrukGuncellendi(kuyruk: List<SesParcasi>)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val anaHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    val audioSessionId: Int get() = runCatching { mediaPlayer?.audioSessionId ?: 0 }.getOrDefault(0)
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _calismaListesi = mutableListOf<SesParcasi>()
    private val _calmaKuyrugu = mutableListOf<SesParcasi>()
    private var suAnkiIndex = -1

    private var lastSavedPosition = -1L
    private var pendingPlay = false
    private var recordNextStart = false
    private var hazirMi = false
    private var odakGeriGelinceCal = false
    private var bekleyenSeekKonumu = 0L

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    fun clearPlaybackError() { _playbackError.value = null }

    // StateFlows
    private val _anlikParca = MutableStateFlow<SesParcasi?>(null)
    val anlikParca: StateFlow<SesParcasi?> = _anlikParca.asStateFlow()

    private val _caliyorMu = MutableStateFlow(false)
    val caliyorMu: StateFlow<Boolean> = _caliyorMu.asStateFlow()

    private val _anlikKonumMs = MutableStateFlow(0L)
    val anlikKonumMs: StateFlow<Long> = _anlikKonumMs.asStateFlow()

    private val _toplamSureMs = MutableStateFlow(0L)
    val toplamSureMs: StateFlow<Long> = _toplamSureMs.asStateFlow()

    private val _karisikCal = MutableStateFlow(false)
    val karisikCal: StateFlow<Boolean> = _karisikCal.asStateFlow()

    private val _tekrarModu = MutableStateFlow(0) // 0: Kapali, 1: Tek Parca, 2: Tum Liste
    val tekrarModu: StateFlow<Int> = _tekrarModu.asStateFlow()

    private val _kuyruk = MutableStateFlow<List<SesParcasi>>(emptyList())
    val kuyruk: StateFlow<List<SesParcasi>> = _kuyruk.asStateFlow()

    private var listener: PlaybackListener? = null

    // Ilerleme (Progress) Zamanlayicisi
    private val ilerlemeGorevi = object : Runnable {
        override fun run() {
            if (_caliyorMu.value && mediaPlayer != null && hazirMi) {
                try {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    _anlikKonumMs.value = pos
                    if (kotlin.math.abs(pos - lastSavedPosition) >= 5000) {
                        prefs.edit().putLong(KEY_LAST_POS, pos).apply()
                        lastSavedPosition = pos
                    }
                } catch (e: Exception) {
                    // Sessizce gec
                }
                anaHandler.postDelayed(this, 500)
            }
        }
    }

    // Audio Focus Dinleyicisi
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus alindi (GAIN)")
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (odakGeriGelinceCal) {
                    odakGeriGelinceCal = false
                    oynat()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus kalici kaybedildi (LOSS)")
                odakGeriGelinceCal = false
                duraklat()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus gecici kaybedildi (TRANSIENT)")
                if (_caliyorMu.value) {
                    duraklat(releaseFocus = false)
                    odakGeriGelinceCal = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus gecici kisilma (DUCK)")
                // GPS sesli yonlendirmesi konusurken muzik sesi %20'ye iner
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    init {
        _karisikCal.value = prefs.getBoolean(KEY_SHUFFLE, false)
        _tekrarModu.value = prefs.getInt(KEY_REPEAT, 0)
        bekleyenSeekKonumu = 0L
    }

    fun setListener(l: PlaybackListener?) {
        this.listener = l
    }

    private fun baslatMediaPlayer() {
        durdurVeSifirlaPlayer()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnPreparedListener(this@InternalMusicPlayer)
            setOnCompletionListener(this@InternalMusicPlayer)
            setOnErrorListener(this@InternalMusicPlayer)
        }
    }

    private fun durdurVeSifirlaPlayer() {
        mediaPlayer?.let { player ->
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        hazirMi = false
    }

    fun parcaAtaVeOynat(parca: SesParcasi, liste: List<SesParcasi> = emptyList()) {
        if (liste.isNotEmpty()) {
            _calismaListesi.clear()
            _calismaListesi.addAll(liste)
            kuyruguYenidenKur(parca)
        } else if (_calmaKuyrugu.none { it.libraryKey() == parca.libraryKey() }) {
            _calismaListesi.clear()
            _calismaListesi.add(parca)
            _calmaKuyrugu.clear()
            _calmaKuyrugu.add(parca)
            suAnkiIndex = 0
            _kuyruk.value = _calmaKuyrugu.toList()
        } else {
            val idx = _calmaKuyrugu.indexOfFirst { it.libraryKey() == parca.libraryKey() }
            suAnkiIndex = if (idx >= 0) idx else 0
        }

        calParca(parca, otomatikOynat = true)
    }

    fun restoreSavedPlayback(library: List<SesParcasi>, autoPlay: Boolean): Boolean {
        if (_anlikParca.value != null) return true
        val byKey = library.associateBy { it.libraryKey() }
        fun savedKeys(key: String): List<String> = runCatching {
            val array = org.json.JSONArray(prefs.getString(key, "[]"))
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
        val queueKeys = savedKeys("last_queue")
        val baseKeys = savedKeys("last_base_queue").ifEmpty { queueKeys }
        _calismaListesi.clear()
        _calismaListesi.addAll(baseKeys.mapNotNull { byKey[it] })
        _calmaKuyrugu.clear()
        _calmaKuyrugu.addAll(queueKeys.mapNotNull { byKey[it] }.ifEmpty { _calismaListesi })
        _kuyruk.value = _calmaKuyrugu.toList()
        val saved = prefs.getString(KEY_LAST_PATH, null) ?: return true
        val track = byKey[saved] ?: return false
        val position = prefs.getLong(KEY_LAST_POS, 0L).coerceAtLeast(0L)
        if (_calmaKuyrugu.none { it.libraryKey() == saved }) _calmaKuyrugu.add(track)
        if (_calismaListesi.none { it.libraryKey() == saved }) _calismaListesi.add(track)
        suAnkiIndex = _calmaKuyrugu.indexOfFirst { it.libraryKey() == saved }
        _kuyruk.value = _calmaKuyrugu.toList()
        calParca(track, autoPlay && prefs.getBoolean("was_playing", false), position)
        return true
    }

    fun indexeGoreOynat(index: Int) {
        if (index in 0 until _calmaKuyrugu.size) {
            suAnkiIndex = index
            calParca(_calmaKuyrugu[index], otomatikOynat = true)
        }
    }

    fun kuyrugaEkle(parca: SesParcasi, siradaki: Boolean = false) = kuyrugaEkle(listOf(parca), siradaki)

    fun kuyrugaEkle(parcalar: List<SesParcasi>, siradaki: Boolean = false) {
        val known = _calismaListesi.mapTo(mutableSetOf()) { it.libraryKey() }
        val additions = parcalar.filter { known.add(it.libraryKey()) }
        if (additions.isEmpty()) return
        val currentKey = _anlikParca.value?.libraryKey()
        val baseIndex = if (siradaki) _calismaListesi.indexOfFirst { it.libraryKey() == currentKey } + 1
            else _calismaListesi.size
        val queueIndex = if (siradaki && suAnkiIndex >= 0) suAnkiIndex + 1 else _calmaKuyrugu.size
        _calismaListesi.addAll(baseIndex.coerceIn(0, _calismaListesi.size), additions)
        _calmaKuyrugu.addAll(queueIndex.coerceIn(0, _calmaKuyrugu.size), additions)
        if (suAnkiIndex >= queueIndex) suAnkiIndex += additions.size
        kuyruguYayinla()
    }

    fun kuyruktanKaldir(parca: SesParcasi) {
        val key = parca.libraryKey()
        val index = _calmaKuyrugu.indexOfFirst { it.libraryKey() == key }
        if (index < 0) return
        val isCurrent = _anlikParca.value?.libraryKey() == key
        _calismaListesi.removeAll { it.libraryKey() == key }
        _calmaKuyrugu.removeAt(index)
        if (isCurrent) {
            duraklat()
            if (_calmaKuyrugu.isEmpty()) {
                durdurVeSifirlaPlayer()
                _anlikParca.value = null
                suAnkiIndex = -1
            } else {
                suAnkiIndex = -1
                indexeGoreOynat(index.coerceAtMost(_calmaKuyrugu.lastIndex))
            }
        } else if (suAnkiIndex > index) suAnkiIndex--
        kuyruguYayinla()
    }

    fun kuyruktaTasi(parca: SesParcasi, delta: Int) {
        val key = parca.libraryKey()
        val from = _calmaKuyrugu.indexOfFirst { it.libraryKey() == key }
        val to = from + delta
        if (from < 0 || to !in _calmaKuyrugu.indices) return
        val currentKey = _anlikParca.value?.libraryKey()
        _calmaKuyrugu.add(to, _calmaKuyrugu.removeAt(from))
        val baseFrom = _calismaListesi.indexOfFirst { it.libraryKey() == key }
        val baseTo = baseFrom + delta
        if (baseFrom >= 0 && baseTo in _calismaListesi.indices)
            _calismaListesi.add(baseTo, _calismaListesi.removeAt(baseFrom))
        suAnkiIndex = _calmaKuyrugu.indexOfFirst { it.libraryKey() == currentKey }
        kuyruguYayinla()
    }

    private fun kuyruguYayinla() {
        _kuyruk.value = _calmaKuyrugu.toList()
        listener?.onKuyrukGuncellendi(_kuyruk.value)
        kaydetDurum()
    }

    private fun calParca(parca: SesParcasi, otomatikOynat: Boolean, startPosition: Long = 0L) {
        _playbackError.value = null
        anaHandler.removeCallbacks(ilerlemeGorevi)
        baslatMediaPlayer()
        odakGeriGelinceCal = false
        pendingPlay = otomatikOynat
        recordNextStart = true
        bekleyenSeekKonumu = startPosition.coerceIn(0L, (parca.sureMs - 1L).coerceAtLeast(0L))
        _caliyorMu.value = false
        _anlikParca.value = parca
        _toplamSureMs.value = parca.sureMs
        _anlikKonumMs.value = bekleyenSeekKonumu
        listener?.onParcaDegisti(parca)

        try {
            val uri = if (parca.contentUri.isNotBlank()) Uri.parse(parca.contentUri)
                else Uri.fromFile(File(parca.dosyaYolu))
            mediaPlayer?.setDataSource(context, uri)
            hazirMi = false
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Parca yukleme hatasi: ${e.message}")
            durdurVeSifirlaPlayer()
            _playbackError.value = context.getString(app.vela.R.string.car_music_play_failed)
            _caliyorMu.value = false
            listener?.onCalmaDurumuDegisti(false)
        }

        kaydetDurum()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        if (mp !== mediaPlayer) return
        hazirMi = true
        _toplamSureMs.value = mp?.duration?.toLong() ?: _anlikParca.value?.sureMs ?: 0L

        if (bekleyenSeekKonumu > 0L && bekleyenSeekKonumu < _toplamSureMs.value) {
            mp?.seekTo(bekleyenSeekKonumu.toInt())
            _anlikKonumMs.value = bekleyenSeekKonumu
            bekleyenSeekKonumu = 0L
        }

        if (pendingPlay) oynat()
    }

    fun oynat() {
        pendingPlay = true
        if (!hazirMi && mediaPlayer != null) return
        if (!odakTalepEt()) {
            Log.w(TAG, "Audio focus alinamadi, oynatma baslatilamiyor")
            return
        }

        try {
            if (hazirMi && mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                _caliyorMu.value = true
                if (recordNextStart) {
                    _anlikParca.value?.let { MusicPlaylistStore.getInstance(context).recordPlay(it) }
                    recordNextStart = false
                }
                kaydetDurum()
                listener?.onCalmaDurumuDegisti(true)
                anaHandler.removeCallbacks(ilerlemeGorevi)
                anaHandler.post(ilerlemeGorevi)
            } else if (!hazirMi && _anlikParca.value != null) {
                calParca(_anlikParca.value!!, otomatikOynat = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Oynatma baslatma hatasi: ${e.message}")
        }
    }

    fun duraklat(releaseFocus: Boolean = true) {
        odakGeriGelinceCal = false
        pendingPlay = false
        if (releaseFocus) odagiBirak()
        try {
            if (hazirMi && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
            _caliyorMu.value = false
            listener?.onCalmaDurumuDegisti(false)
            anaHandler.removeCallbacks(ilerlemeGorevi)
            kaydetDurum()
        } catch (e: Exception) {
            Log.e(TAG, "Duraklatma hatasi: ${e.message}")
        }
    }

    fun oynatDuraklat() {
        if (_caliyorMu.value) {
            duraklat()
        } else {
            oynat()
        }
    }

    fun sonraki() {
        if (_calmaKuyrugu.isEmpty()) return

        val yeniIndex = suAnkiIndex + 1
        if (yeniIndex < _calmaKuyrugu.size) {
            suAnkiIndex = yeniIndex
            calParca(_calmaKuyrugu[suAnkiIndex], otomatikOynat = true)
        } else if (_tekrarModu.value == 2) { // Tum liste tekrari
            suAnkiIndex = 0
            calParca(_calmaKuyrugu[0], otomatikOynat = true)
        } else {
            // Liste bitti
            duraklat()
            konumaGit(0L)
        }
    }

    fun onceki() {
        if (_calmaKuyrugu.isEmpty()) return

        // Sarki 3 saniyeden fazla calmissa basa sar, yoksa onceki parcaya gec
        if (_anlikKonumMs.value > 3000L) {
            konumaGit(0L)
            return
        }

        val yeniIndex = suAnkiIndex - 1
        if (yeniIndex >= 0) {
            suAnkiIndex = yeniIndex
            calParca(_calmaKuyrugu[suAnkiIndex], otomatikOynat = true)
        } else if (_tekrarModu.value == 2) {
            suAnkiIndex = _calmaKuyrugu.size - 1
            calParca(_calmaKuyrugu[suAnkiIndex], otomatikOynat = true)
        } else {
            konumaGit(0L)
        }
    }

    fun konumaGit(requestedMs: Long) {
        val konumMs = requestedMs.coerceIn(0L, _toplamSureMs.value.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()))
        try {
            if (hazirMi) {
                mediaPlayer?.seekTo(konumMs.toInt())
                _anlikKonumMs.value = konumMs
            } else {
                bekleyenSeekKonumu = konumMs
            }
        } catch (e: Exception) {
            Log.e(TAG, "Konuma git hatasi: ${e.message}")
        }
    }

    fun setKarisikCal(aktif: Boolean) {
        if (_karisikCal.value != aktif) {
            _karisikCal.value = aktif
            prefs.edit().putBoolean(KEY_SHUFFLE, aktif).apply()
            kuyruguYenidenKur(_anlikParca.value)
        }
    }

    fun setTekrarModu(mod: Int) {
        val yeniMod = mod % 3
        _tekrarModu.value = yeniMod
        prefs.edit().putInt(KEY_REPEAT, yeniMod).apply()
    }

    private fun kuyruguYenidenKur(suAnki: SesParcasi?) {
        _calmaKuyrugu.clear()
        if (_calismaListesi.isEmpty()) { _kuyruk.value = emptyList(); suAnkiIndex = -1; return }

        if (_karisikCal.value) {
            val karisikListe = ArrayList(_calismaListesi)
            Collections.shuffle(karisikListe)
            if (suAnki != null) {
                karisikListe.remove(suAnki)
                karisikListe.add(0, suAnki)
            }
            _calmaKuyrugu.addAll(karisikListe)
        } else {
            _calmaKuyrugu.addAll(_calismaListesi)
        }

        suAnkiIndex = if (suAnki != null) _calmaKuyrugu.indexOfFirst { it.libraryKey() == suAnki.libraryKey() } else 0
        if (suAnkiIndex < 0) suAnkiIndex = 0

        _kuyruk.value = _calmaKuyrugu.toList()
        listener?.onKuyrukGuncellendi(_kuyruk.value)
        kaydetDurum()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        if (mp !== mediaPlayer) return
        if (_tekrarModu.value == 1) { konumaGit(0L); oynat() } else sonraki()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        if (mp !== mediaPlayer) return true
        Log.e(TAG, "MediaPlayer hata: what=$what extra=$extra")
        durdurVeSifirlaPlayer()
        _playbackError.value = context.getString(app.vela.R.string.car_music_play_failed)
        _caliyorMu.value = false
        listener?.onCalmaDurumuDegisti(false)
        hazirMi = false
        return true
    }

    private fun odakTalepEt(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(audioFocusListener, anaHandler)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun odagiBirak() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun kaydetDurum() {
        val editor = prefs.edit()
        _anlikParca.value?.let { editor.putString(KEY_LAST_PATH, it.libraryKey()) }
            ?: editor.remove(KEY_LAST_PATH)
        editor.putString("last_queue", org.json.JSONArray(_calmaKuyrugu.map { it.libraryKey() }).toString())
        editor.putString("last_base_queue", org.json.JSONArray(_calismaListesi.map { it.libraryKey() }).toString())
        editor.putLong(KEY_LAST_POS, _anlikKonumMs.value)
        editor.putBoolean("was_playing", _caliyorMu.value || pendingPlay)
        editor.apply()
    }

    fun serbestBirak() {
        anaHandler.removeCallbacks(ilerlemeGorevi)
        duraklat()
        odagiBirak()
        durdurVeSifirlaPlayer()
    }
}
