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

// Bildirim geçmişini RecyclerView'da göstermek için adaptör
class HistoryAdapter(
    private var historyList: List<NotificationHistory>,
    private val listener: HistoryItemListener // Harici Listener'ı kullanıyor
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    // Geçmiş saatleri cihazın yerel saat dilimine göre gösterir
    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    fun updateList(newList: List<NotificationHistory>) {
        // En son sabitlenenleri üste çıkarmak için listeyi yeniden sırala
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

        // Tepki/Sabitleme Bileşenleri
        private val tvReaction: TextView = itemView.findViewById(R.id.tv_history_reaction)
        private val ivHeart: ImageView = itemView.findViewById(R.id.iv_react_heart)
        private val ivPin: ImageView = itemView.findViewById(R.id.iv_pin_toggle)

        fun bind(history: NotificationHistory) {
            tvTime.text = dateFormat.format(Date(history.time))
            tvMessage.text = history.message

            // Anı/Context metni
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
            ivPin.setImageResource(
                if (history.isPinned) R.drawable.ic_pinned else R.drawable.ic_pin
            )

            // --- TIKLAMA OLAYLARI (ETKİLEŞİM) ---
            ivHeart.setOnClickListener {
                listener.onReactClicked(history, "❤️") // Kalp tepkisi gönder
            }

            ivPin.setOnClickListener {
                listener.onPinToggled(history, !history.isPinned) // Sabitleme durumunu tersine çevir
            }

            // Görsel Yükleme ve Tıklama
            history.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    ivImage.visibility = View.VISIBLE
                    ivImage.load(url) {
                        crossfade(true)
                        placeholder(R.drawable.ic_image_placeholder)
                        error(R.drawable.ic_image_error)
                    }
                    // Görsele tıklandığında Full-Screen açılmasını tetikle
                    ivImage.setOnClickListener {
                        listener.onImageClicked(url)
                    }
                } else {
                    ivImage.visibility = View.GONE
                    ivImage.setOnClickListener(null)
                }
            }
        }
    }
}