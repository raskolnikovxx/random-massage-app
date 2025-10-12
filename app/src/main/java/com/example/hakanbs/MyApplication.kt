package com.example.hakanbs

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MyApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Firebase başlat
        FirebaseApp.initializeApp(this)
        // Kullanıcıyı anonim olarak oturum açtır (güvenlik kuralları için auth şartı)
        Firebase.auth.signInAnonymously()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .components {
                // GIF, WebP ve APNG animasyonları için bu motoru ekliyoruz
                if (SDK_INT >= 28) {
                    // Yeni Android versiyonları için
                    add(ImageDecoderDecoder.Factory())
                } else {
                    // Eski Android versiyonları için
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}