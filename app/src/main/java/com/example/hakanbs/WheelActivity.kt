package com.example.hakanbs

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class WheelActivity : AppCompatActivity() {
    private lateinit var layoutResult: LinearLayout
    private lateinit var layoutDecide: View
    private lateinit var tvFinalResult: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var decideButton: Button
    private lateinit var resetButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var wheelHistoryAdapter: WheelHistoryAdapter
    private lateinit var historyStore: HistoryStore

    private var options: List<String> = emptyList()
    private val cardViews = mutableListOf<CardView>()
    private var isAnimating = false

    private val PREFS_NAME = "DecisionWheelPrefs"
    private val PREF_DECISION_DATE = "decision_date"
    private val PREF_DECISION_RESULT = "decision_result"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private const val ANIMATION_DURATION = 3000L
        private const val HIGHLIGHT_DURATION = 300L
        private const val MAX_HISTORY = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wheel)

        setupViews()
        setupRecyclerView()
        loadConfig()
        checkForSavedDecision()
    }

    private fun setupViews() {
        layoutResult = findViewById(R.id.layout_result)
        layoutDecide = findViewById(R.id.layout_decide)
        tvFinalResult = findViewById(R.id.tv_final_result)
        optionsContainer = findViewById(R.id.options_container)
        decideButton = findViewById(R.id.btn_decide)
        resetButton = findViewById(R.id.btn_reset)
        recyclerView = findViewById(R.id.recycler_view_history)
        historyStore = HistoryStore(this)
    }

    private fun setupRecyclerView() {
        wheelHistoryAdapter = WheelHistoryAdapter()
        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@WheelActivity)
            adapter = wheelHistoryAdapter
        }
        loadHistoricalDecisions()
    }

    private fun loadConfig() {
        val config = ControlConfig(this).getLocalConfig()
        options = config.decisionWheel.options
        supportActionBar?.title = config.decisionWheel.title
    }

    private fun loadHistoricalDecisions() {
        val decisions = historyStore.getHistory()
            .filter { it.type == "wheel_decision" }
            .take(MAX_HISTORY)
            .sortedByDescending { it.time }
        wheelHistoryAdapter.submitList(decisions)
    }

    private fun checkForSavedDecision() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(PREF_DECISION_DATE, null)
        val todayDate = dateFormat.format(Date())
        if (savedDate == todayDate) {
            val result = prefs.getString(PREF_DECISION_RESULT, "Hata!")
            showResultState(result)
        } else {
            showInitialState()
        }
    }

    private fun showInitialState() {
        layoutResult.visibility = View.GONE
        layoutDecide.visibility = View.VISIBLE
        if (options.isEmpty()) {
            Toast.makeText(this, "Seçenek bulunamadı!", Toast.LENGTH_SHORT).show()
            decideButton.isEnabled = false
            return
        }

        options.forEach { optionText ->
            val card = LayoutInflater.from(this).inflate(R.layout.item_wheel_option, optionsContainer, false) as CardView
            val textView = card.findViewById<TextView>(R.id.tv_option_text)
            textView.text = optionText
            optionsContainer.addView(card)
            cardViews.add(card)
        }

        decideButton.setOnClickListener {
            if (!isAnimating) startWheelAnimation()
        }
    }

    private fun startWheelAnimation() {
        if (isAnimating) return
        isAnimating = true
        decideButton.isEnabled = false

        val finalChoice = options.random()
        val finalIndex = options.indexOf(finalChoice)

        lifecycleScope.launch {
            // Hızlı karıştırma animasyonu
            repeat(15) { iteration ->
                cardViews.forEach { it.setCardBackgroundColor(Color.WHITE) }
                val randomIndex = Random.nextInt(cardViews.size)
                animateCard(cardViews[randomIndex])
                delay(100L + (iteration * 20))
            }

            // Final seçim animasyonu
            cardViews[finalIndex].let { targetCard ->
                val colorAnim = ValueAnimator.ofObject(
                    ArgbEvaluator(),
                    Color.WHITE,
                    ContextCompat.getColor(this@WheelActivity, R.color.purple_500)
                )
                colorAnim.duration = HIGHLIGHT_DURATION
                colorAnim.addUpdateListener { animator ->
                    targetCard.setCardBackgroundColor(animator.animatedValue as Int)
                }
                colorAnim.start()
            }

            delay(HIGHLIGHT_DURATION)
            saveAndShowResult(finalChoice)
            isAnimating = false
        }
    }

    private fun animateCard(card: CardView) {
        card.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .withEndAction {
                card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()

        val colorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.WHITE,
            ContextCompat.getColor(this, R.color.purple_200),
            Color.WHITE
        )
        colorAnim.duration = 300
        colorAnim.addUpdateListener { animator ->
            card.setCardBackgroundColor(animator.animatedValue as Int)
        }
        colorAnim.start()
    }

    private fun saveAndShowResult(result: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = dateFormat.format(Date())
        prefs.edit().apply {
            putString(PREF_DECISION_DATE, today)
            putString(PREF_DECISION_RESULT, result)
            apply()
        }

        // NotificationHistory nesnesini oluştur ve kaydet
        val currentTime = System.currentTimeMillis()
        val decision = NotificationHistory(
            id = currentTime,
            time = currentTime,
            message = result,
            type = "wheel_decision"
        )

        historyStore.addNotificationToHistory(decision)
        loadHistoricalDecisions()
        showResultState(result)
    }

    private fun showResultState(result: String?) {
        layoutDecide.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvFinalResult.text = result

        resetButton.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(PREF_DECISION_DATE).remove(PREF_DECISION_RESULT).apply()
            recreate()
        }
    }
}

class WheelHistoryAdapter : RecyclerView.Adapter<WheelHistoryAdapter.ViewHolder>() {
    private var decisions = listOf<NotificationHistory>()

    fun submitList(newList: List<NotificationHistory>) {
        decisions = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val decision = decisions[position]
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.text1.text = decision.message
        holder.text2.text = dateFormat.format(Date(decision.time))
    }

    override fun getItemCount() = decisions.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }
}
