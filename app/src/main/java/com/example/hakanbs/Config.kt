package com.example.hakanbs

import com.google.firebase.firestore.Exclude
import com.google.gson.annotations.SerializedName

// YENİ YARDIMCI SINIFLAR
data class DecisionWheelOption(
    val title: String = "Ne Yapsak?",
    val options: List<String> = emptyList()
)

data class Note(
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class RemoteSentence(
    val id: String = "",
    val text: String = "",
    val isQuote: Boolean = false,
    val context: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null, // YENİ ALAN
    val videoUrl: String? = null  // YENİ ALAN
)

data class RemoteOverride(
    val time: String = "",
    val messageId: String? = null,
    val imageUrl: String? = null
)

// GÜNCELLENMİŞ RemoteConfig SINIFI
data class RemoteConfig(
    val enabled: Boolean = true,
    val startHour: Int = 10,
    val endHour: Int = 22,
    val timesPerDay: Int = 8,
    val sentences: List<RemoteSentence> = emptyList(),
    val overrides: List<RemoteOverride> = emptyList(),
    val activityTitle: String = "Anılarımız",
    val emptyMessage: String = "Henüz anı yok...",
    val decisionWheel: DecisionWheelOption = DecisionWheelOption(),
    val coupons: List<Coupon> = emptyList() // YENİ ALAN
)

data class NotificationHistory(
    val id: Long = System.currentTimeMillis(),
    val time: Long = 0,
    val messageId: String = "",
    val message: String = "",
    val imageUrl: String? = null,
    val isQuote: Boolean = false,
    val context: String? = null,
    val reaction: String? = null,
    val comments: List<Note> = emptyList(),
    val isPinned: Boolean = false,
    val audioUrl: String? = null,
    val videoUrl: String? = null
)

data class Coupon(
    val id: String = "",
    val title: String = "",
    val description: String = ""
)

data class FirestoreHistoryItem(
    @get:Exclude val deviceId: String = "",
    val historyId: Long = 0,
    val messageId: String = "",
    val timestamp: Long = 0,
    val reaction: String? = null,
    val comments: List<Note> = emptyList(),
    val isPinned: Boolean = false
)