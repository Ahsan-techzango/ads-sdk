package com.example.sampleads

import android.app.Application
import android.util.Log
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.AnalyticsListener
import com.xai.adslibrary.core.ProgressProvider

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Native layouts are now INSIDE the library – no NativeLayoutProvider required.
        // Pass one only if you want fully custom host layouts.
        AdsLibrary.initialize(
            context = this,
            isPremium = false,
            config = AdsConfig(
                adsEnabled = true,
                analyticsListener = AnalyticsListener { tag, message ->
                    Log.d(tag, message)
                },
                progressProvider = object : ProgressProvider {
                    override fun show(activity: android.app.Activity, message: String?) { }
                    override fun dismiss() { }
                }
                // nativeLayoutProvider omitted → uses library built-in layouts
            )
        )
    }
}
