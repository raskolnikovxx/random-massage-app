package com.example.hakanbs

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore

class HistoryStore(context: Context) {
    private val TAG = "HistoryStore"
    private val historyPrefs: SharedPreferences = context.getSharedPreferences("NotificationHistory", Context.MODE_PRIVATE)
    private val seenPrefs: SharedPreferences = context.getSharedPreferences("SeenSentenceIds", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    private val HISTORY_LIST_KEY = "history_list"
    private val SEEN_ID_SET_KEY = "seen_id_set"


    fun getHistory(): List<NotificationHistory> {
        val json = historyPrefs.getString(HISTORY_LIST_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<NotificationHistory>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // Geçmişi güncelleyen temel fonksiyon (reaksiyon ve sabitleme için)
    fun updateHistoryItem(historyId: Long, newReaction: String? = null, newPinState: Boolean? = null) {
        val currentHistory = getHistory().toMutableList()
        val index = currentHistory.indexOfFirst { it.id == historyId }

        if (index != -1) {
            val oldItem = currentHistory[index]

            // 1. Yerel Veriyi Güncelle
            val updatedItem = oldItem.copy(
                reaction = newReaction ?: oldItem.reaction,
                isPinned = newPinState ?: oldItem.isPinned
                // comments listesi burada değişmez
            )
            currentHistory[index] = updatedItem
            saveHistory(currentHistory)

            // 2. FIREBASE'E GÜNCELLEMEYİ GÖNDER
            sendToFirestore(updatedItem)
        }
    }

    // YENİ/KRİTİK FONKSİYON: Yeni notu listeye ekler (silinmezlik kuralı)
    fun addNoteToHistoryItem(historyId: Long, noteText: String) {
        val currentHistory = getHistory().toMutableList()
        val index = currentHistory.indexOfFirst { it.id == historyId }

        if (index != -1) {
            val oldItem = currentHistory[index]

            // Yeni not objesi oluşturuluyor ve listeye ekleniyor
            val newNote = Note(text = noteText, timestamp = System.currentTimeMillis())
            val updatedNotes = oldItem.comments.toMutableList().apply {
                add(0, newNote) // En yeni not en üste
            }

            // 1. Yerel Veriyi Güncelle
            val updatedItem = oldItem.copy(
                comments = updatedNotes // YORUM LİSTESİ ATANDI
            )
            currentHistory[index] = updatedItem
            saveHistory(currentHistory)

            // 2. FIREBASE'E GÜNCELLEMEYİ GÖNDER
            sendToFirestore(updatedItem)
        }
    }


    fun addNotificationToHistory(history: NotificationHistory) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.add(0, history)

        if (currentHistory.size > 100) {
            currentHistory.subList(100, currentHistory.size).clear()
        }
        saveHistory(currentHistory)

        sendToFirestore(history)
    }

    private fun saveHistory(historyList: List<NotificationHistory>) {
        val json = gson.toJson(historyList)
        historyPrefs.edit().putString(HISTORY_LIST_KEY, json).apply()
    }

    // --- Firestore Yazma Mantığı (Değişmedi) ---

    private fun sendToFirestore(history: NotificationHistory) {
        val firestoreItem = FirestoreHistoryItem(
            deviceId = deviceId,
            historyId = history.id,
            messageId = history.messageId,
            timestamp = history.time,
            reaction = history.reaction,
            comments = history.comments, // YORUM LİSTESİ
            isPinned = history.isPinned
        )
        // ... (Firestore yazma kodları aynı) ...
    }

    // --- Seen Store (Görülenler) İşlemleri (AlarmReceiver hatalarını çözer) ---

    // Hata veren addSeenSentenceId metodu
    fun addSeenSentenceId(messageId: String) {
        val seenIds = getSeenSentenceIds().toMutableSet()
        seenIds.add(messageId)
        val json = gson.toJson(seenIds)
        seenPrefs.edit().putString(SEEN_ID_SET_KEY, json).apply()
    }

    // Hata veren getSeenSentenceIds metodu
    fun getSeenSentenceIds(): Set<String> {
        val json = seenPrefs.getString(SEEN_ID_SET_KEY, null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }
}