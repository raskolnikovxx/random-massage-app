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
    private val appContext: Context = context.applicationContext
    private val historyPrefs: SharedPreferences = appContext.getSharedPreferences("NotificationHistory", Context.MODE_PRIVATE)
    private val seenPrefs: SharedPreferences = appContext.getSharedPreferences("SeenSentenceIds", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        // Global lock to synchronize access to the seen-id SharedPreferences across
        // different HistoryStore instances (prevents race conditions when alarms
        // are executed concurrently in different threads).
        private val SEEN_LOCK = Any()
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val deviceId: String = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
    private val HISTORY_LIST_KEY = "history_list"
    private val SEEN_ID_SET_KEY = "seen_id_set"


    // Seen list'ini tamamen sıfırlar (yalnızca hangi cümlelerin gösterildiği bilgisini temizler).
    // NOT: Bu işlem NotificationHistory (geçmiş) kayıtlarını silmez; sadece "görüldü" ID setini temizler.
    fun clearSeenSentenceIds() {
        synchronized(SEEN_LOCK) {
            seenPrefs.edit().remove(SEEN_ID_SET_KEY).apply()
            Log.d(TAG, "Seen sentence ids cleared.")
        }
    }

    fun getHistory(): List<NotificationHistory> {
        val json = historyPrefs.getString(HISTORY_LIST_KEY, null)
        if (json.isNullOrEmpty()) {
            // Değişiklik: Uygulama ilk açıldığında assets'ten otomatik yükleme yapma.
            // Eğer kullanıcı veya bir admin elle yüklemek isterse resetHistoryToDefault() kullanılabilir.
            return emptyList()
        }
        val type = object : TypeToken<List<NotificationHistory>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    // Yeni: assets/default_config.json içindeki sentences listesini okuyup NotificationHistory listesine çevirir.
    private fun loadHistoryFromAssets(): List<NotificationHistory> {
        return try {
            val input = appContext.assets.open("default_config.json")
            val size = input.available()
            val buffer = ByteArray(size)
            input.read(buffer)
            input.close()
            val jsonString = String(buffer, Charsets.UTF_8)

            // RemoteConfig modeli ControlConfig.kt içinde tanımlı
            val remoteConfig = gson.fromJson(jsonString, RemoteConfig::class.java)
            val sentences = remoteConfig.sentences

            val now = System.currentTimeMillis()
            val historyList = sentences.mapIndexed { idx, s ->
                val stableId = try {
                    val mid = s.id.ifEmpty { s.text.hashCode().toString() }
                    var idLong = mid.hashCode().toLong()
                    if (idLong == Long.MIN_VALUE) idLong = idLong + 1
                    if (idLong < 0) idLong = -idLong
                    idLong
                } catch (e: Exception) {
                    System.currentTimeMillis() + idx
                }

                NotificationHistory(
                    id = stableId,
                    time = now + idx, // küçük artışlarla zaman ver
                    messageId = s.id,
                    message = s.text,
                    imageUrl = s.imageUrl,
                    isQuote = s.isQuote,
                    context = s.context,
                    reaction = null,
                    comments = emptyList(),
                    isPinned = false,
                    audioUrl = s.audioUrl,
                    videoUrl = s.videoUrl,
                    type = ""
                )
            }
            historyList
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load history from assets: ${e.message}")
            emptyList()
        }
    }

    // Geçmişi güncelleyen temel fonksiyon (reaksiyon ve sabitleme için)
    fun updateHistoryItem(historyId: Long, newReaction: String? = null, newPinState: Boolean? = null) {
        var updatedItemForFirestore: NotificationHistory? = null
        synchronized(this) {
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
                updatedItemForFirestore = updatedItem
            }
        }
        updatedItemForFirestore?.let { sendToFirestore(it) }
    }

    // Yeni notu listeye ekler
    fun addNoteToHistoryItem(historyId: Long, noteText: String) {
        var updatedItemForFirestore: NotificationHistory? = null
        synchronized(this) {
            val currentHistory = getHistory().toMutableList()
            val index = currentHistory.indexOfFirst { it.id == historyId }

            if (index != -1) {
                val oldItem = currentHistory[index]
                val newNote = Note(text = noteText, timestamp = System.currentTimeMillis())
                val updatedNotes = oldItem.comments.toMutableList().apply {
                    add(0, newNote) // En yeni not en üste
                }
                val updatedItem = oldItem.copy(
                    comments = updatedNotes
                )
                currentHistory[index] = updatedItem
                saveHistory(currentHistory)
                updatedItemForFirestore = updatedItem
            }
        }
        updatedItemForFirestore?.let { sendToFirestore(it) }
    }

    fun addNotificationToHistory(history: NotificationHistory) {
        synchronized(this) {
            val currentHistory = getHistory().toMutableList()
            currentHistory.add(0, history)

            if (currentHistory.size > 100) {
                currentHistory.subList(100, currentHistory.size).clear()
            }
            saveHistory(currentHistory)
        }
        sendToFirestore(history)
    }

    private fun saveHistory(historyList: List<NotificationHistory>) {
        val json = gson.toJson(historyList)
        historyPrefs.edit().putString(HISTORY_LIST_KEY, json).apply()
    }

    /**
     * Kullanıcıya, uygulamadaki history girişlerini assets/default_config.json içeriği ile
     * sıfırlama imkanı verir. Döndürür: true ise yükleme başarılı ve kaydedildi.
     */
    fun resetHistoryToDefault(): Boolean {
        val loaded = loadHistoryFromAssets()
        return if (loaded.isNotEmpty()) {
            saveHistory(loaded)
            true
        } else {
            false
        }
    }

    // --- Firestore Yazma Mantığı ---

    private fun sendToFirestore(history: NotificationHistory) {
        val firestoreItem = FirestoreHistoryItem(
            deviceId = deviceId,
            historyId = history.id,
            messageId = history.messageId,
            timestamp = history.time,
            reaction = history.reaction,
            comments = history.comments,
            isPinned = history.isPinned
        )
        firestore.collection("history")
            .document("${deviceId}_${history.id}")
            .set(firestoreItem)
            .addOnSuccessListener { Log.d(TAG, "History item ${history.id} synced to Firestore.") }
            .addOnFailureListener { e -> Log.w(TAG, "Error syncing item ${history.id} to Firestore", e) }
    }

    // --- Seen Store (Görülenler) İşlemleri ---

    fun addSeenSentenceId(messageId: String) {
        synchronized(SEEN_LOCK) {
            val currentJson = seenPrefs.getString(SEEN_ID_SET_KEY, null)
            val type = object : TypeToken<Set<String>>() {}.type
            val seenIds = if (currentJson.isNullOrEmpty()) mutableSetOf<String>() else gson.fromJson<Set<String>>(currentJson, type).toMutableSet()
            if (seenIds.add(messageId)) {
                val json = gson.toJson(seenIds)
                seenPrefs.edit().putString(SEEN_ID_SET_KEY, json).apply()
                Log.d(TAG, "addSeenSentenceId: added $messageId, total seen=${seenIds.size}")
            } else {
                Log.d(TAG, "addSeenSentenceId: $messageId already present in seen set")
            }
        }
    }

    fun getSeenSentenceIds(): Set<String> {
        synchronized(SEEN_LOCK) {
            val json = seenPrefs.getString(SEEN_ID_SET_KEY, null) ?: return emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            return gson.fromJson(json, type) ?: emptySet()
        }
    }
}