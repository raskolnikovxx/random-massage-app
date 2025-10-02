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
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.widget.LinearLayout // Yeni notlar için eklendi
import android.widget.ScrollView // Yeni notlar için eklendi
import java.text.SimpleDateFormat // Not tarihleri için
import java.util.Date // Not tarihleri için
import java.util.Locale // Not tarihleri için
import android.graphics.Typeface // Font stili için


class MainActivity : AppCompatActivity(), HistoryItemListener {
    private val TAG = "MainActivity"

    private val POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE = 100
    private val PREFS_NAME = "AppPrefs"
    private val PREF_ALARM_DIALOG_SHOWN = "alarm_dialog_shown"

    // Layout bileşenleri
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var ivFavoritesToggle: ImageView

    // Yardımcı sınıflar
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyStore: HistoryStore
    private lateinit var controlConfig: ControlConfig

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

    // onCommentClicked metodu artık List<Note> yapısını çağıracak
    override fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?) {
        val history = historyStore.getHistory().find { it.id == historyId }
        val existingNotes = history?.comments ?: emptyList()

        showNoteEntryDialog(historyId, originalMessage, existingNotes) // Yeni metot çağrıldı
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
        val intent = Intent(this, FullscreenMediaActivity::class.java).apply {
            putExtra(FullscreenMediaActivity.EXTRA_MEDIA_URL, imageUrl)
        }
        startActivity(intent)
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
        recyclerView = findViewById(R.id.recycler_view_history)
        tvEmpty = findViewById(R.id.tv_empty_history)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        ivFavoritesToggle = findViewById(R.id.iv_favorites_toggle)

        historyAdapter = HistoryAdapter(emptyList(), this)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        ivFavoritesToggle.setOnClickListener {
            isShowingFavorites = !isShowingFavorites
            updateFavoritesToggleUI()
            loadHistory()
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadHistory()
            fetchRemoteConfig()
        }

        updateFavoritesToggleUI()
        loadHistory()
    }

    private fun updateFavoritesToggleUI() {
        if (isShowingFavorites) {
            ivFavoritesToggle.setImageResource(R.drawable.ic_favorite_filled)
            ivFavoritesToggle.contentDescription = "Tüm Geçmişi Göster"
            Toast.makeText(this, "Favori Anılar Filtrelendi.", Toast.LENGTH_SHORT).show()
        } else {
            ivFavoritesToggle.setImageResource(R.drawable.ic_favorite_border)
            ivFavoritesToggle.contentDescription = "Favorileri Göster"
        }
    }


    private fun loadHistory() {
        swipeRefreshLayout.isRefreshing = true

        val allHistory = historyStore.getHistory()

        val displayList = if (isShowingFavorites) {
            allHistory.filter { it.isPinned }
        } else {
            allHistory
        }

        historyAdapter.updateList(displayList)

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

    // YENİ VE NİHAİ DİYALOG FONKSİYONU: Not Listesini Yönetir
    private fun showNoteEntryDialog(historyId: Long, originalMessage: String, existingNotes: List<Note>) {
        val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

        // Ana Konteyner
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // 1. MEVCUT NOTLARI GÖSTER (Sıralı)
        if (existingNotes.isNotEmpty()) {
            val historyTitle = TextView(this).apply {
                text = "Önceki Notlar (${existingNotes.size}):"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
            container.addView(historyTitle)

            // Tüm notları listeler (en yenisi üstte olmalı)
            existingNotes.forEach { note ->
                val noteView = TextView(this).apply {
                    text = "(${dateFormat.format(Date(note.timestamp))}) ${note.text}"
                    textSize = 14f
                    setPadding(5, 5, 5, 5)
                    setBackgroundColor(ContextCompat.getColor(context, R.color.purple_200))
                }
                container.addView(noteView)
            }

            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 20 }
                setBackgroundColor(ContextCompat.getColor(context, R.color.black))
            }
            container.addView(separator)
        }

        // 2. YENİ NOT GİRİŞ ALANI
        val input = EditText(this).apply {
            hint = "Eşinize yeni bir not ekleyin (Silinemez)..."
            setLines(3)
        }
        container.addView(input)

        // Ekranın kaydırılabilir olması için ScrollView'e ekle
        val dialogView = ScrollView(this).apply { addView(container) }

        AlertDialog.Builder(this)
            .setTitle("Not Ekle (Anı: \"$originalMessage\")")
            .setView(dialogView)
            .setPositiveButton("Kaydet ve Kapat") { _, _ ->
                val newComment = input.text.toString()
                if (newComment.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        // YENİ FONKSİYON KULLANILDI: Notu listeye ekle
                        historyStore.addNoteToHistoryItem(historyId, newComment)
                        withContext(Dispatchers.Main) {
                            loadHistory()
                            Toast.makeText(this@MainActivity, "Yeni not kaydedildi.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }


    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    POST_NOTIFICATIONS_PERMISSION_REQUEST_CODE
                )
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