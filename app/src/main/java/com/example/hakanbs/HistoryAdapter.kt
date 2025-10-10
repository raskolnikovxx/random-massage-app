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
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter(
    private var originalHistoryList: List<NotificationHistory>,
    private val listener: HistoryItemListener
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var filteredHistoryList: MutableList<NotificationHistory> = originalHistoryList.toMutableList()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var context: Context

    // Hatanın olduğu 'dateFormat' değişkeni burada tanımlanıyor.
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        context = parent.context
        val view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun updateList(newList: List<NotificationHistory>) {
        originalHistoryList = newList
        filteredHistoryList = originalHistoryList.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String?) {
        filteredHistoryList.clear()
        if (query.isNullOrEmpty()) {
            filteredHistoryList.addAll(originalHistoryList)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val results = originalHistoryList.filter {
                it.message.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        it.context?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
            }
            filteredHistoryList.addAll(results)
        }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = filteredHistoryList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = filteredHistoryList.size

    // 'inner' kelimesi, bu sınıfın 'dateFormat' gibi dış sınıf değişkenlerine erişebilmesini sağlar.
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
            tvTime.text = dateFormat.format(Date(history.time)) // Hatanın olduğu satır
            tvMessage.text = history.message

            if (!history.context.isNullOrEmpty()) {
                tvContext.text = if (history.isQuote) "💬 ${history.context}" else "Anı: ${history.context}"
                tvContext.visibility = View.VISIBLE
            } else { tvContext.visibility = View.GONE }
            if (!history.reaction.isNullOrEmpty()) {
                tvReaction.text = history.reaction
                tvReaction.visibility = View.VISIBLE
            } else { tvReaction.visibility = View.GONE }
            if (history.comments.isNotEmpty()) {
                tvComment.text = "Not: ${history.comments.firstOrNull()?.text ?: "Çoklu Not Var"}"
                tvComment.visibility = View.VISIBLE
            } else { tvComment.visibility = View.GONE }

            if (history.isPinned) {
                tvFavorite.text = "Favorilere eklendi."
                tvFavorite.setTextColor(ContextCompat.getColor(itemView.context, R.color.purple_700))
                tvFavorite.setTypeface(null, Typeface.BOLD_ITALIC)
            } else {
                tvFavorite.text = "Favoriye ekle"
                tvFavorite.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                tvFavorite.setTypeface(null, Typeface.NORMAL)
            }

            // Tıklama Olayları
            tvFavorite.setOnClickListener { listener.onFavoriteToggled(history, !history.isPinned) }
            ivHeart.setOnClickListener { listener.onReactClicked(history) }
            ivAddComment.setOnClickListener { listener.onCommentClicked(history.id, history.message, null) }

            // Medya Gösterim Mantığı
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
                ivPlayAudio.setOnClickListener { playAudio(history.audioUrl) }
            }
        }
    }

    private fun playAudio(url: String) {
        try {
            releasePlayer()
            Toast.makeText(context, "Ses çalınıyor...", Toast.LENGTH_SHORT).show()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { start() }
                setOnCompletionListener { releasePlayer() }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    Toast.makeText(context, "Ses çalınırken bir hata oluştu.", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ses dosyası hazırlanamadı.", Toast.LENGTH_SHORT).show()
        }
    }
}