package app.vela.carlauncher.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.model.SesParcasi

/**
 * Otomotiv Muzik Parca Listesi Adaptoru (CarMusicTrackAdapter).
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
class CarMusicTrackAdapter(
    private var parcalar: List<SesParcasi>,
    private var calanParcaId: Long = -1L,
    private val onParcaSecildi: (SesParcasi, List<SesParcasi>) -> Unit
) : RecyclerView.Adapter<CarMusicTrackAdapter.TrackViewHolder>() {

    class TrackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.music_title)
        val artist: TextView = itemView.findViewById(R.id.music_artist)
        val playingIndicator: ImageView = itemView.findViewById(R.id.track_playing_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_music_track, parent, false)
        return TrackViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val parca = parcalar[position]
        holder.title.text = parca.baslik
        holder.artist.text = parca.sanatci

        val suAnCalanMi = parca.id == calanParcaId
        holder.playingIndicator.visibility = if (suAnCalanMi) View.VISIBLE else View.GONE
        holder.title.setTextColor(if (suAnCalanMi) 0xFF00FFFF.toInt() else 0xFFFFFFFF.toInt())

        holder.itemView.setOnClickListener {
            onParcaSecildi(parca, parcalar)
        }
    }

    override fun getItemCount(): Int = parcalar.size

    fun guncelleParcalar(yeniListe: List<SesParcasi>, calanId: Long = calanParcaId) {
        this.parcalar = yeniListe
        this.calanParcaId = calanId
        notifyDataSetChanged()
    }

    fun guncelleCalanId(calanId: Long) {
        if (this.calanParcaId != calanId) {
            this.calanParcaId = calanId
            notifyDataSetChanged()
        }
    }
}
