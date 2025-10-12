package com.example.hakanbs

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_MESSAGE = "extra_message"
    }

    private lateinit var tvMessage: TextView
    private lateinit var tvMeta: TextView
    private lateinit var rvComments: RecyclerView
    private lateinit var etNewComment: EditText
    private lateinit var btnAddComment: Button

    private lateinit var historyStore: HistoryStore
    private lateinit var commentAdapter: CommentAdapter
    private var historyId: Long = -1L

    private val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)

        tvMessage = findViewById(R.id.tv_history_message)
        tvMeta = findViewById(R.id.tv_history_meta)
        rvComments = findViewById(R.id.rv_comments)
        etNewComment = findViewById(R.id.et_new_comment)
        btnAddComment = findViewById(R.id.btn_add_comment)

        historyStore = HistoryStore(this)

        commentAdapter = CommentAdapter()
        rvComments.apply {
            layoutManager = LinearLayoutManager(this@HistoryDetailActivity)
            adapter = commentAdapter
        }

        historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""

        tvMessage.text = message
        tvMeta.text = ""

        loadComments()

        btnAddComment.setOnClickListener {
            val text = etNewComment.text.toString().trim()
            if (text.isNotEmpty() && historyId != -1L) {
                // Add comment in background
                CoroutineScope(Dispatchers.IO).launch {
                    historyStore.addNoteToHistoryItem(historyId, text)
                    withContext(Dispatchers.Main) {
                        etNewComment.setText("")
                        loadComments()
                    }
                }
            }
        }
    }

    private fun loadComments() {
        val history = historyStore.getHistory().find { it.id == historyId }
        val comments = history?.comments ?: emptyList()
        // Sort by timestamp descending (newest first)
        val sorted = comments.sortedByDescending { it.timestamp }
        commentAdapter.submitList(sorted)
    }
}

