package app.vela.carlauncher.ui

import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.vela.R
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.widgets.MusicVisualizerView

/**
 * CoMaps_Auto_V2 widget_music_modern.xml Layoutunu Dogrudan Sisiren (Inflate Eden) Bilesen.
 * Kod icerisinde Turkce karakter kullanilmamistir.
 */
@Composable
fun CarSmallMusicWidget(
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onAppIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val themedContext = android.view.ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
            val view = LayoutInflater.from(themedContext).inflate(R.layout.widget_music_modern, null)

            val appIcon = view.findViewById<ImageView>(R.id.widget_app_icon)
            val btnPrev = view.findViewById<ImageButton>(R.id.widget_btn_prev)
            val btnPlay = view.findViewById<ImageButton>(R.id.widget_btn_play)
            val btnNext = view.findViewById<ImageButton>(R.id.widget_btn_next)
            val trackTitle = view.findViewById<TextView>(R.id.widget_track_title)
            val trackArtist = view.findViewById<TextView>(R.id.widget_track_artist)

            appIcon?.setOnClickListener { onAppIconClick() }
            trackTitle?.setOnClickListener { onAppIconClick() }
            trackArtist?.setOnClickListener { onAppIconClick() }

            btnPrev?.setOnClickListener { onOnceki() }
            btnPlay?.setOnClickListener { onOynatDuraklat() }
            btnNext?.setOnClickListener { onSonraki() }

            view
        },
        update = { view ->
            val albumArt = view.findViewById<ImageView>(R.id.widget_album_art)
            val trackTitle = view.findViewById<TextView>(R.id.widget_track_title)
            val trackArtist = view.findViewById<TextView>(R.id.widget_track_artist)
            val btnPlay = view.findViewById<ImageButton>(R.id.widget_btn_play)
            val visualizer = view.findViewById<MusicVisualizerView>(R.id.widget_visualizer)

            if (medya.albumKapagi != null) {
                albumArt?.setImageBitmap(medya.albumKapagi)
            } else {
                albumArt?.setImageResource(R.drawable.bg_track_art_placeholder)
            }

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
        modifier = modifier
    )
}
