package com.xai.adslibrary.interstitial

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.xai.adslibrary.callback.InterstitialCallback
import com.xai.adslibrary.callback.PreloadCallback
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interstitial ad manager – preserves original caching, fallback, timeout,
 * pending-show and pause/resume behaviour.
 */
internal object InterstitialAdManager {

    private const val TAG = "InterstitialAdManager"

    private val cache = ConcurrentHashMap<String, InterstitialAd?>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val pendingShow = ConcurrentHashMap<String, PendingShow>()
    private val pausedPending = ConcurrentHashMap<String, InterstitialCallback?>()

    private val isPaused = AtomicBoolean(false)
    private val activityCounter = AtomicInteger(0)
    private val isVisible = AtomicBoolean(false)

    private data class PendingShow(
        val activityRef: WeakReference<Activity>,
        val showDelayMs: Long,
        val callback: InterstitialCallback?,
        val timeoutRunnable: Runnable
    )

    // -------------------------------------------------------------------------
    // Public API used by AdsLibrary
    // -------------------------------------------------------------------------

    fun preload(
        context: Context,
        adUnitId: String,
        placement: String,
        config: AdsConfig,
        onResult: PreloadCallback?
    ) {
        if (!AdsLibrary.shouldRequestAd(context)) {
            MainThreadDispatcher.dispatch { onResult?.onResult(false) }
            return
        }
        if (cache[placement] != null) {
            MainThreadDispatcher.dispatch { onResult?.onResult(true) }
            return
        }
        if (!loading.add(placement)) {
            MainThreadDispatcher.dispatch { onResult?.onResult(false) }
            return
        }

        config.analyticsListener?.logEvent(TAG, "Preloading interstitial ($placement)")

        InterstitialAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cache[placement] = ad
                    loading.remove(placement)
                    Log.d(TAG, "Preloaded ($placement)")
                    MainThreadDispatcher.dispatch { onResult?.onResult(true) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading.remove(placement)
                    cache[placement] = null
                    Log.d(TAG, "Preload failed ($placement): $error")
                    MainThreadDispatcher.dispatch { onResult?.onResult(false) }
                }
            }
        )
    }

    fun loadAndShow(
        activity: Activity,
        adUnitId: String,
        placement: String,
        useAnyCachedFallback: Boolean,
        showDelayMs: Long,
        config: AdsConfig,
        callback: InterstitialCallback?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            config.progressProvider?.dismiss()
            MainThreadDispatcher.dispatch {
                callback?.onResult(loaded = false, failed = true, showed = false, dismissed = false)
            }
            return
        }

        isVisible.set(true)

        // Use placement-specific or any cached ad
        val cached = cache[placement]
            ?: if (useAnyCachedFallback) cache.values.firstOrNull { it != null } else null

        if (cached != null) {
            MainThreadDispatcher.dispatch {
                callback?.onResult(loaded = true, failed = false, showed = false, dismissed = false)
            }
            scheduleShow(activity, cached, placement, showDelayMs, config, callback)
            return
        }

        // Already loading → register pending show
        if (!loading.add(placement)) {
            Log.d(TAG, "Already loading; will show when ready ($placement)")
            clearPending(placement)

            val timeoutRunnable = MainThreadDispatcher.postDelayed(config.pendingShowTimeoutMs) {
                if (pendingShow.containsKey(placement)) {
                    clearPending(placement)
                    loading.remove(placement)
                    config.progressProvider?.dismiss()
                    isVisible.set(false)
                    callback?.onResult(false, true, false, false)
                    Log.d(TAG, "Pending show timeout ($placement)")
                }
            }

            pendingShow[placement] = PendingShow(
                WeakReference(activity),
                showDelayMs,
                callback,
                timeoutRunnable
            )
            return
        }

        // Fresh load with timeout
        val timedOut = AtomicBoolean(false)
        val timeoutRunnable = MainThreadDispatcher.postDelayed(config.loadTimeoutMs) {
            if (cache[placement] == null) {
                loading.remove(placement)
                clearPending(placement)
                config.progressProvider?.dismiss()
                isVisible.set(false)
                timedOut.set(true)
                Log.d(TAG, "Load timeout ($placement)")
                callback?.onResult(false, true, false, false)
            }
        }

        config.analyticsListener?.logEvent(TAG, "Loading interstitial ($adUnitId)")

        InterstitialAd.load(
            AdRequest.Builder(adUnitId).build(),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(ad: InterstitialAd) {
                    MainThreadDispatcher.removeCallbacks(timeoutRunnable)
                    cache[placement] = ad
                    loading.remove(placement)

                    // Fulfil any pending show that was waiting
                    val pending = pendingShow.remove(placement)
                    if (pending != null) {
                        MainThreadDispatcher.removeCallbacks(pending.timeoutRunnable)
                        val act = pending.activityRef.get()
                        if (act != null && !act.isFinishing && !act.isDestroyed) {
                            MainThreadDispatcher.dispatch {
                                pending.callback?.onResult(true, false, false, false)
                            }
                            scheduleShow(act, ad, placement, pending.showDelayMs, config, pending.callback)
                        } else {
                            MainThreadDispatcher.dispatch {
                                pending.callback?.onResult(false, true, false, false)
                            }
                        }
                        return
                    }

                    if (timedOut.get() || PremiumManager.isPremium()) {
                        config.progressProvider?.dismiss()
                        return
                    }

                    MainThreadDispatcher.dispatch {
                        callback?.onResult(true, false, false, false)
                    }
                    scheduleShow(activity, ad, placement, showDelayMs, config, callback)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    MainThreadDispatcher.removeCallbacks(timeoutRunnable)
                    loading.remove(placement)
                    cache[placement] = null
                    isVisible.set(false)
                    clearPending(placement)
                    config.progressProvider?.dismiss()
                    Log.d(TAG, "Load failed ($placement): $error")
                    config.analyticsListener?.logEvent(TAG, "Load failed ($placement): $error")
                    MainThreadDispatcher.dispatch {
                        callback?.onResult(false, true, false, false)
                    }
                }
            }
        )
    }

    fun showAnyCached(
        activity: Activity,
        showDelayMs: Long,
        config: AdsConfig,
        callback: ((Boolean, Boolean, Boolean) -> Unit)?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            MainThreadDispatcher.dispatch { callback?.invoke(false, false, true) }
            return
        }

        val entry = cache.entries.firstOrNull { it.value != null }
        if (entry == null) {
            MainThreadDispatcher.dispatch { callback?.invoke(false, false, true) }
            return
        }

        val placement = entry.key
        val ad = entry.value!!
        scheduleShow(activity, ad, placement, showDelayMs, config) { _, failed, showed, dismissed ->
            callback?.invoke(showed, dismissed, failed)
        }
    }

    fun showWithCounter(
        activity: Activity,
        adUnitId: String,
        threshold: Int,
        config: AdsConfig,
        callback: InterstitialCallback?
    ) {
        val count = activityCounter.incrementAndGet()
        if (count >= threshold && AdsLibrary.isAdsEnabled()) {
            config.progressProvider?.show(activity, null)
            activityCounter.set(0)
            loadAndShow(activity, adUnitId, "counter", true, 0L, config, callback)
        } else {
            MainThreadDispatcher.dispatch {
                callback?.onResult(false, false, false, true) // skipped
            }
        }
    }

    fun setPaused(paused: Boolean, activity: Activity? = null) {
        isPaused.set(paused)
        if (!paused && activity != null) {
            val pending = pausedPending.toMap()
            pausedPending.clear()
            pending.forEach { (placement, cb) ->
                cache[placement]?.let { ad ->
                    internalShow(activity, ad, placement, AdsLibrary.getConfig(), cb)
                }
            }
        }
    }

    fun clearPlacement(placement: String) {
        cache[placement] = null
        loading.remove(placement)
        clearPending(placement)
        pausedPending.remove(placement)
    }

    fun clearAll() {
        cache.keys.toList().forEach { cache[it] = null }
        loading.clear()
        pendingShow.values.forEach { MainThreadDispatcher.removeCallbacks(it.timeoutRunnable) }
        pendingShow.clear()
        pausedPending.clear()
        isVisible.set(false)
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun scheduleShow(
        activity: Activity,
        ad: InterstitialAd,
        placement: String,
        delayMs: Long,
        config: AdsConfig,
        callback: InterstitialCallback?
    ) {
        MainThreadDispatcher.postDelayed(delayMs) {
            if (PremiumManager.isPremium()) {
                config.progressProvider?.dismiss()
                callback?.onResult(false, true, false, false)
                return@postDelayed
            }
            if (activity.isFinishing || activity.isDestroyed) {
                Log.d(TAG, "Activity invalid at show time")
                callback?.onResult(false, true, false, false)
                return@postDelayed
            }
            internalShow(activity, ad, placement, config, callback)
        }
    }

    private fun internalShow(
        activity: Activity,
        ad: InterstitialAd,
        placement: String,
        config: AdsConfig,
        callback: InterstitialCallback?
    ) {
        if (isPaused.get()) {
            Log.d(TAG, "Paused – deferring show ($placement)")
            pausedPending[placement] = callback
            return
        }
        if (activity.isFinishing || activity.isDestroyed || PremiumManager.isPremium()) {
            isVisible.set(false)
            return
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                config.progressProvider?.dismiss()
                isVisible.set(true)
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, false, true, false)
                }
                Log.d(TAG, "Showed ($placement)")
            }

            override fun onAdDismissedFullScreenContent() {
                isVisible.set(false)
                // Invalidate this ad wherever it was stored
                cache.entries.filter { it.value === ad }.forEach { cache[it.key] = null }
                activityCounter.set(0)
                config.progressProvider?.dismiss()
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, false, false, true)
                }
                Log.d(TAG, "Dismissed ($placement)")
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                config.progressProvider?.dismiss()
                isVisible.set(false)
                MainThreadDispatcher.dispatch {
                    callback?.onResult(false, true, false, false)
                }
                Log.d(TAG, "Failed to show ($placement): $error")
            }
        }

        try {
            ad.show(activity)
        } catch (t: Throwable) {
            Log.e(TAG, "Show threw: ${t.message}")
            isVisible.set(false)
            MainThreadDispatcher.dispatch {
                callback?.onResult(false, true, false, false)
            }
        }
    }

    private fun clearPending(placement: String) {
        pendingShow.remove(placement)?.let {
            MainThreadDispatcher.removeCallbacks(it.timeoutRunnable)
        }
    }
}
