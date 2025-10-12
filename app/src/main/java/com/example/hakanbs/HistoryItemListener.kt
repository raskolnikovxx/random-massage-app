package com.example.hakanbs

interface HistoryItemListener {
    fun onReactClicked(history: NotificationHistory)
    fun onFavoriteToggled(history: NotificationHistory, isFavorite: Boolean)
    fun onImageClicked(imageUrl: String)
    fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?)
    fun onVideoClicked(videoUrl: String) // YENİ METOT
    fun onItemClicked(historyId: Long, message: String)
}