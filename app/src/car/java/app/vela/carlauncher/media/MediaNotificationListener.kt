package app.vela.carlauncher.media

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Medya Bildirim Dinleme Servisi (MediaNotificationListener).
 * Harici muzik uygulamalarinin (Spotify, YouTube Music, Deezer, vb.)
 * bildirimlerini dinleyerek aktif MediaSession oturumlarina erisim saglar.
 *
 * Android'in MediaSessionManager.getActiveSessions() API'si bu servisin ComponentName'i
 * uzerinden calisir. Servis aktif olarak bildirim dinlemezse session listesi bos doner.
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

    private val anaHandler = Handler(Looper.getMainLooper())
    private var sonYenilemeZamaniMs = 0L
    private val YENILEME_ESIK_MS = 2000L // En az 2 saniyede bir oturum yenile

    override fun onListenerConnected() {
        app.vela.util.FileLogger.i(TAG, "NotificationListener baglandi. Medya oturumlari dinlenmeye hazir.")
        yenileAktifOturumlar()
    }

    override fun onListenerDisconnected() {
        app.vela.util.FileLogger.w(TAG, "NotificationListener baglantisi kesildi.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // OsmAnd mimarisi: Bildirimler cok sik gelebilir (RDS, navigasyon, teyp sistem bildirimleri).
        // Bu yuzden sadece gercek medya bildirimlerinde ve debounced olarak islenmelidir.
        if (isMediaNotification(sbn)) {
            val simdi = System.currentTimeMillis()
            if (simdi - sonYenilemeZamaniMs > YENILEME_ESIK_MS) {
                sonYenilemeZamaniMs = simdi
                anaHandler.removeCallbacksAndMessages(null)
                anaHandler.postDelayed({
                    yenileAktifOturumlar()
                }, 1000)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Bildirim kaldirildiginda teybi mesgul etmemek icin gereksiz tarama yapma
    }

    private fun isMediaNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null || sbn.notification == null) return false
        val category = sbn.notification.category
        return Notification.CATEGORY_TRANSPORT == category
    }

    fun yenileAktifOturumlar() {
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val componentName = ComponentName(this, MediaNotificationListener::class.java)
            val controllers: List<MediaController> = manager.getActiveSessions(componentName)

            app.vela.util.FileLogger.d(TAG, "Aktif oturumlar yenilendi. Oturum sayisi: ${controllers.size}")
            MusicManager.getInstance(applicationContext).onOturumlarYenilendi(controllers)
        } catch (se: SecurityException) {
            app.vela.util.FileLogger.w(TAG, "Bildirim erisim izni henuz verilmedi: ${se.message}")
        } catch (e: Exception) {
            app.vela.util.FileLogger.e(TAG, "Aktif oturumlari yenileme hatasi: ${e.message}", e)
        }
    }
}
