package com.example.hakanbs

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
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

    // Layout components
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tvFavoritesToggle: TextView
    private lateinit var searchView: SearchView

    // Helper classes
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var historyStore: HistoryStore
    private lateinit var controlConfig: ControlConfig

    private var isShowingFavorites = false

    // Pagination fields
    private val PAGE_SIZE = 20
    private var currentPage = 0
    private var allHistoryCache: List<NotificationHistory> = emptyList()
    private var isLoadingMore = false
    private var isLastPage = false

    // This launcher handles the result from CouponsActivity
    private val couponsActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Returned from CouponsActivity with result OK. Refreshing list.")
            loadHistory()
        }
    }

    // --- HistoryItemListener Implementations ---

    override fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?) {
        // Open the detail screen for this history item where the user can see and add comments
        val intent = Intent(this, HistoryDetailActivity::class.java).apply {
            putExtra(HistoryDetailActivity.EXTRA_HISTORY_ID, historyId)
            putExtra(HistoryDetailActivity.EXTRA_MESSAGE, originalMessage)
        }
        startActivity(intent)
    }

    override fun onItemClicked(historyId: Long, message: String) {
        // When the whole item/card is tapped, navigate to the same detail screen
        val intent = Intent(this, HistoryDetailActivity::class.java).apply {
            putExtra(HistoryDetailActivity.EXTRA_HISTORY_ID, historyId)
            putExtra(HistoryDetailActivity.EXTRA_MESSAGE, message)
        }
        startActivity(intent)
    }

    override fun onReactClicked(history: NotificationHistory) {
        showEmojiEntryDialog(history)
    }

    override fun onVideoClicked(videoUrl: String) {
        val intent = Intent(this, VideoPlayerActivity::class.java).apply {
            putExtra("VIDEO_URL", videoUrl)
        }
        startActivity(intent)
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
    // --- End of Listener Methods ---

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

        // Immediately try to fetch remote config once on startup and apply schedules
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val cfg = controlConfig.fetchConfig() ?: controlConfig.getLocalConfig()
                try {
                    // Run scheduler on IO (not on Main) to avoid blocking UI thread
                    Planner(this@MainActivity, cfg).scheduleAllNotifications(true)
                    Log.d("MainActivity", "Initial Planner.scheduleAllNotifications executed on startup (background thread).")
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to run Planner on startup: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Startup config fetch failed: ${e.message}")
            }
        }

        // DEBUG: Hemen planlamayı tetkileyerek yeni Planner davranışını test et (sadece debug build)
        if (isDebugBuild()) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val cfg = controlConfig.getLocalConfig()
                    Planner(this@MainActivity, cfg).scheduleAllNotifications(false)
                    Log.d(TAG, "DEBUG: Planner.scheduleAllNotifications() triggered for testing (background thread).")
                } catch (e: Exception) {
                    Log.w(TAG, "DEBUG: Failed to trigger planner for testing: ${e.message}")
                }
            }
        }

        Log.d(TAG, "MainActivity initialized successfully.")

        // SearchView hint ve metin rengini dark modda light_gray_near_white yap
        val isNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            if (searchEditText != null) {
                val lightGray = ContextCompat.getColor(this, R.color.light_gray_near_white)
                searchEditText.setTextColor(lightGray)
                searchEditText.setHintTextColor(lightGray)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::historyAdapter.isInitialized) {
            historyAdapter.releasePlayer()
        }
    }

    // --- Helper Functions ---

    private fun isDebugBuild(): Boolean {
        return (applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }


    private fun setupUI() {
        recyclerView = findViewById(R.id.recycler_view_history)
        tvEmpty = findViewById(R.id.tv_empty_history)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        searchView = findViewById(R.id.search_view)
        tvFavoritesToggle = findViewById(R.id.tv_favorites_toggle)
        val tvCoupons: TextView = findViewById(R.id.tv_coupons)

        val homepageIcon: ImageView = findViewById(R.id.tv_homepage)
        homepageIcon.setOnClickListener {
            isShowingFavorites = false
            updateFavoritesToggleUI()
            searchView.setQuery("", false)
            searchView.clearFocus()
            loadHistory()
            Toast.makeText(this, "Filters cleared.", Toast.LENGTH_SHORT).show()
        }

        val snakeGameIcon: ImageView = findViewById(R.id.tv_snake_game)
        snakeGameIcon.setOnClickListener {
            startActivity(Intent(this, SnakeGameActivity::class.java))
        }


        val wheelIcon: ImageView = findViewById(R.id.iv_wheel)
        wheelIcon.setOnClickListener {
            startActivity(Intent(this, WheelActivity::class.java))
        }

        tvCoupons.setOnClickListener {
            val intent = Intent(this, CouponsActivity::class.java)
            couponsActivityLauncher.launch(intent)
        }


        historyAdapter = HistoryAdapter(this)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = historyAdapter
        }

        // Endless scroll - yükle daha fazla
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoadingMore && !isLastPage) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 3) {
                        loadMore()
                    }
                }
            }
        })

        tvFavoritesToggle.setOnClickListener {
            isShowingFavorites = !isShowingFavorites
            updateFavoritesToggleUI()
            updateMenuTextColors()
            loadHistory()
        }

        swipeRefreshLayout.setOnRefreshListener {
            // Yenileme her zaman sayfalamayı resetlesin
            fetchRemoteConfigAsync { config ->
                 // schedule planner on background to avoid blocking UI
                 lifecycleScope.launch(Dispatchers.IO) {
                     try {
                         val cfg = controlConfig.getLocalConfig()
                         Planner(this@MainActivity, cfg).scheduleAllNotifications(true)
                     } catch (e: Exception) {
                         Log.w(TAG, "Failed to schedule planner during refresh: ${e.message}")
                     }
                     withContext(Dispatchers.Main) {
                         loadHistory(reset = true)
                         swipeRefreshLayout.isRefreshing = false
                     }
                 }
             }
        }

        setupSearchListener()
        updateFavoritesToggleUI()
        updateMenuTextColors()
        loadHistory()

        // Debug-only quick reset button: yükle ve planla
        if (isDebugBuild()) {
            val root = findViewById<View>(android.R.id.content) as FrameLayout
            val fab = FloatingActionButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_revert)
                size = FloatingActionButton.SIZE_MINI
                setOnClickListener {
                    // Reset history from assets and re-run planner
                    val hs = HistoryStore(this@MainActivity)
                    val ok = hs.resetHistoryToDefault()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val cfg = controlConfig.getLocalConfig()
                        Planner(this@MainActivity, cfg).scheduleAllNotifications(true)
                        withContext(Dispatchers.Main) {
                            loadHistory(reset = true)
                            Toast.makeText(this@MainActivity, if (ok) "Default loaded & schedules updated" else "Default load failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.END or Gravity.BOTTOM
                rightMargin = 24
                bottomMargin = 24
            }
            root.addView(fab, params)
        }
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
        // Bu fonksiyonun içeriğini sadeleştiriyoruz, renk atamasını updateMenuTextColors yönetecek
    }

    private fun updateMenuTextColors() {
        val isNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val colorRes = if (isNightMode) R.color.light_gray_near_white else R.color.text_primary
        tvFavoritesToggle.setTextColor(ContextCompat.getColor(this, colorRes))
        val tvCoupons: TextView = findViewById(R.id.tv_coupons)
        tvCoupons.setTextColor(ContextCompat.getColor(this, colorRes))
    }

    // loadHistory artık sayfalamayı destekliyor. reset=true ise baştan yükler.
    private fun loadHistory(reset: Boolean = true) {
        swipeRefreshLayout.isRefreshing = true

        if (reset) {
            currentPage = 0
            isLastPage = false
            allHistoryCache = historyStore.getHistory()
        } else {
            if (allHistoryCache.isEmpty()) allHistoryCache = historyStore.getHistory()
        }

        val sourceList = if (isShowingFavorites) {
            allHistoryCache.filter { it.isPinned }
        } else {
            allHistoryCache
        }

        val endIndex = ((currentPage + 1) * PAGE_SIZE).coerceAtMost(sourceList.size)
        val pageItems = if (endIndex > 0) sourceList.subList(0, endIndex) else emptyList()

        historyAdapter.updateOriginalList(pageItems)

        val currentQuery = searchView.query.toString()
        if (currentQuery.isNotEmpty()) {
            historyAdapter.filter(currentQuery)
        }
        val config = controlConfig.getLocalConfig()
        updateUiTexts(config)
        swipeRefreshLayout.isRefreshing = false

        isLastPage = endIndex >= sourceList.size
        tvEmpty.visibility = if (historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun loadMore() {
        if (isLastPage) return
        isLoadingMore = true
        currentPage++
        loadHistory(reset = false)
        isLoadingMore = false
    }

    private fun fetchRemoteConfigAsync(onComplete: ((RemoteConfig) -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            controlConfig.fetchConfig()
            val config = controlConfig.getLocalConfig()
            Planner(this@MainActivity, config).scheduleAllNotifications(false)
            withContext(Dispatchers.Main) {
                loadHistory()
                onComplete?.invoke(config)
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
            hint = ""
            setText(history.reaction ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Emoji seçin")
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
            .create()
        dialog.setOnShowListener {
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    private fun showNoteEntryDialog(historyId: Long, originalMessage: String, existingNotes: List<Note>) {
        val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        if (existingNotes.isNotEmpty()) {
            val historyTitle = TextView(this).apply {
                text = "Previous Notes (${existingNotes.size}):"
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
                setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.black))
            }
            container.addView(separator)
        }
        val input = EditText(this).apply {
            hint = "Add a new note to your partner (cannot be deleted)..."
            setLines(3)
        }
        container.addView(input)
        val dialogView = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("Add Note (Memory: \"$originalMessage\")")
            .setView(dialogView)
            .setPositiveButton("Save & Close") { _, _ ->
                val newComment = input.text.toString()
                if (newComment.isNotBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        historyStore.addNoteToHistoryItem(historyId, newComment)
                        withContext(Dispatchers.Main) {
                            loadHistory()
                            Toast.makeText(this@MainActivity, "New note saved.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
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
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms() && !prefs.getBoolean(PREF_ALARM_DIALOG_SHOWN, false)) {
                prefs.edit().putBoolean(PREF_ALARM_DIALOG_SHOWN, true).apply()
                AlertDialog.Builder(this)
                    .setTitle("INFORMATION")
                    .setMessage("Uygulama kesin zamanlı alarmlar kullanmak istiyor. Lütfen izin verin; aksi halde bildirimler gecikebilir. Exact alarm iznini vermek için Ayarlar ekranına yönlendirileceksiniz.")
                    .setPositiveButton("Enable") { _, _ ->
                        try {
                            // Açıkça kullanıcının exact alarm yetkisini istemek için Ayarlar sayfasını aç
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            startActivity(intent)
                        } catch (e: Exception) {
                            // Fallback: genel ayarlar sayfasını aç
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.parse("package:" + packageName)
                            }
                            startActivity(intent)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
}
