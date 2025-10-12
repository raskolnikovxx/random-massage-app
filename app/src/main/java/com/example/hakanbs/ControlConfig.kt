package com.example.hakanbs

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.Exclude
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.experimental.and

// Veri sınıfları
data class DecisionWheelOption(
    val title: String = "Ne Yapsak?",
    val options: List<String> = listOf("Park", "Sinema", "Yemek", "Alışveriş", "Spor", "Müze")
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
    val audioUrl: String? = null,
    val videoUrl: String? = null
)

data class RemoteOverride(
    val time: String = "",
    val messageId: String? = null,
    val imageUrl: String? = null
)

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
    val coupons: List<Coupon> = emptyList()
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
    val videoUrl: String? = null,
    val type: String = ""
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

class ControlConfig(private val context: Context) {
    private val TAG = "ControlConfig"
    private val PREFS_NAME = "RemoteConfigPrefs"
    private val CONFIG_KEY = "current_config"

    private val gson = Gson()
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 60
        }
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)

        val defaultJson = gson.toJson(RemoteConfig())
        firebaseRemoteConfig.setDefaultsAsync(mapOf("app_config" to defaultJson))
    }

    suspend fun fetchConfig(): RemoteConfig? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Fetching config from Firebase Remote Config.")
        try {
            firebaseRemoteConfig.fetchAndActivate().await()
            val jsonString = firebaseRemoteConfig.getString("app_config")

            if (jsonString.isNullOrEmpty()) {
                Log.e(TAG, "Empty config retrieved from Firebase.")
                return@withContext null
            }

            val config = gson.fromJson(jsonString, RemoteConfig::class.java)

            // Ensure sentences have stable, unique ids. This handles RemoteConfig updates where
            // some sentences may lack an id or ids might collide after edits.
            val validatedConfig = ensureSentenceIds(config)

            saveConfigLocally(validatedConfig)
            Log.d(TAG, "Config fetched, validated and saved successfully.")
            return@withContext validatedConfig
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config from Firebase: ${e.message}")
            return@withContext null
        }
    }

    // Ensure each sentence has a stable, unique id. If id is empty, generate SHA-256(text).
    // Also resolve duplicate ids by appending a short suffix and log warnings.
    private fun ensureSentenceIds(config: RemoteConfig): RemoteConfig {
        val seenIds = mutableSetOf<String>()
        val newSentences = mutableListOf<RemoteSentence>()

        config.sentences.forEachIndexed { idx, s ->
            var id = s.id.orEmpty().trim()
            if (id.isEmpty()) {
                id = generateStableId(s.text)
                Log.w(TAG, "Generated id for sentence at index $idx: $id")
            }

            // If collision, make id unique by appending index
            if (id in seenIds) {
                val uniqueId = "$id-$idx"
                Log.w(TAG, "Duplicate id detected: $id. Renaming to $uniqueId")
                id = uniqueId
            }

            seenIds.add(id)
            newSentences.add(s.copy(id = id))
        }

        // Validate overrides reference existing ids; warn if they don't
        config.overrides.forEach { o ->
            if (!o.messageId.isNullOrEmpty() && o.messageId !in seenIds) {
                Log.w(TAG, "RemoteOverride references unknown messageId: ${o.messageId}")
            }
        }

        return config.copy(sentences = newSentences)
    }

    private fun generateStableId(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
            bytes.joinToString(separator = "") { String.format("%02x", it and 0xff.toByte()) }
        } catch (e: Exception) {
            // Fallback: use text hashCode if MessageDigest unavailable
            Log.w(TAG, "SHA-256 not available, falling back to hashCode for id generation.")
            "id_${text.hashCode().toString(16)}"
        }
    }

    private fun saveConfigLocally(config: RemoteConfig) {
        val json = gson.toJson(config)
        sharedPrefs.edit().putString(CONFIG_KEY, json).apply()
    }

    fun getLocalConfig(): RemoteConfig {
        val json = sharedPrefs.getString(CONFIG_KEY, null)
        return if (json != null) {
            gson.fromJson(json, RemoteConfig::class.java)
        } else {
            RemoteConfig()
        }
    }
}