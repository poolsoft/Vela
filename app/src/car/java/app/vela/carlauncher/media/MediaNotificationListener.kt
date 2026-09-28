package app.vela.carlauncher.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.HandlerThread
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.vela.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Medya Bildirim Dinleme Servisi (MediaNotificationListener).
 * Harici muzik uygulamalarinin (Spotify, YouTube Music, Deezer, vb.)
 * bildirimlerini ve oturumlarini dinleyerek aktif MediaSession erisimi saglar.
 *
 * system_server kilitlenmelerini onlemek icin tum islemler arkaplan is parcaciginda
 * calistirilir ve uygulamanin kendi bildirimleri filtrelenir.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class MediaNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaNotifListener"

        fun bildirimIzniVerildiMi(context: Context): Boolean {
            val paket = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(paket)
        }

        fun bildirimAyarlariniAc(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Bildirim ayarlari acilamadi: ${e.message}")
            }
        }
    }

    private val servisScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var arkaPlanThread: HandlerThread? = null
    private var arkaPlanHandler: Handler? = null
    private var sessionManager: MediaSessionManager? = null
    private var sessionsListenerKayitliMi = false
    private var sonYenilemeZamaniMs = 0L
    private val YENILEME_ESIK_MS = 2000L

    private val sessionChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        FileLogger.d(TAG, "MediaSession degisikligi algilandi. Oturum sayisi: ${controllers?.size ?: 0}")
        servisScope.launch {
            MusicManager.getInstance(applicationContext).onOturumlarYenilendi(controllers ?: emptyList())
        }
    }

    private val yenilemeRunnable = Runnable {
        yenileAktifOturumlar()
    }

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("VelaMediaNotifBgThread")
        thread.start()
        arkaPlanThread = thread
        arkaPlanHandler = Handler(thread.looper)
        sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    override fun onDestroy() {
        dinleyiciyiKaldir()
        arkaPlanThread?.quitSafely()
        arkaPlanThread = null
        arkaPlanHandler = null
        servisScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        FileLogger.i(TAG, "NotificationListener baglandi. Medya oturumlari dinlenmeye hazir.")
        // system_server kilitlenmesini onlemek icin senkron islem yapma, arka plana gecikmeli post et
        arkaPlanHandler?.postDelayed({
            dinleyiciyiKaydet()
            yenileAktifOturumlar()
        }, 400L)
    }

    override fun onListenerDisconnected() {
        FileLogger.w(TAG, "NotificationListener baglantisi kesildi.")
        dinleyiciyiKaldir()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Vela'nin kendi bildirimlerini kesinlikle yut (Sonsuz bildirim dongusunu engeller)
        if (sbn == null || sbn.packageName == packageName) return

        if (isMediaNotification(sbn)) {
            val simdi = System.currentTimeMillis()
            if (simdi - sonYenilemeZamaniMs > YENILEME_ESIK_MS) {
                sonYenilemeZamaniMs = simdi
                arkaPlanHandler?.removeCallbacks(yenilemeRunnable)
                arkaPlanHandler?.postDelayed(yenilemeRunnable, 1000L)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        if (isMediaNotification(sbn)) {
            val simdi = System.currentTimeMillis()
            if (simdi - sonYenilemeZamaniMs > YENILEME_ESIK_MS) {
                sonYenilemeZamaniMs = simdi
                arkaPlanHandler?.removeCallbacks(yenilemeRunnable)
                arkaPlanHandler?.postDelayed(yenilemeRunnable, 1500L)
            }
        }
    }

    private fun isMediaNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null || sbn.packageName == packageName) return false
        val notif = sbn.notification ?: return false
        val isTransport = Notification.CATEGORY_TRANSPORT == notif.category
        val hasMediaSession = notif.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
        return isTransport || hasMediaSession
    }

    private fun dinleyiciyiKaydet() {
        try {
            if (!sessionsListenerKayitliMi) {
                val manager = sessionManager ?: return
                val componentName = ComponentName(this, MediaNotificationListener::class.java)
                manager.addOnActiveSessionsChangedListener(sessionChangedListener, componentName, arkaPlanHandler)
                sessionsListenerKayitliMi = true
                FileLogger.d(TAG, "OnActiveSessionsChangedListener basariyla kaydedildi.")
            }
        } catch (se: SecurityException) {
            FileLogger.w(TAG, "ActiveSessions dinleyici kayit izni henuz yok: ${se.message}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "ActiveSessions dinleyici kaydedilemedi: ${e.message}", e)
        }
    }

    private fun dinleyiciyiKaldir() {
        try {
            if (sessionsListenerKayitliMi) {
                sessionManager?.removeOnActiveSessionsChangedListener(sessionChangedListener)
                sessionsListenerKayitliMi = false
                FileLogger.d(TAG, "OnActiveSessionsChangedListener kaldirildi.")
            }
        } catch (e: Exception) {
            FileLogger.w(TAG, "ActiveSessions dinleyici kaldirma hatasi: ${e.message}")
        }
    }

    fun yenileAktifOturumlar() {
        servisScope.launch {
            try {
                val manager = sessionManager ?: (getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager) ?: return@launch
                val componentName = ComponentName(this@MediaNotificationListener, MediaNotificationListener::class.java)
                val controllers: List<MediaController> = manager.getActiveSessions(componentName)

                FileLogger.d(TAG, "Aktif oturumlar yenilendi. Oturum sayisi: ${controllers.size}")
                MusicManager.getInstance(applicationContext).onOturumlarYenilendi(controllers)
            } catch (se: SecurityException) {
                FileLogger.w(TAG, "Bildirim erisim izni henuz verilmedi: ${se.message}")
            } catch (e: Exception) {
                FileLogger.e(TAG, "Aktif oturumlari yenileme hatasi: ${e.message}", e)
            }
        }
    }
}
