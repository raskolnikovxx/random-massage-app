package com.example.hakanbs

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private lateinit var searchView: SearchView

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

    override fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?) {
        val history = historyStore.getHistory().find { it.id == historyId }
        val existingNotes = history?.comments ?: emptyList()
        showNoteEntryDialog(historyId, originalMessage, existingNotes)
    }

    override fun onReactClicked(history: NotificationHistory) {
        showEmojiEntryDialog(history)
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
        searchView = findViewById(R.id.search_view)

        val wheelIcon: ImageView = findViewById(R.id.iv_wheel)
        wheelIcon.setOnClickListener {
            startActivity(Intent(this, WheelActivity::class.java))
        }

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

        setupSearchListener()

        updateFavoritesToggleUI()
        loadHistory()
    }

    private fun setupSearchListener() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                historyAdapter.filter(newText)
                return true
            }
        })
    }

    private fun updateFavoritesToggleUI() {
        if (isShowingFavorites) {
            ivFavoritesToggle.setImageResource(R.drawable.ic_favorite_filled)
            ivFavoritesToggle.contentDescription = "Tüm Geçmişi Göster"
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

        val currentQuery = searchView.query.toString()
        if (currentQuery.isNotEmpty()) {
            historyAdapter.filter(currentQuery)
        }
        val config = controlConfig.getLocalConfig()
        updateUiTexts(config)
        swipeRefreshLayout.isRefreshing = false
        tvEmpty.visibility = if (historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
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

    private fun showEmojiEntryDialog(history: NotificationHistory) {
        val input = EditText(this).apply {
            hint = "Bir emoji seçin..."
            setText(history.reaction ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Tepkini Seç")
            .setMessage("Klavyenizdeki emoji butonunu kullanarak bir tepki seçebilirsiniz.")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                val newEmoji = input.text.toString().trim()
                if (newEmoji.isNotBlank()) {
                    val finalReaction = if (newEmoji == history.reaction) null else newEmoji
                    lifecycleScope.launch(Dispatchers.IO) {
                        historyStore.updateHistoryItem(history.id, newReaction = finalReaction)
                        withContext(Dispatchers.Main) {
                            loadHistory()
                        }
                    }
                }
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showNoteEntryDialog(historyId: Long, originalMessage: String, existingNotes: List<Note>) {
        val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        if (existingNotes.isNotEmpty()) {
            val historyTitle = TextView(this).apply {
                text = "Önceki Notlar (${existingNotes.size}):"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
            container.addView(historyTitle)
            existingNotes.forEach { note ->
                val noteView = TextView(this).apply {
                    text = "(${dateFormat.format(Date(note.timestamp))}) ${note.text}"
                    textSize = 14f
                    setPadding(5, 5, 5, 5)
                }
                container.addView(noteView)
            }
            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 20 }
                setBackgroundColor(ContextCompat.getColor(context, R.color.black))
            }
            container.addView(separator)
        }
        val input = EditText(this).apply {
            hint = "Eşinize yeni bir not ekleyin (Silinemez)..."
            setLines(3)
        }
        container.addView(input)
        val dialogView = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("Not Ekle (Anı: \"$originalMessage\")")
            .setView(dialogView)
            .setPositiveButton("Kaydet ve Kapat") { _, _ ->
                val newComment = input.text.toString()
                if (newComment.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
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
                    .setPositiveButton("Anladım") { _, _ -> }
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