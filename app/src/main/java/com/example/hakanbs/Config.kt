package com.example.hakanbs

// Bildirim içerikleri
data class RemoteSentence(
    val id: String,
    val text: String,
    val isQuote: Boolean = false,
    val context: String? = null // Cümlenin anlamını açıklayan anı, şarkı sözü vb.
)

// Saat override (belli bir saatte belli bir mesaj göndermek için)
data class TimeOverride(
    val time: String, // "HH:mm" formatında (örn: "14:30")
    val messageId: String // Gönderilecek RemoteSentence ID'si
)

// Remote Config'den çekilen ana konfigürasyon yapısı
data class RemoteConfig(
    val enabled: Boolean = true, // <-- BURASI ARTIK TRUE
    val startHour: Int = 10,
    val endHour: Int = 22,
    val timesPerDay: Int = 8,
    val sentences: List<RemoteSentence> = listOf( // <-- VARSAYILAN CÜMLELER EKLENDİ
        RemoteSentence(id = "DEF01", text = "İyi ki varsın! Bu varsayılan bir başlangıç mesajıdır."),
        RemoteSentence(id = "DEF02", text = "Günün güzel geçsin! Firebase'den veri çekilene kadar bu cümleler kullanılır."),
        RemoteSentence(id = "DEF03", text = "Seni seviyorum 💖")
    ),
    val images: List<String> = emptyList(),
    val overrides: List<TimeOverride> = emptyList(),
    val activityTitle: String = "Sürpriz Notlar Geçmişi",
    val emptyMessage: String = "Henüz mesaj yok. İlk alarm ve yeni not bekleniyor!"
)

// Bildirim Geçmişi Kaydı (Yerel veritabanında saklanır)
data class NotificationHistory(
    val id: Long = 0,
    val time: Long,
    val messageId: String,
    val message: String,
    val reaction: String? = null,
    val imageUrl: String? = null,
    val isPinned: Boolean = false,
    val isQuote: Boolean = false,
    val context: String? = null
)