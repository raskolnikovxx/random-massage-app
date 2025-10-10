package com.example.hakanbs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class CouponsAdapter(
    private val coupons: List<Coupon>,
    private val usedCouponIds: Set<String>,
    private val onCouponClicked: (Coupon) -> Unit
) : RecyclerView.Adapter<CouponsAdapter.CouponViewHolder>() {

    class CouponViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_coupon_title)
        val description: TextView = view.findViewById(R.id.tv_coupon_description)
        val card: CardView = view.findViewById(R.id.card_view_coupon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CouponViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_coupon, parent, false)
        return CouponViewHolder(view)
    }

    override fun onBindViewHolder(holder: CouponViewHolder, position: Int) {
        val coupon = coupons[position]
        holder.title.text = coupon.title
        holder.description.text = coupon.description

        if (usedCouponIds.contains(coupon.id)) {
            // Kullanılmış kuponu pasif ve gri göster
            holder.card.alpha = 0.5f
            holder.itemView.isEnabled = false
        } else {
            // Aktif kupon
            holder.card.alpha = 1.0f
            holder.itemView.isEnabled = true
            holder.itemView.setOnClickListener { onCouponClicked(coupon) }
        }
    }

    override fun getItemCount() = coupons.size
}