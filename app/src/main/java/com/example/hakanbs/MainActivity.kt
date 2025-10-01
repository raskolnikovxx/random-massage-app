package com.example.hakanbs

import android.Manifest
import android.app.AlarmManager
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

    // HistoryItemListener'ın implementasyonu (Etkileşimleri yönetir)
    private val historyItemListener = object : HistoryItemListener {
        override fun onReactClicked(history: NotificationHistory, emoji: String) {
            historyStore.updateHistoryItem(history.id, newReaction = emoji)
            loadHistory()
            // Firestore'a gönderme işlemi burada tetiklenecektir
            Toast.makeText(this@MainActivity, "Tepki kaydedildi: ${emoji}", Toast.LENGTH_SHORT).show()
        }

        override fun onPinToggled(history: NotificationHistory, isPinned: Boolean) {
            historyStore.updateHistoryItem(history.id, newPinState = isPinned)
            loadHistory()
            // Firestore'a gönderme işlemi burada tetiklenecektir
            Toast.makeText(this@MainActivity, if (isPinned) "Anı sabitlendi." else "Sabitleme kaldırıldı.", Toast.LENGTH_SHORT).show()
        }

        override fun onImageClicked(imageUrl: String) {
            // Full screen görsel açma mantığı buraya gelir
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

        // RecyclerView Kurulumu (Listener bağlandı)
        historyAdapter = HistoryAdapter(emptyList(), historyItemListener)
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter

        // İzinleri iste
        requestNotificationPermission()
        checkAlarmPermission()

        // Planlama ve Senkronizasyonu başlat
        SyncRemoteWorker.schedule(this)
        startInitialSync()

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

    // Bildirim İzni İsteme (Basit ve izin isteyen tek fonksiyon)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Hassas Alarm İzni Kontrolü (Sadece bilgilendirme)
    private fun checkAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {

                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("BİLGİLENDİRME")
                    .setMessage("Uygulama tam zamanında bildirim göndermek için izin isterse onaylayın. Aksi takdirde, bildirimleriniz birkaç dakika gecikebilir.")
                    .setPositiveButton("Anladım") { _, _ -> /* Kapat */ }
                    .show()
            }
        }
    }
}