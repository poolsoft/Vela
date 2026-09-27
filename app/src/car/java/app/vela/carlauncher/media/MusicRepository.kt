package app.vela.carlauncher.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import app.vela.carlauncher.model.SesKlasoru
import app.vela.carlauncher.model.SesParcasi
import app.vela.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hibrit Otomotiv Muzik Deposu (MusicRepository).
 * 1. Android MediaStore veritabanini sorgular.
 * 2. MediaStore'un goremedigi arac teybi USB belleklerini ve depolama dizinlerini
 *    dogrudan dosya sistemi uzerinden (Direct Storage Crawler) tarar.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class MusicRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"
        private val DESTEKLENEN_UZANTILAR = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "wma")

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
            FileLogger.i(TAG, "Depolama degisikligi algilandi: ${intent?.action}")
            mediaScanJob?.cancel()
            mediaScanJob = scanScope.launch {
                kotlinx.coroutines.delay(1000)
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
        FileLogger.i(TAG, "Muzik kutuphanesi taramasi baslatildi...")

        val bulunanParcalar = mutableListOf<SesParcasi>()
        val tarananDosyaYollari = HashSet<String>()

        try {
            // ==============================================================
            // 1. ADIM: MediaStore Taramasi (Esnek Sure Kriteri)
            // ==============================================================
            runCatching {
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

                // Teyplerde IS_MUSIC genelde 0 oldugundan sadece sure kontrolu yapiyoruz
                val selection = "${MediaStore.Audio.Media.DURATION} >= 10000"
                val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                context.contentResolver.query(uri, columns.toTypedArray(), selection, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: ""
                        if (path.isBlank() || karaListedeMi(path)) continue

                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: File(path).nameWithoutExtension
                        val artist = cursor.getString(artistCol) ?: "Bilinmeyen Sanatçı"
                        val album = cursor.getString(albumCol) ?: "Bilinmeyen Albüm"
                        val duration = cursor.getLong(durationCol)
                        val dateAdded = cursor.getLong(dateCol)
                        val folderPath = File(path).parent ?: ""

                        val artworkUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            cursor.getLong(albumIdCol)
                        ).toString()

                        if (tarananDosyaYollari.add(path)) {
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
                FileLogger.i(TAG, "MediaStore uzerinden ${bulunanParcalar.size} sarki bulundu.")
            }.onFailure { e ->
                FileLogger.w(TAG, "MediaStore tarama uyarisi: ${e.message}")
            }

            // ==============================================================
            // 2. ADIM: Dogrudan Dosya Sistemi & USB Taramasi (Direct Crawler)
            // ==============================================================
            val kokDizinler = taranacakKokDizinleriBul()
            FileLogger.i(TAG, "Dogrudan dosya taramasi yapilacak kok dizinler: ${kokDizinler.map { it.absolutePath }}")

            val retriever = MediaMetadataRetriever()
            try {
                for (kok in kokDizinler) {
                    diziniTara(kok, 0, tarananDosyaYollari, bulunanParcalar, retriever)
                }
            } finally {
                runCatching { retriever.release() }
            }

            FileLogger.i(TAG, "Tarama tamamlandi. Toplam bulunan sarki: ${bulunanParcalar.size}")

            // ==============================================================
            // 3. ADIM: Klasorleri Grupla ve Sirala
            // ==============================================================
            val klasorMap = mutableMapOf<String, MutableList<SesParcasi>>()
            for (p in bulunanParcalar) {
                if (p.dosyaYolu.isNotBlank()) {
                    val parent = File(p.dosyaYolu).parent ?: "Dahili Depolama"
                    klasorMap.getOrPut(parent) { mutableListOf() }.add(p)
                }
            }

            val klasorListesi = klasorMap.map { (yol, list) ->
                val folderFile = File(yol)
                val isUsb = isUsbYolu(yol)
                SesKlasoru(
                    yol = yol,
                    ad = folderFile.name.ifBlank { "Müzik Klasörü" },
                    parcaSayisi = list.size,
                    isUsb = isUsb
                )
            }.sortedWith(compareByDescending<SesKlasoru> { it.isUsb }.thenBy { it.ad })

            FileLogger.i(TAG, "Toplam klasor sayisi: ${klasorListesi.size}")

            _parcalar.value = bulunanParcalar
            _klasorler.value = klasorListesi
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = context.getString(if (e is SecurityException) app.vela.R.string.car_music_permission else app.vela.R.string.car_music_scan_failed)
            if (e is SecurityException) { _parcalar.value = emptyList(); _klasorler.value = emptyList() }
            FileLogger.e(TAG, "Muzik tarama genel hatasi: ${e.message}", e)
        } finally {
            _taraniyorMu.value = false
            scanMutex.unlock()
        }
    }

    private fun taranacakKokDizinleriBul(): Set<File> {
        val kokler = mutableSetOf<File>()

        // 1. Android Context uzerinden harici hafiza ve USB dizinleri
        runCatching {
            val hariciDizinler = context.getExternalFilesDirs(null)
            for (d in hariciDizinler) {
                if (d != null) {
                    var cur: File? = d
                    while (cur != null && cur.parentFile != null &&
                        cur.parentFile?.name != "storage" && cur.parentFile?.name != "mnt"
                    ) {
                        cur = cur.parentFile
                    }
                    if (cur != null && cur.exists() && cur.canRead()) {
                        kokler.add(cur)
                    }
                }
            }
        }

        // 2. Standart Android /storage ve /mnt dizinleri
        val standartDizinler = listOf("/storage", "/mnt/media_rw", "/mnt/usb_storage", "/mnt/sdcard", "/sdcard")
        for (yol in standartDizinler) {
            runCatching {
                val f = File(yol)
                if (f.exists() && f.canRead()) {
                    f.listFiles()?.filter { it.isDirectory && it.canRead() && !it.name.startsWith(".") }?.forEach { sub ->
                        if (sub.name != "self" && sub.name != "knox") {
                            kokler.add(sub)
                        }
                    }
                }
            }
        }

        // 3. Environment root
        runCatching {
            val envDir = android.os.Environment.getExternalStorageDirectory()
            if (envDir != null && envDir.exists() && envDir.canRead()) {
                kokler.add(envDir)
            }
        }

        return kokler
    }

    private fun diziniTara(
        dir: File,
        derinlik: Int,
        bilinenYollar: MutableSet<String>,
        bulunanParcalar: MutableList<SesParcasi>,
        retriever: MediaMetadataRetriever
    ) {
        if (derinlik > 6 || !dir.exists() || !dir.canRead()) return

        val files = dir.listFiles() ?: return
        for (file in files) {
            val adi = file.name
            if (adi.startsWith(".")) continue

            if (file.isDirectory) {
                val adKucuk = adi.lowercase()
                if (!adKucuk.contains("android") &&
                    !adKucuk.contains("whatsapp") &&
                    !adKucuk.contains("telegram") &&
                    !adKucuk.contains(".cache")
                ) {
                    diziniTara(file, derinlik + 1, bilinenYollar, bulunanParcalar, retriever)
                }
            } else if (file.isFile && file.length() > 50 * 1024) { // En az 50 KB
                val ext = file.extension.lowercase()
                if (ext in DESTEKLENEN_UZANTILAR && bilinenYollar.add(file.absolutePath)) {
                    val parca = dosyadanParcaUret(file, retriever, bulunanParcalar.size.toLong() + 100000L)
                    bulunanParcalar.add(parca)
                }
            }
        }
    }

    private fun dosyadanParcaUret(file: File, retriever: MediaMetadataRetriever, sanalId: Long): SesParcasi {
        var title = file.nameWithoutExtension
        var artist = "Bilinmeyen Sanatçı"
        var album = file.parentFile?.name ?: "Bilinmeyen Albüm"
        var duration = 0L

        runCatching {
            retriever.setDataSource(file.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            if (!metaDuration.isNullOrBlank()) duration = metaDuration.toLongOrNull() ?: 0L
        }

        return SesParcasi(
            id = sanalId,
            baslik = title,
            sanatci = artist,
            album = album,
            sureMs = duration,
            dosyaYolu = file.absolutePath,
            albumArtUri = null,
            eklenmeTarihi = file.lastModified() / 1000L,
            contentUri = Uri.fromFile(file).toString(),
            folderPath = file.parent ?: ""
        )
    }

    private fun isUsbYolu(yol: String): Boolean {
        val y = yol.lowercase()
        return y.contains("usb") || y.contains("otg") ||
            (y.startsWith("/storage/") && !y.contains("emulated") && !y.contains("self"))
    }

    private fun karaListedeMi(path: String): Boolean {
        val p = path.lowercase()
        return p.contains("/whatsapp") ||
            p.contains("/telegram") ||
            p.contains("/notifications") ||
            p.contains("/ringtones") ||
            p.contains("/alarms") ||
            p.contains("/recordings")
    }
}
