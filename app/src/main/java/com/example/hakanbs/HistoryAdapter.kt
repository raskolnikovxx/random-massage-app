package com.example.hakanbs

import android.content.Context
import android.graphics.Typeface
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter(
    private val listener: HistoryItemListener
) : ListAdapter<NotificationHistory, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    private var originalHistoryList: List<NotificationHistory> = emptyList()
    private var mediaPlayer: MediaPlayer? = null
    // ID of the history item whose audio is currently loaded/playing (null if none)
    private var playingHistoryId: Long? = null
    // track previous playing id for efficient UI updates
    private var previousPlayingId: Long? = null
    private lateinit var context: Context

    // Date format
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) { /* ignore */ }
        try {
            mediaPlayer?.release()
        } catch (e: Exception) { /* ignore */ }
        mediaPlayer = null
        // remember previous then clear
        previousPlayingId = playingHistoryId
        playingHistoryId = null
        // notify only affected items
        try {
            previousPlayingId?.let { id ->
                val idx = currentList.indexOfFirst { it.id == id }
                if (idx >= 0) notifyItemChanged(idx)
            }
        } catch (_: Exception) { }
    }

    // Yeni listeyi kaydet ve görüntüle
    fun updateOriginalList(newList: List<NotificationHistory>) {
        originalHistoryList = newList.toList()
        submitList(originalHistoryList)
    }

    // Filtre uygulamak için: orijinali bozmadan ListAdapter'a gönder
    fun filter(query: String?) {
        if (query.isNullOrEmpty()) {
            submitList(originalHistoryList)
            return
        }
        val lowerCaseQuery = query.lowercase(Locale.getDefault())
        val results = originalHistoryList.filter {
            it.message.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                    it.context?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
        }
        submitList(results)
    }

    // DiffUtil
    class DiffCallback : DiffUtil.ItemCallback<NotificationHistory>() {
        override fun areItemsTheSame(oldItem: NotificationHistory, newItem: NotificationHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NotificationHistory, newItem: NotificationHistory): Boolean {
            return oldItem == newItem
        }
    }

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_history_message)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_history_image)
        private val tvContext: TextView = itemView.findViewById(R.id.tv_history_context)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_history_reaction)
        private val ivHeart: ImageView = itemView.findViewById(R.id.iv_react_heart)
        private val tvFavorite: TextView = itemView.findViewById(R.id.tv_favorite_toggle)
        private val ivAddComment: ImageView = itemView.findViewById(R.id.iv_add_comment)
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment_text)
        private val ivPlayAudio: ImageView = itemView.findViewById(R.id.iv_play_audio)
        private val ivPlayVideo: ImageView = itemView.findViewById(R.id.iv_play_video)

        fun bind(history: NotificationHistory) {
            tvTime.text = dateFormat.format(Date(history.time))
            tvMessage.text = history.message

            // Set whole item click to open detail
            itemView.setOnClickListener {
                listener.onItemClicked(history.id, history.message)
            }

            if (!history.context.isNullOrEmpty()) {
                tvContext.text = if (history.isQuote) "💬 ${history.context}" else "Anı: ${history.context}"
                tvContext.visibility = View.VISIBLE
            } else {
                tvContext.visibility = View.GONE
            }
            if (!history.reaction.isNullOrEmpty()) {
                tvReaction.text = history.reaction
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }
            if (history.comments.isNotEmpty()) {
                tvComment.text = "Not: ${history.comments.firstOrNull()?.text ?: "Çoklu Not Var"}"
                tvComment.visibility = View.VISIBLE
            } else {
                tvComment.visibility = View.GONE
            }

            if (history.isPinned) {
                tvFavorite.text = "Favorilere eklendi."
                tvFavorite.setTextColor(ContextCompat.getColor(itemView.context, R.color.purple_700))
                tvFavorite.setTypeface(null, Typeface.BOLD_ITALIC)
            } else {
                tvFavorite.text = "Favoriye ekle"
                tvFavorite.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                tvFavorite.setTypeface(null, Typeface.NORMAL)
            }

            // Clicks
            tvFavorite.setOnClickListener { listener.onFavoriteToggled(history, !history.isPinned) }
            ivHeart.setOnClickListener { listener.onReactClicked(history) }
            ivAddComment.setOnClickListener { listener.onCommentClicked(history.id, history.message, null) }

            // Media
            ivImage.visibility = View.GONE
            ivPlayVideo.visibility = View.GONE
            ivPlayAudio.visibility = View.GONE

            when {
                history.videoUrl != null -> {
                    ivPlayVideo.visibility = View.VISIBLE
                    ivImage.visibility = View.VISIBLE
                    ivImage.setImageResource(R.drawable.ic_image_placeholder)
                    ivImage.setOnClickListener { listener.onVideoClicked(history.videoUrl) }
                    ivPlayVideo.setOnClickListener { listener.onVideoClicked(history.videoUrl) }
                }
                history.imageUrl != null -> {
                    ivImage.visibility = View.VISIBLE
                    ivImage.load(history.imageUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_image_placeholder)
                        error(R.drawable.ic_image_error)
                    }
                    ivImage.setOnClickListener { listener.onImageClicked(history.imageUrl) }
                }
            }

            if (history.audioUrl != null) {
                ivPlayAudio.visibility = View.VISIBLE

                // Update icon based on whether this item is currently playing
                val isPlayingThis = (history.id == playingHistoryId) && (mediaPlayer?.isPlaying == true)
                ivPlayAudio.setImageResource(if (isPlayingThis) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

                ivPlayAudio.setOnClickListener {
                    try {
                        if (history.id == playingHistoryId) {
                            // toggle pause/resume
                            if (mediaPlayer?.isPlaying == true) {
                                mediaPlayer?.pause()
                                ivPlayAudio.setImageResource(android.R.drawable.ic_media_play)
                            } else {
                                mediaPlayer?.start()
                                ivPlayAudio.setImageResource(android.R.drawable.ic_media_pause)
                            }
                        } else {
                            // start new audio, stop previous
                            startPlaying(history.audioUrl ?: return@setOnClickListener, history.id)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(itemView.context, "Ses oynatılırken hata oluştu", Toast.LENGTH_SHORT).show()
                        releasePlayer()
                    }
                }
            }
        }
    }

    private fun startPlaying(url: String, historyId: Long) {
        // stop previous and remember previous id for UI update
        val prevId = playingHistoryId
        releasePlayer()
        playingHistoryId = if (historyId >= 0) historyId else null
        previousPlayingId = prevId

        // notify previous and new item's UI to update icons
        try {
            prevId?.let { id ->
                val idx = currentList.indexOfFirst { it.id == id }
                if (idx >= 0) notifyItemChanged(idx)
            }
            playingHistoryId?.let { id ->
                val idx = currentList.indexOfFirst { it.id == id }
                if (idx >= 0) notifyItemChanged(idx)
            }
        } catch (_: Exception) { }

        Toast.makeText(context, "Ses çalınıyor...", Toast.LENGTH_SHORT).show()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    // notify current item (prepared -> playing)
                    try {
                        playingHistoryId?.let { id ->
                            val idx = currentList.indexOfFirst { it.id == id }
                            if (idx >= 0) notifyItemChanged(idx)
                        }
                    } catch (_: Exception) { }
                }
                setOnCompletionListener {
                    // playback finished
                    releasePlayer()
                }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    Toast.makeText(context, "Ses çalınırken bir hata oluştu.", Toast.LENGTH_SHORT).show()
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Ses dosyası hazırlanamadı.", Toast.LENGTH_SHORT).show()
                releasePlayer()
            }
        }
    }
}