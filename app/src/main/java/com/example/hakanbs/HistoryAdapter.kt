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

// HistoryItemListener arayüz tanımı bu dosyada OLMAMALIDIR (Harici dosyadan gelir)

class HistoryAdapter(
    private var historyList: List<NotificationHistory>,
    private val listener: HistoryItemListener
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    fun updateList(newList: List<NotificationHistory>) {
        historyList = newList.sortedByDescending { it.isPinned }.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Layout ID'leri
        private val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_history_message)
        private val ivImage: ImageView = itemView.findViewById(R.id.iv_history_image)
        private val tvContext: TextView = itemView.findViewById(R.id.tv_history_context)

        // Tepki/Sabitleme/Yorum Bileşenleri
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_history_reaction)
        private val ivHeart: ImageView = itemView.findViewById(R.id.iv_react_heart)
        private val ivPin: ImageView = itemView.findViewById(R.id.iv_pin_toggle)
        private val ivAddComment: ImageView = itemView.findViewById(R.id.iv_add_comment) // YENİ BUTON
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment_text)

        fun bind(history: NotificationHistory) {
            tvTime.text = dateFormat.format(Date(history.time))
            tvMessage.text = history.message

            // ... (Diğer bind mantıkları) ...

            // YORUM GÖSTERİMİ
            if (!history.comment.isNullOrBlank()) {
                tvComment.text = "Not: ${history.comment}"
                tvComment.visibility = View.VISIBLE
            } else {
                tvComment.visibility = View.GONE
            }

            // TIKLAMA OLAYLARI
            ivHeart.setOnClickListener {
                listener.onReactClicked(history, "❤️")
            }

            ivPin.setOnClickListener {
                listener.onPinToggled(history, !history.isPinned)
            }

            // YORUM EKLE BUTONU TIKLAMA OLAYI
            ivAddComment.setOnClickListener {
                listener.onCommentClicked(history.id, history.message, history.comment)
            }

            // Görsel Yükleme ve Tıklama (Değişmedi)
            history.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    ivImage.visibility = View.VISIBLE
                    ivImage.load(url) { /* ... */ }
                    ivImage.setOnClickListener { listener.onImageClicked(url) }
                } else {
                    ivImage.visibility = View.GONE
                    ivImage.setOnClickListener(null)
                }
            }
        }
    }
}