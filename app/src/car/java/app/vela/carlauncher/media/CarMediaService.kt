package app.vela.carlauncher.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.service.media.MediaBrowserService
import android.view.KeyEvent
import app.vela.MainActivity
import app.vela.R
import app.vela.carlauncher.model.MedyaParcasi

/**
 * Otomotiv Medya Arka Plan Servisi (CarMediaService).
 * MediaBrowserService ve Foreground Service olarak calisir.
 * - Direksiyon medya tuslarini (Steering Wheel Controls) yakalar.
 * - Kalici sistem bildiriminde medya kontrollerini ve kapak resmini gosterir.
 * - Android Auto ile uyumlu calisir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarMediaService : MediaBrowserService(), MusicManager.MusicUIListener {

    companion object {
        // Never persisted: only the explicit diagnostic service test may opt in.
        internal var diagnosticTestAllowed = false
        internal val diagnosticReady = kotlinx.coroutines.flow.MutableStateFlow(false)
        private val disabled get() = app.vela.BuildConfig.DIAGNOSTIC_MUSIC_DISABLED && !diagnosticTestAllowed

        const val CHANNEL_ID = "vela_car_music_channel"
        const val NOTIFICATION_ID = 888

        const val ACTION_PLAY_PAUSE = "app.vela.carlauncher.media.action.PLAY_PAUSE"
        const val ACTION_NEXT = "app.vela.carlauncher.media.action.NEXT"
        const val ACTION_PREVIOUS = "app.vela.carlauncher.media.action.PREVIOUS"
        const val ACTION_CLOSE = "app.vela.carlauncher.media.action.CLOSE"

        fun baslat(context: Context) {
            if (disabled) return
            val intent = Intent(context, CarMediaService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaSession: MediaSession? = null
    private lateinit var musicManager: MusicManager
    private var bildirimKapatildi = false

    override fun onCreate() {
        super.onCreate()
        if (disabled) { stopSelf(); return }
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: create begin")
        bildirimKanaliOlustur()
        // Meet the foreground deadline before initializing adapters or talking to OEM services.
        val readyNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        startForeground(NOTIFICATION_ID, readyNotification
            .setSmallIcon(R.drawable.ic_internal_music)
            .setContentTitle(getString(R.string.car_media_notification_title))
            .build())
        app.vela.util.FileLogger.i("CarMediaService", "onCreate: foreground ready")
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: foreground ready")
        musicManager = MusicManager.getInstance(applicationContext)
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: music manager ready")

        mediaSession = MediaSession(this, "VelaCarMedia").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    musicManager.oynat()
                }

                override fun onPause() {
                    musicManager.duraklat()
                }

                override fun onSkipToNext() {
                    musicManager.sonraki()
                }

                override fun onSkipToPrevious() {
                    musicManager.onceki()
                }

                override fun onSeekTo(pos: Long) {
                    musicManager.konumaGit(pos)
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> musicManager.oynat()
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> musicManager.duraklat()
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> musicManager.oynatDuraklat()
                            KeyEvent.KEYCODE_MEDIA_NEXT -> musicManager.sonraki()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> musicManager.onceki()
                        }
                        return true
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            setPlaybackState(buildPlaybackState(PlaybackState.STATE_PAUSED))
            isActive = true
        }

        sessionToken = mediaSession?.sessionToken
        musicManager.addListener(this)
        guncelleBildirim(hemen = true)
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: created")
        diagnosticReady.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (disabled) { stopSelf(); return START_NOT_STICKY }
        app.vela.util.FileLogger.i("CarMediaService", "onStartCommand: ${intent?.action}, startId=$startId")
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: start command")
        val action = intent?.action
        if (action != ACTION_CLOSE) bildirimKapatildi = false
        when (action) {
            ACTION_PLAY_PAUSE -> musicManager.oynatDuraklat()
            ACTION_NEXT -> musicManager.sonraki()
            ACTION_PREVIOUS -> musicManager.onceki()
            ACTION_CLOSE -> {
                bildirimKapatildi = true
                musicManager.duraklat()
                bildirimHandler.removeCallbacks(bildirimGuncellemeRunnable)
                bildirimGuncellemeBekliyor = false
                mediaSession?.isActive = false
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        guncelleBildirim(hemen = true)
        return if (diagnosticTestAllowed) START_NOT_STICKY else START_STICKY
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<android.media.browse.MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private fun buildPlaybackState(state: Int): PlaybackState {
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    (if (musicManager.canSeek()) PlaybackState.ACTION_SEEK_TO else 0L)
            )
            .setState(state, musicManager.currentPlayback().anlikKonumMs, if (state == PlaybackState.STATE_PLAYING) 1.0f else 0f)
            .build()
    }

    private fun bildirimKanaliOlustur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Araç Medya Oynatıcı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Otomotiv arka plan müzik servisi"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private val bildirimHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bildirimGuncellemeBekliyor = false
    private val bildirimGuncellemeRunnable = Runnable {
        bildirimGuncellemeBekliyor = false
        gerceklesitirBildirimGuncelleme()
    }

    private fun guncelleBildirim(hemen: Boolean = false) {
        if (bildirimKapatildi) return
        if (hemen) {
            bildirimHandler.removeCallbacks(bildirimGuncellemeRunnable)
            bildirimGuncellemeBekliyor = false
            gerceklesitirBildirimGuncelleme()
        } else {
            if (!bildirimGuncellemeBekliyor) {
                bildirimGuncellemeBekliyor = true
                bildirimHandler.postDelayed(bildirimGuncellemeRunnable, 150L)
            }
        }
    }

    private fun guvenliOlcekleBitmap(kaynak: Bitmap?, maxBoyut: Int = 512): Bitmap? {
        if (kaynak == null) return null
        return try {
            val genislik = kaynak.width
            val yukseklik = kaynak.height
            if (genislik <= maxBoyut && yukseklik <= maxBoyut) {
                kaynak
            } else {
                val oran = genislik.toFloat() / yukseklik.toFloat()
                val yeniGenislik: Int
                val yeniYukseklik: Int
                if (oran > 1f) {
                    yeniGenislik = maxBoyut
                    yeniYukseklik = (maxBoyut / oran).toInt().coerceAtLeast(1)
                } else {
                    yeniYukseklik = maxBoyut
                    yeniGenislik = (maxBoyut * oran).toInt().coerceAtLeast(1)
                }
                Bitmap.createScaledBitmap(kaynak, yeniGenislik, yeniYukseklik, true)
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun gerceklesitirBildirimGuncelleme() {
        if (bildirimKapatildi) return

        val medya = musicManager.medyaDurumu.value
        val caliyor = medya.caliyorMu

        // MediaSession guncelle
        val state = if (caliyor) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        mediaSession?.setPlaybackState(buildPlaybackState(state))

        val guvenliKapak = guvenliOlcekleBitmap(medya.albumKapagi, 512)

        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, medya.baslik)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, medya.sanatci)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, medya.toplamSureMs)

        if (guvenliKapak != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, guvenliKapak)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        // Bildirim PendingIntent'leri
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CarMediaService::class.java).apply { action = ACTION_PREVIOUS },
            PendingIntent.FLAG_IMMUTABLE
        )

        val playIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CarMediaService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, CarMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, CarMediaService::class.java).apply { action = ACTION_CLOSE },
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setContentTitle(if (medya.baslik.isNotBlank()) medya.baslik else "Vela Müzik")
            .setContentText(if (medya.sanatci.isNotBlank()) medya.sanatci else "Hazır")
            .setSmallIcon(R.drawable.ic_internal_music)
            .setLargeIcon(guvenliKapak)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(closeIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(caliyor)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Önceki",
                    prevIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (caliyor) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (caliyor) "Duraklat" else "Oynat",
                    playIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Sonraki",
                    nextIntent
                ).build()
            )
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        try {
            startForeground(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            app.vela.util.FileLogger.e("CarMediaService", "startForeground bildirimi gonderilemedi: ${e.message}", e)
        }
    }

    override fun onParcaDegisti(medya: MedyaParcasi) {
        guncelleBildirim(hemen = false)
    }

    override fun onCalmaDurumuDegisti(caliyor: Boolean) {
        guncelleBildirim(hemen = false)
    }

    override fun onDestroy() {
        diagnosticReady.value = false
        app.vela.util.FileLogger.i("CarMediaService", "onDestroy")
        app.vela.diag.ProcessDiagnostics.checkpoint("media service: destroy")
        bildirimHandler.removeCallbacks(bildirimGuncellemeRunnable)
        if (::musicManager.isInitialized) musicManager.removeListener(this)
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
