package com.example.hakanbs

import android.app.Application
import android.os.Build.VERSION.SDK_INT
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

class MyApplication : Application(), ImageLoaderFactory {
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