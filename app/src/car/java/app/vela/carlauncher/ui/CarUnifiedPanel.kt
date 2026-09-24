package app.vela.carlauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.vela.R
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.model.HizTelemetrisi
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.widgets.InterlockingClockView
import app.vela.carlauncher.widgets.MusicVisualizerView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CoMaps_Auto_V2 ve OsmAnd fragment_unified_panel.xml Layoutunu Dogrudan Sisiren (Inflate Eden) Panel Bileseni.
 * - Konturlu Saat (InterlockingClockView)
 * - Album Kapagi Arka Plani ve Glass Layer
 * - Muzik Bilgisi ve Canli Visualizer
 * - Panel Menu Popup: Harita, Masaustu, Gosterge, Hava Durumu, Ayarlar, Ekran Kapat, RAM Temizle
 * - Bildirim Karti
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarUnifiedPanel(
    telemetri: HizTelemetrisi,
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onMuzikPaneliAc: () -> Unit,
    onMasaustuSecildi: () -> Unit,
    onDashboardSecildi: () -> Unit,
    onHavaDurumuSecildi: () -> Unit,
    onAyarlarSecildi: () -> Unit,
    onPaneliKapat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var saatMetni by remember { mutableStateOf("12:00") }

    LaunchedEffect(Unit) {
        val saatFormati = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            saatMetni = saatFormati.format(Date())
            delay(1000L)
        }
    }

    AndroidView(
        factory = { ctx ->
            val themedContext = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            val view = LayoutInflater.from(themedContext).inflate(R.layout.fragment_unified_panel, null)

            val panelMenuBtn = view.findViewById<ImageButton>(R.id.panel_menu_btn)
            val musicArea = view.findViewById<View>(R.id.music_area)
            val btnPrev = view.findViewById<ImageButton>(R.id.music_btn_prev)
            val btnPlay = view.findViewById<ImageButton>(R.id.music_btn_play)
            val btnNext = view.findViewById<ImageButton>(R.id.music_btn_next)
            val btnNotifClose = view.findViewById<ImageButton>(R.id.btn_notif_close)
            val cardNotification = view.findViewById<View>(R.id.card_notification)

            btnNotifClose?.setOnClickListener {
                cardNotification?.visibility = View.GONE
            }

            panelMenuBtn?.setOnClickListener { anchorView ->
                val popup = PopupMenu(themedContext, anchorView)
                popup.menu.add(0, 1, 0, "Harita Modu")
                popup.menu.add(0, 2, 1, "Masaüstü Modu")
                popup.menu.add(0, 3, 2, "Gösterge / Telemetri")
                popup.menu.add(0, 4, 3, "Hava Durumu")
                popup.menu.add(0, 5, 4, "Ayarlar")
                popup.menu.add(0, 6, 5, "Ekranı Kapat")
                popup.menu.add(0, 7, 6, "Hafızayı Temizle (RAM)")

                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { onPaneliKapat(); true }
                        2 -> { onMasaustuSecildi(); true }
                        3 -> { onDashboardSecildi(); true }
                        4 -> { onHavaDurumuSecildi(); true }
                        5 -> { onAyarlarSecildi(); true }
                        6 -> { CarHardwareManager.getInstance(ctx).turnOffScreen(ctx); true }
                        7 -> { CarHardwareManager.getInstance(ctx).cleanRam(ctx); true }
                        else -> false
                    }
                }
                popup.show()
            }

            musicArea?.setOnClickListener { onMuzikPaneliAc() }
            btnPrev?.setOnClickListener { onOnceki() }
            btnPlay?.setOnClickListener { onOynatDuraklat() }
            btnNext?.setOnClickListener { onSonraki() }

            view
        },
        update = { view ->
            val albumArt = view.findViewById<ImageView>(R.id.unified_album_art)
            val panelClock = view.findViewById<InterlockingClockView>(R.id.music_panel_clock)
            val trackTitle = view.findViewById<TextView>(R.id.music_track_title)
            val trackArtist = view.findViewById<TextView>(R.id.music_track_artist)
            val btnPlay = view.findViewById<ImageButton>(R.id.music_btn_play)
            val visualizer = view.findViewById<MusicVisualizerView>(R.id.music_visualizer)

            if (medya.albumKapagi != null) {
                albumArt?.setImageBitmap(medya.albumKapagi)
            } else {
                albumArt?.setImageResource(R.drawable.bg_track_art_placeholder)
            }

            panelClock?.text = saatMetni

            trackTitle?.text = if (medya.baslik.isNotBlank() && medya.baslik != "Müzik Seçilmedi") {
                medya.baslik
            } else {
                "Parça Adı"
            }

            trackArtist?.text = if (medya.sanatci.isNotBlank() && medya.sanatci != "Dokunun veya Çalın") {
                medya.sanatci
            } else {
                "Sanatçı"
            }

            btnPlay?.setImageResource(
                if (medya.caliyorMu) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            visualizer?.setPlaying(medya.caliyorMu)
        },
        modifier = modifier.fillMaxSize()
    )
}
