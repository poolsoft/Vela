package app.vela.carlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.media.libraryKey
import app.vela.carlauncher.model.SesParcasi

class MiniQueueAdapter(
    private val onItemClick: (SesParcasi) -> Unit
) : RecyclerView.Adapter<MiniQueueAdapter.ViewHolder>() {

    private var items: List<SesParcasi> = emptyList()
    private var currentPlayingKey: String? = null
    private var currentPlayingTitle: String? = null
    private var titleTextSizeSp: Float = 14f
    private var subtitleTextSizeSp: Float = 11f
    private var indexTextSizeSp: Float = 12f
    private var durationTextSizeSp: Float = 11f

    fun setTextSizes(titleSp: Float, subtitleSp: Float, indexSp: Float = 12f, durationSp: Float = 11f) {
        if (titleTextSizeSp != titleSp || subtitleTextSizeSp != subtitleSp || indexTextSizeSp != indexSp || durationTextSizeSp != durationSp) {
            titleTextSizeSp = titleSp
            subtitleTextSizeSp = subtitleSp
            indexTextSizeSp = indexSp
            durationTextSizeSp = durationSp
            notifyDataSetChanged()
        }
    }

    fun updateQueue(list: List<SesParcasi>, key: String?, title: String? = null, onDone: (() -> Unit)? = null) {
        items = list
        currentPlayingKey = key
        currentPlayingTitle = title
        notifyDataSetChanged()
        onDone?.invoke()
    }

    fun updatePlaying(key: String?, title: String? = null) {
        if (currentPlayingKey != key || currentPlayingTitle != title) {
            currentPlayingKey = key
            currentPlayingTitle = title
            notifyDataSetChanged()
        }
    }

    fun setCurrentPlayingKey(key: String?) {
        updatePlaying(key, currentPlayingTitle)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_queue_track, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isMatchKey = currentPlayingKey != null && item.libraryKey() == currentPlayingKey
        val isMatchTitle = !currentPlayingTitle.isNullOrBlank() &&
            item.baslik.isNotBlank() &&
            item.baslik.trim().equals(currentPlayingTitle!!.trim(), ignoreCase = true)
        val isPlaying = isMatchKey || isMatchTitle
        holder.bind(item, position + 1, isPlaying, titleTextSizeSp, subtitleTextSizeSp, indexTextSizeSp, durationTextSizeSp)
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (SesParcasi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val indexText: TextView = itemView.findViewById(R.id.mini_queue_index)
        private val playingIcon: ImageView = itemView.findViewById(R.id.mini_queue_playing_icon)
        private val titleText: TextView = itemView.findViewById(R.id.mini_queue_title)
        private val artistText: TextView = itemView.findViewById(R.id.mini_queue_artist)
        private val durationText: TextView = itemView.findViewById(R.id.mini_queue_duration)

        private var currentItem: SesParcasi? = null

        init {
            itemView.setOnClickListener {
                currentItem?.let { onItemClick(it) }
            }
        }

        fun bind(
            item: SesParcasi,
            displayIndex: Int,
            isPlaying: Boolean,
            titleSp: Float,
            subtitleSp: Float,
            indexSp: Float,
            durationSp: Float
        ) {
            currentItem = item
            titleText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, titleSp)
            artistText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSp)
            indexText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, indexSp)
            durationText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, durationSp)

            titleText.text = if (item.baslik.isNotBlank()) item.baslik else "Bilinmeyen Parça"
            artistText.text = if (item.sanatci.isNotBlank()) item.sanatci else "Bilinmeyen Sanatçı"
            durationText.text = formatDuration(item.sureMs)

            if (isPlaying) {
                indexText.visibility = View.GONE
                playingIcon.visibility = View.VISIBLE
                titleText.setTextColor(0xFF3FD9CA.toInt())
            } else {
                indexText.visibility = View.VISIBLE
                playingIcon.visibility = View.GONE
                indexText.text = displayIndex.toString()
                titleText.setTextColor(0xFFFFFFFF.toInt())
            }
        }

        private fun formatDuration(ms: Long): String {
            if (ms <= 0) return "0:00"
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
    }
}
