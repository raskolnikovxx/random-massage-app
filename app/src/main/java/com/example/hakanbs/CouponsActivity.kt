package com.example.hakanbs

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CouponsActivity : AppCompatActivity() {

    private val PREFS_NAME = "CouponPrefs"
    private val PREF_USED_COUPONS = "used_coupon_ids"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coupons)

        // Varsayılan ActionBar'ı gizle
        supportActionBar?.hide()

        val config = ControlConfig(this).getLocalConfig()
        val allCoupons = config.coupons // Tüm kuponları al
        val usedCouponIds = getUsedCouponIds()

        // --- YENİ SIRALAMA MANTIĞI BURADA ---
        // Kupon listesini, ID'sinin "kullanılmış" listesinde olup olmamasına göre sırala.
        val sortedCoupons = allCoupons.sortedBy { coupon ->
            usedCouponIds.contains(coupon.id)
        }
        // ------------------------------------

        val recyclerView: RecyclerView = findViewById(R.id.recycler_view_coupons)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Adaptöre artık orijinal listeyi değil, sıralanmış listeyi gönderiyoruz.
        recyclerView.adapter = CouponsAdapter(sortedCoupons, usedCouponIds) { coupon ->
            // Bir kupona tıklandığında...
            showUseCouponDialog(coupon)
        }
    }

    private fun showUseCouponDialog(coupon: Coupon) {
        AlertDialog.Builder(this)
            .setTitle("Kuponu Kullan")
            .setMessage("\"${coupon.title}\" kuponunu kullanmak istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Evet, Kullan") { _, _ ->
                useCoupon(coupon)
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun useCoupon(coupon: Coupon) {
        // Kupon ID'sini kullanılmış olarak kaydet
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentIds = prefs.getStringSet(PREF_USED_COUPONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentIds.add(coupon.id)
        prefs.edit().putStringSet(PREF_USED_COUPONS, currentIds).apply()

        // Bu olayı anı olarak kaydet
        val historyStore = HistoryStore(this)
        val history = NotificationHistory(
            time = System.currentTimeMillis(),
            messageId = coupon.id,
            message = "Kupon kullanıldı: ${coupon.title}",
            context = "Ortak Aktivite"
        )
        historyStore.addNotificationToHistory(history)

        // Ekranı yeniden oluşturarak listeyi güncelle
        recreate()
    }

    private fun getUsedCouponIds(): Set<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREF_USED_COUPONS, emptySet()) ?: emptySet()
    }
}