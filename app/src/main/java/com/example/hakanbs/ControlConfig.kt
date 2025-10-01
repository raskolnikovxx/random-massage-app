package com.example.hakanbs

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ControlConfig(private val context: Context) {
    private val TAG = "ControlConfig"

    private val PREFS_NAME = "RemoteConfigPrefs"
    private val CONFIG_KEY = "current_config"

    private val gson = Gson()
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val firebaseRemoteConfig = FirebaseRemoteConfig.getInstance()


    init {
        // Firebase Remote Config ayarları başlatılır
        val configSettings = remoteConfigSettings {
            // Hata veren BuildConfig.DEBUG yerine basit bir kontrol veya sabit süre kullanılır (Geliştirme için 60 saniye)
            minimumFetchIntervalInSeconds = 60
        }
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings)

        // Varsayılan boş konfigürasyonu ayarla
        val defaultJson = gson.toJson(RemoteConfig())
        firebaseRemoteConfig.setDefaultsAsync(mapOf("app_config" to defaultJson))
    }

    // ... (geri kalan fonksiyonlar aynı)

    // Firebase'den JSON'u çeker ve yerel olarak kaydeder
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
            saveConfigLocally(config)
            return@withContext config

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config from Firebase: ${e.message}")
            return@withContext null
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
            gson.fromJson(firebaseRemoteConfig.getString("app_config"), RemoteConfig::class.java)
        }
    }
}