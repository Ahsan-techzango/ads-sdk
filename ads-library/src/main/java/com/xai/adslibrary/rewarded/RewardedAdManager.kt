package com.xai.adslibrary.rewarded

import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.xai.adslibrary.callback.RewardedCallback
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object RewardedAdManager {

    private const val TAG = "RewardedAdManager"

    private val rewardedAd = AtomicReference<RewardedAd?>(null)
    private val isLoading = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var pendingAd: RewardedAd? = null
    private var pendingCallback: RewardedCallback? = null

    fun loadAndShow(
        activity: Activity,
        adUnitId: String,
        showDelayMs: Long,
        config: AdsConfig,
        callback: RewardedCallback?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            MainThreadDispatcher.dispatch {
                callback?.onResult(false, true, false, false, false)
            }
            return
        }

        // Already loaded → show
        rewardedAd.get()?.let { ad ->
            MainThreadDispatcher.dispatch {
                callback?.onResult(true, false, false, false, false)
            }
            scheduleShow(activity, ad, showDelayMs, config, callback)
            return
        }

        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Already loading rewarded")
            return
        }

        val timedOut = AtomicBoolean(false)
        val timeoutRunnable = MainThreadDispatcher.postDelayed(config.loadTimeoutMs) {
            if (rewardedAd.get() == null) {
                isLoading.set(false)
                timedOut.set(true)
                config.progressProvider?.dismiss()
                Log.d(TAG, "Rewarded load timeout")
                callback?.onResult(false, true, false, false, false)
            }
        }

        config.progressProvider?.show(activity, null)
        config.analyticsListener?.logEvent(TAG, "Loading rewarded ($adUnitId)")

        RewardedAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    MainThreadDispatcher.removeCallbacks(timeoutRunnable)
                    rewardedAd.set(ad)
                    isLoading.set(false)

                    if (timedOut.get() || PremiumManager.isPremium()) {
                        config.progressProvider?.dismiss()
                        return
                    }

                    MainThreadDispatcher.dispatch {
                        callback?.onResult(true, false, false, false, false)
                    }
                    scheduleShow(activity, ad, showDelayMs, config, callback)
                    Log.d(TAG, "Rewarded loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    MainThreadDispatcher.removeCallbacks(timeoutRunnable)
                    rewardedAd.set(null)
                    isLoading.set(false)
                    config.progressProvider?.dismiss()
                    Log.d(TAG, "Rewarded load failed: $error")
                    MainThreadDispatcher.dispatch {
                        callback?.onResult(false, true, false, false, false)
                    }
                }
            }
        )
    }

    fun setPaused(paused: Boolean, activity: Activity? = null) {
        isPaused.set(paused)
        if (!paused && activity != null) {
            pendingAd?.let { ad ->
                internalShow(activity, ad, AdsLibrary.getConfig(), pendingCallback)
                pendingAd = null
                pendingCallback = null
            }
        }
    }

    fun clear() {
        rewardedAd.getAndSet(null)
        isLoading.set(false)
        pendingAd = null
        pendingCallback = null
    }

    // -------------------------------------------------------------------------

    private fun scheduleShow(
        activity: Activity,
        ad: RewardedAd,
        delayMs: Long,
        config: AdsConfig,
        callback: RewardedCallback?
    ) {
        MainThreadDispatcher.postDelayed(delayMs) {
            if (PremiumManager.isPremium() || activity.isFinishing || activity.isDestroyed) {
                config.progressProvider?.dismiss()
                callback?.onResult(false, true, false, false, false)
                return@postDelayed
            }
            internalShow(activity, ad, config, callback)
        }
    }

    private fun internalShow(
        activity: Activity,
        ad: RewardedAd,
        config: AdsConfig,
        callback: RewardedCallback?
    ) {
        if (isPaused.get()) {
            pendingAd = ad
            pendingCallback = callback
            return
        }

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                config.progressProvider?.dismiss()
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, false, true, false, false)
                }
                Log.d(TAG, "Rewarded showed")
            }

            override fun onAdDismissedFullScreenContent() {
                config.progressProvider?.dismiss()
                rewardedAd.set(null)
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, false, false, true, false)
                }
                Log.d(TAG, "Rewarded dismissed")
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                rewardedAd.set(null)
                config.progressProvider?.dismiss()
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, true, false, false, false)
                }
                Log.d(TAG, "Rewarded failed to show: $error")
            }
        }

        try {
            ad.show(activity) { rewardItem ->
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, false, false, false, true)
                }
                Log.d(TAG, "Reward earned: ${rewardItem.amount} ${rewardItem.type}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Show threw: ${t.message}")
            MainThreadDispatcher.dispatch {
                callback?.onResult(false, true, false, false, false)
            }
        }
    }
}
