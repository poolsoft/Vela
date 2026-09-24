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

    override fun onListenerConnected() {
        Log.d(TAG, "NotificationListener baglandi. Medya oturumlari dinlenmeye hazir.")
        yenileAktifOturumlar()
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "NotificationListener baglantisi kesildi.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (isMediaNotification(sbn)) {
            Log.v(TAG, "Medya bildirimi geldi: ${sbn?.packageName}")
            anaHandler.postDelayed({
                yenileAktifOturumlar()
            }, 500)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isMediaNotification(sbn)) {
            Log.v(TAG, "Medya bildirimi kaldirildi: ${sbn?.packageName}")
            anaHandler.postDelayed({
                yenileAktifOturumlar()
            }, 300)
        }
    }

    private fun isMediaNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null || sbn.notification == null) return false
        val category = sbn.notification.category
        return Notification.CATEGORY_TRANSPORT == category || Notification.CATEGORY_SERVICE == category
    }

    private fun yenileAktifOturumlar() {
        try {
            val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val componentName = ComponentName(this, MediaNotificationListener::class.java)
            val controllers: List<MediaController> = manager.getActiveSessions(componentName)

            Log.d(TAG, "Aktif oturumlar yenilendi. Oturum sayisi: ${controllers.size}")
            MusicManager.getInstance(applicationContext).onOturumlarYenilendi(controllers)
        } catch (se: SecurityException) {
            Log.w(TAG, "Bildirim erisim izni henuz verilmedi: ${se.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Aktif oturumlari yenileme hatasi: ${e.message}")
        }
    }
}
