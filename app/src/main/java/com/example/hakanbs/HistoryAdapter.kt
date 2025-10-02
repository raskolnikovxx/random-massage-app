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

// UYARI: HistoryItemListener arayüz tanımı BURADA OLMAMALIDIR (Harici dosyadan gelir, bu kurala uyulmuştur.)

class HistoryAdapter(
    private var historyList: List<NotificationHistory>,
    private val listener: HistoryItemListener // Harici Listener'ı kullanıyor
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    fun updateList(newList: List<NotificationHistory>) {
        // Favorilere göre sıralama (isPinned, isFavorite olarak kullanılıyor)
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
        private val ivFavorite: ImageView = itemView.findViewById(R.id.iv_favorite_toggle)
        private val ivAddComment: ImageView = itemView.findViewById(R.id.iv_add_comment)
        private val tvComment: TextView = itemView.findViewById(R.id.tv_comment_text) // YORUM GÖSTERİMİ İÇİN KULLANILIR

        fun bind(history: NotificationHistory) {
            tvTime.text = dateFormat.format(Date(history.time))
            tvMessage.text = history.message

            if (!history.context.isNullOrEmpty()) {
                tvContext.text = if (history.isQuote) "💬 ${history.context}" else "Anı: ${history.context}"
                tvContext.visibility = View.VISIBLE
            } else {
                tvContext.visibility = View.GONE
            }

            // Görünen Tepki
            if (!history.reaction.isNullOrEmpty()) {
                tvReaction.text = history.reaction
                tvReaction.visibility = View.VISIBLE
            } else {
                tvReaction.visibility = View.GONE
            }

            // Sabitleme İkonu Durumu
            ivFavorite.setImageResource(
                if (history.isPinned) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )

            // YENİ: Yorum Gösterimi (Listeden son yorumu alarak veya boş değilse göster)
            if (history.comments.isNotEmpty()) {
                // En son yorumu gösterir
                tvComment.text = "Not: ${history.comments.firstOrNull()?.text ?: "Çoklu Not Var"}"
                tvComment.visibility = View.VISIBLE
            } else {
                tvComment.visibility = View.GONE
            }

            // --- TIKLAMA OLAYLARI ---

            ivFavorite.setOnClickListener {
                listener.onFavoriteToggled(history, !history.isPinned)
            }

            ivHeart.setOnClickListener {
                listener.onReactClicked(history, "❤️")
            }

            // YORUM EKLE BUTONU AKTİF EDİLDİ
            ivAddComment.setOnClickListener {
                // Tıklama olayını MainActivity'ye ilet
                // Not: history.comment yerine artık null gönderiyoruz, çünkü MainAcitivity List<Note>'u kendisi çekmeli
                listener.onCommentClicked(history.id, history.message, null)
            }

            // Görsel Yükleme ve Tıklama (Değişmedi)
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
            }
        }
    }
}