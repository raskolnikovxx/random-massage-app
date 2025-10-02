package com.example.hakanbs

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkManager
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.widget.ImageView // ImageView sınıfı için

class MainActivity : AppCompatActivity(), HistoryItemListener {
    private val TAG = "MainActivity"

    private val POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE = 100
    private val PREFS_NAME = "AppPrefs"
    private val PREF_ALARM_DIALOG_SHOWN = "alarm_dialog_shown"

    // Layout bileşenleri
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var ivFavoritesToggle: ImageView // YENİ: Favoriler butonu değişkeni

    // Yardımcı sınıflar
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyStore: HistoryStore
    private lateinit var controlConfig: ControlConfig

    // Durum tutucu
    private var isShowingFavorites = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS granted.")
        } else {
            Toast.makeText(this, "Bildirim izni olmadan alarm gelmeyebilir.", Toast.LENGTH_SHORT).show()
        }
    }

    // --- HistoryItemListener Uygulamaları ---

    override fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?) {
        showCommentDialog(historyId, originalMessage, currentComment)
    }

    override fun onReactClicked(history: NotificationHistory, emoji: String) {
        val newReaction = if (history.reaction == emoji) null else emoji

        CoroutineScope(Dispatchers.IO).launch {
            historyStore.updateHistoryItem(history.id, newReaction = newReaction)
            withContext(Dispatchers.Main) {
                loadHistory()
            }
        }
    }

    override fun onFavoriteToggled(history: NotificationHistory, isFavorite: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            historyStore.updateHistoryItem(history.id, newPinState = isFavorite)
            withContext(Dispatchers.Main) {
                loadHistory()
            }
        }
    }

    override fun onImageClicked(imageUrl: String) {
        Toast.makeText(this, "Görsel Tıklandı: $imageUrl", Toast.LENGTH_SHORT).show()
    }
    // --- Listener Metotları Bitti ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        DailySchedulerWorker.enqueueWork(this)
        historyStore = HistoryStore(this)
        controlConfig = ControlConfig(this)

        setupUI()

        requestNotificationPermission()
        checkAlarmPermission()

        SyncRemoteWorker.schedule(this)
        startInitialSync()

        Log.d(TAG, "MainActivity initialized successfully.")
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    // --- Helper Fonksiyonlar ---

    private fun setupUI() {
        // Layout bileşenlerini bul
        recyclerView = findViewById(R.id.recycler_view_history)
        tvEmpty = findViewById(R.id.tv_empty_history)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        ivFavoritesToggle = findViewById(R.id.iv_favorites_toggle) // YENİ: Buton ataması

        // RecyclerView Kurulumu
        historyAdapter = HistoryAdapter(emptyList(), this)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        // FAVORİLER BUTONU TIKLAMA MANTIĞI BURADA
        ivFavoritesToggle.setOnClickListener {
            // Durumu tersine çevir
            isShowingFavorites = !isShowingFavorites
            // İkonu ve listeyi güncelle
            updateFavoritesToggleUI()
            loadHistory()
        }

        // Swipe-to-Refresh ve Config çekme
        swipeRefreshLayout.setOnRefreshListener {
            fetchRemoteConfig()
        }

        // İlk yüklemede UI'ı ayarla
        updateFavoritesToggleUI()
        loadHistory()
    }

    // YENİ: Favori Butonunun Görünümünü Yöneten Fonksiyon
    private fun updateFavoritesToggleUI() {
        if (isShowingFavorites) {
            // Favoriler gösteriliyorsa, dolu kalp ikonu
            ivFavoritesToggle.setImageResource(R.drawable.ic_favorite_filled)
            ivFavoritesToggle.contentDescription = "Tüm Geçmişi Göster"
            Toast.makeText(this, "Favori Anılar Filtrelendi.", Toast.LENGTH_SHORT).show()
        } else {
            // Tüm anılar gösteriliyorsa, boş kalp ikonu
            ivFavoritesToggle.setImageResource(R.drawable.ic_favorite_border)
            ivFavoritesToggle.contentDescription = "Favorileri Göster"
        }
    }


    private fun loadHistory() {
        swipeRefreshLayout.isRefreshing = true

        val allHistory = historyStore.getHistory()

        // LİSTEYİ FİLTRELEME MANTIĞI BURADA
        val displayList = if (isShowingFavorites) {
            allHistory.filter { it.isPinned } // Sadece favori (isPinned) olanları göster
        } else {
            allHistory // Tüm geçmişi göster
        }

        historyAdapter.updateList(displayList) // Filtrelenmiş listeyi adaptöre gönder

        val config = controlConfig.getLocalConfig()
        updateUiTexts(config)

        swipeRefreshLayout.isRefreshing = false
        tvEmpty.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun fetchRemoteConfig() {
        lifecycleScope.launch(Dispatchers.IO) {
            controlConfig.fetchConfig()

            val config = controlConfig.getLocalConfig()
            Planner(this@MainActivity, config).scheduleAllNotifications()

            withContext(Dispatchers.Main) {
                loadHistory()
            }
        }
    }

    private fun startInitialSync() {
        val oneTimeSync = androidx.work.OneTimeWorkRequestBuilder<SyncRemoteWorker>()
            .setInitialDelay(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(oneTimeSync)
    }

    private fun updateUiTexts(config: RemoteConfig) {
        supportActionBar?.title = config.activityTitle
        tvEmpty.text = config.emptyMessage
    }

    private fun showCommentDialog(historyId: Long, originalMessage: String, currentComment: String?) {
        val input = EditText(this).apply {
            setText(currentComment)
            hint = "Eşinize özel bir not..."
            setLines(3)
        }

        AlertDialog.Builder(this)
            .setTitle("Not Ekle / Düzenle")
            .setMessage("Anı: \"$originalMessage\"")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val newComment = input.text.toString()
                if (newComment.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        historyStore.updateHistoryItem(historyId, newComment = newComment)
                        withContext(Dispatchers.Main) {
                            loadHistory()
                            Toast.makeText(this@MainActivity, "Not kaydedildi.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Not boş bırakılamaz.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAlarmPermission() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms() && !prefs.getBoolean(PREF_ALARM_DIALOG_SHOWN, false)) {

                prefs.edit().putBoolean(PREF_ALARM_DIALOG_SHOWN, true).apply()

                AlertDialog.Builder(this)
                    .setTitle("BİLGİLENDİRME")
                    .setMessage("Uygulama tam zamanında bildirim göndermek için izin isterse onaylayın. Aksi takdirde, bildirimleriniz birkaç dakika gecikebilir.")
                    .setPositiveButton("Anladım") { _, _ -> /* Kapat */ }
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bildirim izni alındı.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bildirim izni olmadan alarm gelmeyebilir.", Toast.LENGTH_LONG).show()
            }
        }
    }
}