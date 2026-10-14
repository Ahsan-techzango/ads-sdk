package com.xai.adslibrary.nativead

import android.app.Activity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd.NativeAdType
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * List / RecyclerView native-ad helper.
 *
 * Loads a **single** native ad when the Activity is created, then re-uses that
 * same ad instance across multiple list positions (same behaviour as the
 * original WidgetNativeAd).
 *
 * Usage:
 * ```
 * // In Activity.onCreate – start loading once
 * val listNative = AdsLibrary.createListNativeAd(this)
 * listNative.load(adUnitId = "…", style = 1, colors = NativeAdColors(…))
 *
 * // Observe readiness
 * listNative.state.observe(this) { … }
 *
 * // In adapter onBindViewHolder for ad rows
 * listNative.bindTo(holder.adContainer, holder.shimmer)
 *
 * // In Activity.onDestroy
 * listNative.destroy()
 * ```
 */
class ListNativeAdManager internal constructor(
    activity: Activity
) {

    companion object {
        private const val TAG = "ListNativeAdManager"
    }

    private val activityRef = WeakReference(activity)

    private val cachedAd = AtomicReference<NativeAd?>(null)
    private val isLoading = AtomicBoolean(false)

    private val _state = MutableLiveData<NativeAdState>(NativeAdState.Empty)
    /** Observe to know when the single shared ad is ready / failed. */
    val state: LiveData<NativeAdState> = _state

    /** Containers that asked for the ad before it finished loading. */
    private data class WaitingSlot(
        val container: WeakReference<ViewGroup>,
        val shimmer: WeakReference<ShimmerFrameLayout?>
    )

    private val waitingSlots = CopyOnWriteArrayList<WaitingSlot>()

    private var style: Int = 1
    private var colors: NativeAdColors? = null
    private var config: AdsConfig = AdsConfig()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Start loading the single shared native ad.
     * Safe to call once from Activity.onCreate.
     * Subsequent [bindTo] calls will either show immediately (if ready) or
     * wait until the load finishes.
     *
     * @param adUnitId  AdMob native ad unit ID
     * @param style     1 = small, 2 = advance, 3 = large
     * @param colors    optional color overrides (background, Ad badge, CTA …)
     */
    fun load(
        adUnitId: String,
        style: Int = 1,
        colors: NativeAdColors? = null
    ) {
        this.style = style
        this.colors = colors
        this.config = AdsLibrary.getConfig()

        val activity = activityRef.get() ?: return

        if (!AdsLibrary.shouldRequestAd(activity)) {
            postState(NativeAdState.Empty)
            return
        }

        // Already have an ad for this unit
        if (cachedAd.get() != null) {
            postState(NativeAdState.Ready(cachedAd.get()!!, adUnitId))
            return
        }

        if (!isLoading.compareAndSet(false, true)) {
            Log.d(TAG, "Already loading – waiting slots will be notified")
            return
        }

        postState(NativeAdState.Loading)
        config.analyticsListener?.logEvent(TAG, "List native load ($adUnitId)")

        val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
        val request = NativeAdRequest.Builder(adUnitId, listOf(NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
            .build()

        NativeAdLoader.load(request, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                isLoading.set(false)
                cachedAd.getAndSet(null)?.destroy()
                cachedAd.set(nativeAd)
                Log.d(TAG, "List native loaded – binding ${waitingSlots.size} waiting slots")

                MainThreadDispatcher.dispatch {
                    postState(NativeAdState.Ready(nativeAd, adUnitId))
                    // Bind to every container that was waiting
                    val slots = waitingSlots.toList()
                    waitingSlots.clear()
                    slots.forEach { slot ->
                        val container = slot.container.get() ?: return@forEach
                        val shimmer = slot.shimmer.get()
                        bindInternal(container, shimmer, nativeAd)
                    }
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading.set(false)
                Log.e(TAG, "List native failed: ${error.message}")
                MainThreadDispatcher.dispatch {
                    postState(NativeAdState.Failed(error.message ?: "unknown"))
                    waitingSlots.forEach { slot ->
                        slot.shimmer.get()?.apply {
                            stopShimmer()
                            visibility = View.GONE
                        }
                        slot.container.get()?.visibility = View.GONE
                    }
                    waitingSlots.clear()
                }
            }
        })
    }

    /**
     * Bind the shared native ad into a list-item container.
     * Call from RecyclerView.Adapter.onBindViewHolder for ad rows.
     *
     * - If the ad is already loaded → inflates and shows immediately.
     * - If still loading → shows shimmer (if provided) and queues the container;
     *   it will be filled when the load finishes.
     * - If failed / premium / offline → hides the container.
     */
    fun bindTo(
        container: ViewGroup,
        shimmer: ShimmerFrameLayout? = null
    ) {
        if (PremiumManager.isPremium()) {
            container.visibility = View.GONE
            shimmer?.visibility = View.GONE
            return
        }

        val ad = cachedAd.get()
        if (ad != null) {
            bindInternal(container, shimmer, ad)
            return
        }

        // Still loading or not started – show shimmer and wait
        if (isLoading.get() || _state.value is NativeAdState.Loading) {
            shimmer?.visibility = View.VISIBLE
            shimmer?.startShimmer()
            container.visibility = View.VISIBLE
            waitingSlots.add(
                WaitingSlot(WeakReference(container), WeakReference(shimmer))
            )
            return
        }

        // Failed or empty
        shimmer?.stopShimmer()
        shimmer?.visibility = View.GONE
        container.visibility = View.GONE
    }

    /**
     * Returns true when the shared ad is ready to bind.
     */
    fun isReady(): Boolean = cachedAd.get() != null

    /**
     * Destroy the cached ad and clear waiting slots.
     * Call from Activity.onDestroy.
     */
    fun destroy() {
        cachedAd.getAndSet(null)?.destroy()
        isLoading.set(false)
        waitingSlots.clear()
        postState(NativeAdState.Empty)
        Log.d(TAG, "List native destroyed")
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun bindInternal(
        container: ViewGroup,
        shimmer: ShimmerFrameLayout?,
        nativeAd: NativeAd
    ) {
        val activity = activityRef.get() ?: return
        // Use host provider if supplied, otherwise library built-in layouts
        val provider = config.nativeLayoutProvider ?: DefaultNativeLayoutProvider

        container.removeAllViews()

        val layoutRes = provider.getLayoutRes(style)
        val adView = LayoutInflater.from(activity)
            .inflate(layoutRes, container, false) as NativeAdView

        NativeAdBinder.bind(nativeAd, adView, style, provider, colors)

        shimmer?.stopShimmer()
        shimmer?.visibility = View.GONE

        container.addView(adView)
        container.visibility = View.VISIBLE
    }

    private fun postState(state: NativeAdState) {
        MainThreadDispatcher.dispatch {
            _state.value = state
        }
    }
}
