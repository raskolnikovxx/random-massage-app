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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    // YENİ VE DÜZELTİLMİŞ SABİTLER BURAYA EKLENDİ
    private val PREFS_NAME = "AppPrefs"
    private val PREF_ALARM_DIALOG_SHOWN = "alarm_dialog_shown"

    // Layout bileşenleri
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    // Yardımcı sınıflar
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyStore: HistoryStore
    private lateinit var controlConfig: ControlConfig

    // Android 13+ Bildirim İzni Launcher'ı
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "POST_NOTIFICATIONS granted.")
        } else {
            Toast.makeText(this, "Bildirim izni olmadan alarm gelmeyebilir.", Toast.LENGTH_SHORT).show()
        }
    }

    // HistoryItemListener'ın implementasyonu
    private val historyItemListener = object : HistoryItemListener {
        override fun onReactClicked(history: NotificationHistory, emoji: String) {
            historyStore.updateHistoryItem(history.id, newReaction = emoji)
            loadHistory()
            Toast.makeText(this@MainActivity, "Tepki kaydedildi: ${emoji}", Toast.LENGTH_SHORT).show()
        }

        override fun onPinToggled(history: NotificationHistory, isPinned: Boolean) {
            historyStore.updateHistoryItem(history.id, newPinState = isPinned)
            loadHistory()
            Toast.makeText(this@MainActivity, if (isPinned) "Anı sabitlendi." else "Sabitleme kaldırıldı.", Toast.LENGTH_SHORT).show()
        }

        override fun onImageClicked(imageUrl: String) {
            Toast.makeText(this@MainActivity, "Görsel Yolu: $imageUrl (Full Ekran Açılacak)", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Layout bileşenlerini bul
        rvHistory = findViewById(R.id.recycler_view_history)
        tvEmpty = findViewById(R.id.tv_empty_history)

        // Yardımcı Sınıfları başlat
        historyStore = HistoryStore(this)
        controlConfig = ControlConfig(this)

        // RecyclerView Kurulumu
        historyAdapter = HistoryAdapter(emptyList(), historyItemListener)
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        // İzinleri iste
        requestNotificationPermission()
        checkAlarmPermission()

        // Planlama ve Senkronizasyonu başlat
        SyncRemoteWorker.schedule(this)
        DailySchedulerWorker.enqueueWork(applicationContext) // 4 AM planlaması
        startInitialSync() // İlk veri çekme

        Log.d(TAG, "MainActivity initialized successfully.")
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun updateUiTexts(config: RemoteConfig) {
        supportActionBar?.title = config.activityTitle
        tvEmpty.text = config.emptyMessage
    }

    private fun loadHistory() {
        val allHistory = historyStore.getHistory()
        historyAdapter.updateList(allHistory)

        val config = controlConfig.getLocalConfig()
        updateUiTexts(config)

        tvEmpty.visibility = if (allHistory.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startInitialSync() {
        val oneTimeSync = androidx.work.OneTimeWorkRequestBuilder<SyncRemoteWorker>()
            .setInitialDelay(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueue(oneTimeSync)
    }

    // Bildirim İzni İsteme
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // HASSAS ALARM İZNİ KONTROLÜ (Dialogu sadece ilk kez gösterir)
    private fun checkAlarmPermission() {
        // SharedPreferences'ı doğru sabiti kullanarak al
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Android 12 ve üzeri için kontrol
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            // Eğer izin verilmediyse VE dialog daha önce gösterilmediyse
            if (!alarmManager.canScheduleExactAlarms() && !prefs.getBoolean(PREF_ALARM_DIALOG_SHOWN, false)) {

                // Dialog gösterildi bayrağını ayarla
                prefs.edit().putBoolean(PREF_ALARM_DIALOG_SHOWN, true).apply()

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("BİLGİLENDİRME")
                    .setMessage("Uygulama tam zamanında bildirim göndermek için izin isterse onaylayın. Aksi takdirde, bildirimleriniz birkaç dakika gecikebilir.")
                    .setPositiveButton("Anladım") { _, _ -> /* Kapat */ }
                    .show()
            }
        }
    }
}