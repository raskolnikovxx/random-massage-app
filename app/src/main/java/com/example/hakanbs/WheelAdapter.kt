package com.example.hakanbs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WheelAdapter(private val options: List<String>) : RecyclerView.Adapter<WheelAdapter.WheelViewHolder>() {

    class WheelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.tv_option_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WheelViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wheel_option, parent, false)
        return WheelViewHolder(view)
    }

    override fun onBindViewHolder(holder: WheelViewHolder, position: Int) {
        // Listeyi sonsuz bir döngü gibi göstermek için pozisyonu mod işlemine sokuyoruz.
        holder.textView.text = options[position % options.size]
    }

    // Animasyonun uzun süre dönebilmesi için çok büyük bir sayı döndürüyoruz.
    override fun getItemCount(): Int = Int.MAX_VALUE
}