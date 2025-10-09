package com.example.hakanbs

interface HistoryItemListener {
    fun onReactClicked(history: NotificationHistory) // emoji parametresini kaldırdık
    fun onFavoriteToggled(history: NotificationHistory, isFavorite: Boolean)
    fun onImageClicked(imageUrl: String)
    fun onCommentClicked(historyId: Long, originalMessage: String, currentComment: String?)
}