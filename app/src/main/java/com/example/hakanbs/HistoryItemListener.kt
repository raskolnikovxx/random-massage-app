package com.example.hakanbs

// Activity ve Adapter arasında iletişimi sağlayan arayüz (Interface)
interface HistoryItemListener {
    fun onReactClicked(history: NotificationHistory, emoji: String)
    fun onPinToggled(history: NotificationHistory, isPinned: Boolean)
    fun onImageClicked(imageUrl: String)
}