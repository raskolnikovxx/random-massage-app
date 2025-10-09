package com.example.hakanbs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HistoryAdapter(
    private var originalHistoryList: List<NotificationHistory>, // Orijinal tam liste
    private val listener: HistoryItemListener
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var filteredHistoryList: MutableList<NotificationHistory> = originalHistoryList.toMutableList() // Gösterilen/filtrelenen liste

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    fun updateList(newList: List<NotificationHistory>) {
        originalHistoryList = newList.sortedByDescending { it.isPinned }
        filteredHistoryList = originalHistoryList.toMutableList()
        notifyDataSetChanged()
    }

    // YENİ FİLTRELEME FONKSİYONU
    fun filter(query: String?) {
        filteredHistoryList.clear()
        if (query.isNullOrEmpty()) {
            filteredHistoryList.addAll(originalHistoryList)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            val results = originalHistoryList.filter {
                // Mesajda veya context'te arama yap
                it.message.lowercase(Locale.getDefault()).contains(lowerCaseQuery) ||
                        it.context?.lowercase(Locale.getDefault())?.contains(lowerCaseQuery) == true
            }
            filteredHistoryList.addAll(results)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = filteredHistoryList[position] // Artık filtrelenmiş listeyi kullanıyoruz
        holder.bind(item)
    }

    override fun getItemCount(): Int = filteredHistoryList.size // Artık filtrelenmiş listenin boyutunu kullanıyoruz

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // ... ViewHolder içindeki kodlar tamamen aynı kalabilir, değişiklik yok ...
        private val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_history_message)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_history_image)
        private val tvContext: TextView = itemView.findViewById(R.id.tv_history_context)
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_history_reaction)
        private val ivHeart: ImageView = itemView.findViewById(R.id.iv_react_heart)
        private val ivFavorite: ImageView = itemView.findViewById(R.id.iv_favorite_toggle)
        private val ivAddComment: ImageView = itemView.findViewById(R.id.iv_add_comment)
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment_text)

        fun bind(history: NotificationHistory) {
            tvTime.text = dateFormat.format(Date(history.time))
            tvMessage.text = history.message

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

            ivFavorite.setImageResource(
                if (history.isPinned) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )

            if (history.comments.isNotEmpty()) {
                tvComment.text = "Not: ${history.comments.firstOrNull()?.text ?: "Çoklu Not Var"}"
                tvComment.visibility = View.VISIBLE
            } else {
                tvComment.visibility = View.GONE
            }

            ivFavorite.setOnClickListener {
                listener.onFavoriteToggled(history, !history.isPinned)
            }

            ivHeart.setOnClickListener {
                listener.onReactClicked(history)
            }

            ivAddComment.setOnClickListener {
                listener.onCommentClicked(history.id, history.message, null)
            }

            history.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    ivImage.visibility = View.VISIBLE
                    ivImage.load(url) {
                        crossfade(true)
                        placeholder(R.drawable.ic_image_placeholder)
                        error(R.drawable.ic_image_error)
                    }
                    ivImage.setOnClickListener { listener.onImageClicked(url) }
                } else {
                    ivImage.visibility = View.GONE
                    ivImage.setOnClickListener(null)
                }
            } ?: run {
                ivImage.visibility = View.GONE
                ivImage.setOnClickListener(null)
            }
        }
    }
}