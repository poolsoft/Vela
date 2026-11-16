package app.vela.carlauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.vela.R
import app.vela.carlauncher.media.libraryKey
import app.vela.carlauncher.model.SesParcasi

class MiniQueueAdapter(
    private val onItemClick: (SesParcasi) -> Unit
) : ListAdapter<SesParcasi, MiniQueueAdapter.ViewHolder>(DiffCallback) {

    private var currentPlayingKey: String? = null

    fun updateQueue(list: List<SesParcasi>, key: String?, onDone: (() -> Unit)? = null) {
        val keyChanged = currentPlayingKey != key
        currentPlayingKey = key
        submitList(ArrayList(list)) {
            if (keyChanged) {
                notifyDataSetChanged()
            }
            onDone?.invoke()
        }
    }

    fun setCurrentPlayingKey(key: String?) {
        if (currentPlayingKey != key) {
            currentPlayingKey = key
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mini_queue_track, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isPlaying = item.libraryKey() == currentPlayingKey
        holder.bind(item, position + 1, isPlaying)
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

        fun bind(item: SesParcasi, displayIndex: Int, isPlaying: Boolean) {
            currentItem = item
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

    private object DiffCallback : DiffUtil.ItemCallback<SesParcasi>() {
        override fun areItemsTheSame(oldItem: SesParcasi, newItem: SesParcasi): Boolean =
            oldItem.libraryKey() == newItem.libraryKey()

        override fun areContentsTheSame(oldItem: SesParcasi, newItem: SesParcasi): Boolean =
            oldItem == newItem
    }
}
