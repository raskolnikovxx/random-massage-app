package com.example.hakanbs

import com.google.firebase.firestore.Exclude
import com.google.gson.annotations.SerializedName

// YENİ MODEL: Her bir yorum/not bu yapıda saklanır.
data class Note(
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

// Firebase'den çekilen mesaj yapısı
data class RemoteSentence(
    val id: String = "",
    val text: String = "",
    val isQuote: Boolean = false,
    val context: String? = null
)

// Firebase'den çekilen override yapısı
data class RemoteOverride(
    val time: String = "",
    val messageId: String? = null,
    val imageUrl: String? = null
)

// Remote Config'den çekilen ana konfigürasyon yapısı
data class RemoteConfig(
    val enabled: Boolean = true,
    val startHour: Int = 10,
    val endHour: Int = 22,
    val timesPerDay: Int = 8,
    val sentences: List<RemoteSentence> = emptyList(),
    val images: List<String> = emptyList(),
    val overrides: List<RemoteOverride> = emptyList(),
    val activityTitle: String = "Anılarımız",
    val emptyMessage: String = "Henüz anı yok..."
)

// Bildirim geçmişi için yerel veri modeli (COMMENT -> COMMENTS LİSTESİ OLDU)
data class NotificationHistory(
    val id: Long = System.currentTimeMillis(),
    val time: Long = 0,
    val messageId: String = "",
    val message: String = "",
    val imageUrl: String? = null,
    val isQuote: Boolean = false,
    val context: String? = null,
    val reaction: String? = null,
    val comments: List<Note> = emptyList(), // YORUM LİSTESİ OLARAK DEĞİŞTİ
    val isPinned: Boolean = false
)

// Firestore'a kaydedilecek veri modeli (COMMENT -> COMMENTS LİSTESİ OLDU)
data class FirestoreHistoryItem(
    @get:Exclude val deviceId: String = "",
    val historyId: Long = 0,
    val messageId: String = "",
    val timestamp: Long = 0,
    val reaction: String? = null,
    val comments: List<Note> = emptyList(), // YORUM LİSTESİ OLARAK DEĞİŞTİ
    val isPinned: Boolean = false
)