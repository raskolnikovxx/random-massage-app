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

// Yeni: RandomDaily yapılandırması
data class RandomDaily(
    val enabled: Boolean = false,
    val countPerDay: Int = 1,
    val pool: List<String> = emptyList()
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
    val coupons: List<Coupon> = emptyList(),
    // Yeni alan
    val randomDaily: RandomDaily = RandomDaily(),
    // Yeni: Seviye arka plan url'leri
    val backgroundUrl1: String = "",
    val backgroundUrl2: String = "",
    val backgroundUrl3: String = ""
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

            val remote = gson.fromJson(jsonString, RemoteConfig::class.java)
            // Sadece remote config kullanılıyor, yerel fallback kaldırıldı
            return@withContext remote
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config from Firebase: ${e.message}")
            return@withContext null
        }
    }

    // Merge remote config with embedded defaults from assets/default_config.json
    private fun mergeWithDefaults(remote: RemoteConfig): RemoteConfig {
        try {
            val input = context.assets.open("default_config.json")
            val size = input.available()
            val buffer = ByteArray(size)
            input.read(buffer)
            input.close()
            val defaultJson = String(buffer, Charsets.UTF_8)
            val defaults = gson.fromJson(defaultJson, RemoteConfig::class.java)

            // Build a map of defaults by id for quick replace/merge
            val defaultMap = defaults.sentences.associateBy { it.id }.toMutableMap()
            // Remote sentences overwrite defaults with same id; otherwise are added
            remote.sentences.forEach { s ->
                defaultMap[s.id] = s
            }
            val mergedSentences = defaultMap.values.toList()

            // Merge overrides: combine defaults + remote, remote overrides take precedence by time+messageId pair
            val combinedOverrides = (defaults.overrides + remote.overrides).distinctBy { Pair(it.time, it.messageId) }

            // Merge randomDaily: prefer remote if enabled, otherwise keep defaults
            val mergedRandomDaily = if (remote.randomDaily.enabled) remote.randomDaily else defaults.randomDaily

            // Other top-level fields prefer remote when provided
            return RemoteConfig(
                enabled = remote.enabled,
                startHour = remote.startHour,
                endHour = remote.endHour,
                timesPerDay = remote.timesPerDay,
                sentences = mergedSentences,
                overrides = combinedOverrides,
                activityTitle = if (remote.activityTitle.isNotBlank()) remote.activityTitle else defaults.activityTitle,
                emptyMessage = if (remote.emptyMessage.isNotBlank()) remote.emptyMessage else defaults.emptyMessage,
                decisionWheel = if (remote.decisionWheel.options.isNotEmpty()) remote.decisionWheel else defaults.decisionWheel,
                coupons = if (remote.coupons.isNotEmpty()) remote.coupons else defaults.coupons,
                randomDaily = mergedRandomDaily,
                backgroundUrl1 = if (!remote.backgroundUrl1.isNullOrBlank()) remote.backgroundUrl1 else defaults.backgroundUrl1,
                backgroundUrl2 = if (!remote.backgroundUrl2.isNullOrBlank()) remote.backgroundUrl2 else defaults.backgroundUrl2,
                backgroundUrl3 = if (!remote.backgroundUrl3.isNullOrBlank()) remote.backgroundUrl3 else defaults.backgroundUrl3
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load defaults for merge: ${e.message}")
            return remote
        }
    }

    // Ensure each sentence has a stable, unique id. If id is empty, generate SHA-256(text).
    // Also resolve duplicate ids by appending a short suffix and log warnings.
    private fun ensureSentenceIds(config: RemoteConfig): RemoteConfig {
        val seenIds = mutableSetOf<String>()
        val newSentences = mutableListOf<RemoteSentence>()
        val originalIds = config.sentences.map { it.id }

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

        // Build mapping from original ids to new ids (by position) to remap overrides if needed
        val idMapping = mutableMapOf<String, String>()
        for (i in originalIds.indices) {
            val orig = originalIds[i]
            val updated = newSentences.getOrNull(i)?.id ?: orig
            if (orig.isNotEmpty()) idMapping[orig] = updated
        }

        // Update overrides: if an override references an original id that has been changed, map it
        val newOverrides = config.overrides.map { o ->
            val mid = o.messageId
            if (!mid.isNullOrEmpty() && idMapping.containsKey(mid)) {
                o.copy(messageId = idMapping[mid])
            } else {
                o
            }
        }

        // Validate overrides reference existing ids; warn if they don't
        newOverrides.forEach { o ->
            if (!o.messageId.isNullOrEmpty() && o.messageId !in seenIds) {
                Log.w(TAG, "RemoteOverride references unknown messageId after mapping: ${o.messageId}")
            }
        }

        return config.copy(sentences = newSentences, overrides = newOverrides)
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
            // Attempt to load default from assets as a fallback
            try {
                val input = context.assets.open("default_config.json")
                val size = input.available()
                val buffer = ByteArray(size)
                input.read(buffer)
                input.close()
                val jsonString = String(buffer, Charsets.UTF_8)
                val cfg = gson.fromJson(jsonString, RemoteConfig::class.java)
                // ensure ids and validity
                val validated = ensureSentenceIds(cfg)

                // Aynı mantık: randomDaily etkinse ve pool boşsa, tüm sentence id'lerini ekle
                val final = if (validated.randomDaily.enabled && validated.randomDaily.pool.isEmpty()) {
                    val allIds = validated.sentences.map { it.id }
                    validated.copy(randomDaily = validated.randomDaily.copy(pool = allIds))
                } else {
                    validated
                }

                saveConfigLocally(final)
                return final
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load local config from assets: ${e.message}")
                return RemoteConfig()
            }
        }
    }
}