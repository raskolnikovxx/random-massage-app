package com.example.hakanbs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HistoryStore(context: Context) {
    private val historyPrefs: SharedPreferences = context.getSharedPreferences("NotificationHistory", Context.MODE_PRIVATE)
    private val seenPrefs: SharedPreferences = context.getSharedPreferences("SeenSentenceIds", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- History (Geçmiş) İşlemleri ---

    fun getHistory(): List<NotificationHistory> {
        val json = historyPrefs.getString("history_list", null) ?: return emptyList()
        val type = object : TypeToken<List<NotificationHistory>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // Geçmişi güncelleyen genel fonksiyon (reaksiyon ve sabitleme için kullanılır)
    fun updateHistoryItem(historyId: Long, newReaction: String? = null, newPinState: Boolean? = null) {
        val currentHistory = getHistory().toMutableList()
        val index = currentHistory.indexOfFirst { it.id == historyId }

        if (index != -1) {
            val oldItem = currentHistory[index]
            val updatedItem = oldItem.copy(
                reaction = newReaction ?: oldItem.reaction,
                isPinned = newPinState ?: oldItem.isPinned
            )
            currentHistory[index] = updatedItem
            saveHistory(currentHistory)

            // YENİ: Firebase Firestore'a gönderme mantığı buraya eklenecektir.
            // Bu kısım, FirestoreManager gibi bir sınıf gerektirecektir.
        }
    }

    fun addNotificationToHistory(history: NotificationHistory) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.add(0, history)

        if (currentHistory.size > 100) {
            currentHistory.subList(100, currentHistory.size).clear()
        }
        saveHistory(currentHistory)
    }

    private fun saveHistory(historyList: List<NotificationHistory>) {
        val json = gson.toJson(historyList)
        historyPrefs.edit().putString("history_list", json).apply()
    }

    // --- Seen Store (Görülenler) İşlemleri ---

    fun addSeenSentenceId(messageId: String) {
        val seenIds = getSeenSentenceIds().toMutableSet()
        seenIds.add(messageId)
        val json = gson.toJson(seenIds)
        seenPrefs.edit().putString("seen_id_set", json).apply()
    }

    fun getSeenSentenceIds(): Set<String> {
        val json = seenPrefs.getString("seen_id_set", null) ?: return emptySet()
        val type = object : TypeToken<Set<String>>() {}.type
        return gson.fromJson(json, type) ?: emptySet()
    }
}