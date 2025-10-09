package com.example.hakanbs

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class WheelActivity : AppCompatActivity() {

    // Arayüz Bileşenleri
    private lateinit var layoutResult: LinearLayout
    private lateinit var layoutDecide: View
    private lateinit var tvFinalResult: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var decideButton: Button
    private lateinit var resetButton: Button

    private lateinit var historyStore: HistoryStore
    private var options: List<String> = emptyList()
    private val cardViews = mutableListOf<CardView>()

    private val PREFS_NAME = "DecisionWheelPrefs"
    private val PREF_DECISION_DATE = "decision_date"
    private val PREF_DECISION_RESULT = "decision_result"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    companion object {
        private const val FAST_DURATION_MS = 5000L
        private const val TOTAL_DURATION_MS = 10000L
        private const val FAST_DELAY_MS = 50L
        private const val SLOW_START_DELAY_MS = 150L
        private const val SLOW_END_DELAY_MS = 600L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wheel)

        layoutResult = findViewById(R.id.layout_result)
        layoutDecide = findViewById(R.id.layout_decide)
        tvFinalResult = findViewById(R.id.tv_final_result)
        optionsContainer = findViewById(R.id.options_container)
        decideButton = findViewById(R.id.btn_decide)
        resetButton = findViewById(R.id.btn_reset)
        historyStore = HistoryStore(this)

        val config = ControlConfig(this).getLocalConfig()
        options = config.decisionWheel.options
        supportActionBar?.title = config.decisionWheel.title

        checkForSavedDecision()
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
            startAnimation()
        }
    }

    private fun showResultState(result: String?) {
        layoutDecide.visibility = View.GONE
        layoutResult.visibility = View.VISIBLE
        tvFinalResult.text = result
        resetButton.setOnClickListener {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            recreate()
        }
    }

    private fun startAnimation() {
        if (options.isEmpty()) return
        decideButton.isEnabled = false
        decideButton.text = "Karar veriliyor..."
        lifecycleScope.launch {
            val winningIndex = Random.nextInt(options.size)
            val startTime = System.currentTimeMillis()
            var lastHighlightIndex = -1
            while (System.currentTimeMillis() - startTime < TOTAL_DURATION_MS) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (lastHighlightIndex != -1) {
                    cardViews[lastHighlightIndex].setCardBackgroundColor(Color.WHITE)
                }
                var newHighlightIndex = lastHighlightIndex
                while (newHighlightIndex == lastHighlightIndex) {
                    newHighlightIndex = options.indices.random()
                }
                lastHighlightIndex = newHighlightIndex
                cardViews[newHighlightIndex].setCardBackgroundColor(Color.LTGRAY)
                val delayTime = if (elapsedTime < FAST_DURATION_MS) {
                    FAST_DELAY_MS
                } else {
                    val progress = (elapsedTime - FAST_DURATION_MS).toFloat() / (TOTAL_DURATION_MS - FAST_DURATION_MS)
                    (SLOW_START_DELAY_MS + progress * (SLOW_END_DELAY_MS - SLOW_START_DELAY_MS)).toLong()
                }
                delay(delayTime)
            }
            if (lastHighlightIndex != -1) {
                cardViews[lastHighlightIndex].setCardBackgroundColor(Color.WHITE)
            }
            cardViews[winningIndex].setCardBackgroundColor(getColor(R.color.purple_200))
            val finalResult = options[winningIndex]
            saveDecision(finalResult)
            saveResultAsHistory(finalResult)
            delay(1000)
            showResultState(finalResult)
            decideButton.postDelayed({ finish() }, 2000)
        }
    }

    private fun saveDecision(result: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_DECISION_DATE, dateFormat.format(Date()))
            .putString(PREF_DECISION_RESULT, result)
            .apply()
    }

    private fun saveResultAsHistory(result: String) {
        val history = NotificationHistory(
            time = System.currentTimeMillis(),
            messageId = "decision_wheel_${System.currentTimeMillis()}",
            message = "Karar Tekerleği'nden çıkan sonuç: \"$result\"",
            context = "Ortak Karar",
            isQuote = false
        )
        historyStore.addNotificationToHistory(history)
    }
}