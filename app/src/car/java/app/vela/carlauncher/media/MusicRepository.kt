package app.vela.carlauncher.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import app.vela.carlauncher.model.SesKlasoru
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Otomotiv Muzik Deposu (MusicRepository).
 * Dahili hafiza ve takilan USB belleklerdeki ses dosyalarini (MP3, FLAC, WAV, M4A)
 * MediaStore ve dosya sistemi uzerinden tarar, klasorler ve parcalari listeler.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class MusicRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"

        @Volatile
        private var instance: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return instance ?: synchronized(this) {
                instance ?: MusicRepository(context.applicationContext).also { instance = it }
            }
        }

        fun muzikleriTara(context: Context) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                getInstance(context).muzikKutuphanesiniTara()
            }
        }
    }

    private val scanScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private var mediaScanJob: kotlinx.coroutines.Job? = null
    private val storageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
            mediaScanJob?.cancel()
            mediaScanJob = scanScope.launch {
                kotlinx.coroutines.delay(600)
                muzikKutuphanesiniTara()
            }
        }
    }

    init {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MEDIA_MOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_REMOVED)
            addAction(android.content.Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(storageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(storageReceiver, filter)
        }
    }

    private val scanMutex = kotlinx.coroutines.sync.Mutex()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _parcalar = MutableStateFlow<List<SesParcasi>>(emptyList())
    val parcalar: StateFlow<List<SesParcasi>> = _parcalar.asStateFlow()

    private val _klasorler = MutableStateFlow<List<SesKlasoru>>(emptyList())
    val klasorler: StateFlow<List<SesKlasoru>> = _klasorler.asStateFlow()

    private val _taraniyorMu = MutableStateFlow(false)
    val taraniyorMu: StateFlow<Boolean> = _taraniyorMu.asStateFlow()

    suspend fun muzikKutuphanesiniTara() = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) return@withContext
        _lastError.value = null
        _taraniyorMu.value = true

        val bulunanParcalar = mutableListOf<SesParcasi>()

        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID
            )
            val columns = projection.toMutableList()
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                columns.add(MediaStore.Audio.Media.RELATIVE_PATH)
                columns.add(MediaStore.Audio.Media.VOLUME_NAME)
            }
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(uri, columns.toTypedArray(), selection, null, sortOrder).let { result ->
                checkNotNull(result) { "MediaStore query returned no cursor" }
            }.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Bilinmeyen Parça"
                    val artist = cursor.getString(artistCol) ?: "Bilinmeyen Sanatçı"
                    val album = cursor.getString(albumCol) ?: "Bilinmeyen Albüm"
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val folderPath = File(path).parent ?: if (android.os.Build.VERSION.SDK_INT >= 29) {
                        val relative = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)).orEmpty()
                        val volume = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.VOLUME_NAME)).orEmpty()
                        "$volume/$relative".trimEnd('/')
                    } else ""
                    val dateAdded = cursor.getLong(dateCol)

                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        cursor.getLong(albumIdCol)
                    ).toString()

                    if (duration > 5000L) { // 5 saniyeden kisa ses efektlerini haric tut
                        bulunanParcalar.add(
                            SesParcasi(
                                id = id,
                                baslik = title,
                                sanatci = artist,
                                album = album,
                                sureMs = duration,
                                dosyaYolu = path,
                                albumArtUri = artworkUri,
                                eklenmeTarihi = dateAdded,
                                contentUri = ContentUris.withAppendedId(uri, id).toString(),
                                folderPath = folderPath
                            )
                        )
                    }
                }
            }

            // Klasorleri grupla
            val klasorMap = mutableMapOf<String, MutableList<SesParcasi>>()
            for (p in bulunanParcalar) {
                if (p.dosyaYolu.isNotBlank()) {
                    val parent = File(p.dosyaYolu).parent ?: "Dahili Depolama"
                    klasorMap.getOrPut(parent) { mutableListOf() }.add(p)
                }
            }

            val klasorListesi = klasorMap.map { (yol, list) ->
                val folderFile = File(yol)
                val isUsb = yol.contains("usb", ignoreCase = true) || yol.contains("storage/usbotg", ignoreCase = true)
                SesKlasoru(
                    yol = yol,
                    ad = folderFile.name.ifBlank { "Müzik Klasörü" },
                    parcaSayisi = list.size,
                    isUsb = isUsb
                )
            }.sortedBy { it.ad }

            _parcalar.value = bulunanParcalar
            _klasorler.value = klasorListesi
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = context.getString(if (e is SecurityException) app.vela.R.string.car_music_permission else app.vela.R.string.car_music_scan_failed)
            if (e is SecurityException) { _parcalar.value = emptyList(); _klasorler.value = emptyList() }
            Log.e(TAG, "Muzik tarama hatasi: ${e.message}")
        } finally {
            _taraniyorMu.value = false
            scanMutex.unlock()
        }
    }
}
