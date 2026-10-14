package com.xai.adslibrary.appopen

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.xai.adslibrary.callback.AppOpenCallback
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object AppOpenAdManager {

    private const val TAG = "AppOpenAdManager"

    private val startOpenAd = AtomicReference<AppOpenAd?>(null)
    private var adLoadingTime: Long = 0
    private val isLoading = AtomicBoolean(false)
    private val isDisplaying = AtomicBoolean(false)
    private val isActivityPaused = AtomicBoolean(false)

    @Volatile
    var canShowAppOpen: Boolean = true

    private var pendingCallback: AppOpenCallback? = null

    private fun isAdAvailable(config: AdsConfig): Boolean {
        val ad = startOpenAd.get() ?: return false
        val diff = Date().time - adLoadingTime
        return diff <= config.appOpenExpiryHours * 3600 * 1000
    }

    fun onPause() {
        isActivityPaused.set(true)
        Log.d(TAG, "Activity paused – deferring show")
    }

    fun onResume(activity: Activity) {
        isActivityPaused.set(false)
        if (pendingCallback != null && isAdAvailable(AdsLibrary.getConfig())) {
            Log.d(TAG, "Resuming pending AppOpenAd show")
            show(activity, AdsLibrary.getConfig(), pendingCallback)
            pendingCallback = null
        }
    }

    fun load(
        activity: Activity,
        adUnitId: String,
        withCallback: Boolean,
        config: AdsConfig,
        callback: ((Boolean, Boolean) -> Unit)?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity) || !config.adsEnabled) {
            if (withCallback) {
                MainThreadDispatcher.dispatch { callback?.invoke(false, true) }
            }
            return
        }

        if (isAdAvailable(config)) {
            if (withCallback) {
                MainThreadDispatcher.dispatch { callback?.invoke(true, false) }
            }
            return
        }

        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Already loading AppOpen")
            return
        }

        config.analyticsListener?.logEvent(TAG, "Loading AppOpen ($adUnitId)")

        AppOpenAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    startOpenAd.set(ad)
                    adLoadingTime = Date().time
                    isLoading.set(false)
                    Log.d(TAG, "AppOpen loaded")
                    if (withCallback) {
                        MainThreadDispatcher.dispatch { callback?.invoke(true, false) }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    startOpenAd.set(null)
                    isLoading.set(false)
                    Log.d(TAG, "AppOpen failed: $error")
                    if (withCallback) {
                        MainThreadDispatcher.dispatch { callback?.invoke(false, true) }
                    }
                }
            }
        )
    }

    fun show(
        activity: Activity,
        config: AdsConfig,
        callback: AppOpenCallback?
    ) {
        if (isActivityPaused.get()) {
            Log.i(TAG, "Activity paused – deferring")
            pendingCallback = callback
            return
        }
        if (isDisplaying.get()) {
            MainThreadDispatcher.dispatch { callback?.onResult(false, false, true) }
            return
        }
        if (!isAdAvailable(config) || !canShowAppOpen || PremiumManager.isPremium()) {
            MainThreadDispatcher.dispatch { callback?.onResult(false, false, true) }
            return
        }

        val ad = startOpenAd.get() ?: run {
            MainThreadDispatcher.dispatch { callback?.onResult(false, false, true) }
            return
        }

        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                startOpenAd.set(null)
                isDisplaying.set(false)
                MainThreadDispatcher.dispatch { callback?.onResult(false, true, false) }
                Log.d(TAG, "AppOpen dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                isDisplaying.set(false)
                startOpenAd.set(null)
                MainThreadDispatcher.dispatch { callback?.onResult(false, false, true) }
                Log.d(TAG, "AppOpen failed to show: $error")
            }

            override fun onAdShowedFullScreenContent() {
                isDisplaying.set(true)
                startOpenAd.set(null)
                MainThreadDispatcher.dispatch { callback?.onResult(true, false, false) }
                Log.d(TAG, "AppOpen showed")
            }
        }

        try {
            ad.show(activity)
        } catch (t: Throwable) {
            Log.e(TAG, "Show threw: ${t.message}")
            isDisplaying.set(false)
            startOpenAd.set(null)
            MainThreadDispatcher.dispatch { callback?.onResult(false, false, true) }
        }
    }

    fun clear() {
        startOpenAd.set(null)
        isDisplaying.set(false)
        isLoading.set(false)
        isActivityPaused.set(false)
        pendingCallback = null
    }
}
