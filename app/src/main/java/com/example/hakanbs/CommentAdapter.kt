package com.example.hakanbs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter : ListAdapter<Note, CommentAdapter.ViewHolder>(CommentDiff()) {

    private val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvText: TextView = itemView.findViewById(R.id.tv_comment_text)
        private val tvTime: TextView = itemView.findViewById(R.id.tv_comment_time)

        fun bind(note: Note) {
            tvText.text = note.text
            tvTime.text = dateFormat.format(Date(note.timestamp))
        }
    }

    class CommentDiff : DiffUtil.ItemCallback<Note>() {
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.text == newItem.text
        }

        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean {
            return oldItem == newItem
        }
    }
}

