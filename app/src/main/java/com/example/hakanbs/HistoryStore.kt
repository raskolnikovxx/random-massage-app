package com.example.hakanbs

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryStore(context: Context) {
    private val TAG = "HistoryStore"
    private val historyPrefs: SharedPreferences = context.getSharedPreferences("NotificationHistory", Context.MODE_PRIVATE)
    private val seenPrefs: SharedPreferences = context.getSharedPreferences("SeenSentenceIds", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    private val HISTORY_LIST_KEY = "history_list"

    // --- History (Geçmiş) İşlemleri ---

    fun getHistory(): List<NotificationHistory> {
        val json = historyPrefs.getString(HISTORY_LIST_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<NotificationHistory>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

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

    // --- Firestore Yazma Mantığı ---

    private fun sendToFirestore(history: NotificationHistory) {
        // Bu satırda Unresolved reference hatası vardı. Şimdi çalışmalı.
        val firestoreItem = FirestoreHistoryItem(
            deviceId = deviceId,
            historyId = history.id,
            messageId = history.messageId,
            timestamp = history.time,
            reaction = history.reaction,
            isPinned = history.isPinned
        )

        firestore.collection("devices")
            .document(deviceId)
            .collection("history")
            .document(history.id.toString())
            .set(firestoreItem)
            .addOnSuccessListener {
                Log.d(TAG, "Firestore updated successfully for ID: ${history.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error writing to Firestore: ${e.message}")
            }
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