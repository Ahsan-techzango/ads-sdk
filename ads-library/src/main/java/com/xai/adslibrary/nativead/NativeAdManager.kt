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
import com.xai.adslibrary.callback.NativeCallback
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.util.concurrent.atomic.AtomicReference

/**
 * Single-placement native ad manager with LiveData observability.
 * For list / RecyclerView multi-position use [ListNativeAdManager] instead.
 */
internal object NativeAdManager {

    private const val TAG = "NativeAdManager"

    private val cachedAd = AtomicReference<NativeAd?>(null)
    private var lastAdUnit: String? = null

    private val _liveData = MutableLiveData<NativeAdState>(NativeAdState.Empty)
    val liveData: LiveData<NativeAdState> = _liveData

    fun preload(activity: Activity, adUnitId: String, config: AdsConfig) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            postState(NativeAdState.Empty)
            return
        }

        if (cachedAd.get() != null && lastAdUnit == adUnitId) {
            cachedAd.get()?.let { postState(NativeAdState.Ready(it, adUnitId)) }
            return
        }

        postState(NativeAdState.Loading)
        config.analyticsListener?.logEvent(TAG, "Preloading native ($adUnitId)")

        val videoOptions = VideoOptions.Builder().setStartMuted(true).build()
        val request = NativeAdRequest.Builder(adUnitId, listOf(NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
            .build()

        NativeAdLoader.load(request, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                cachedAd.getAndSet(null)?.destroy()
                cachedAd.set(nativeAd)
                lastAdUnit = adUnitId
                Log.d(TAG, "Native preloaded")
                postState(NativeAdState.Ready(nativeAd, adUnitId))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "Preload failed: ${error.message}")
                postState(NativeAdState.Failed(error.message ?: "unknown"))
            }
        })
    }

    fun loadAndShow(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        style: Int,
        shimmer: ShimmerFrameLayout?,
        config: AdsConfig,
        colors: NativeAdColors?,
        callback: NativeCallback?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            MainThreadDispatcher.dispatch { callback?.onResult(false, true, false) }
            return
        }

        val cached = cachedAd.getAndSet(null)
        if (cached != null) {
            Log.d(TAG, "Showing cached native ad")
            MainThreadDispatcher.dispatch {
                inflateAdView(activity, cached, container, style, shimmer, config, colors)
                postState(NativeAdState.Empty)
                callback?.onResult(true, false, true)
            }
            return
        }

        shimmer?.let {
            it.visibility = View.VISIBLE
            it.startShimmer()
        }

        postState(NativeAdState.Loading)
        config.analyticsListener?.logEvent(TAG, "Loading native ($adUnitId)")

        val videoOptions = VideoOptions.Builder().setStartMuted(false).build()
        val request = NativeAdRequest.Builder(adUnitId, listOf(NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
            .build()

        NativeAdLoader.load(request, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                MainThreadDispatcher.dispatch {
                    if (PremiumManager.isPremium()) {
                        nativeAd.destroy()
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.GONE
                        container.visibility = View.GONE
                        callback?.onResult(false, true, false)
                        postState(NativeAdState.Empty)
                        return@dispatch
                    }
                    inflateAdView(activity, nativeAd, container, style, shimmer, config, colors)
                    postState(NativeAdState.Empty)
                    callback?.onResult(true, false, false)
                }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                MainThreadDispatcher.dispatch {
                    Log.d(TAG, "Failed: ${error.message}")
                    config.analyticsListener?.logEvent(TAG, "Failed: ${error.message}")
                    shimmer?.stopShimmer()
                    shimmer?.visibility = View.GONE
                    container.visibility = View.GONE
                    postState(NativeAdState.Failed(error.message ?: "unknown"))
                    callback?.onResult(false, true, false)
                }
            }
        })
    }

    fun getCachedAd(): NativeAd? = cachedAd.get()

    fun clear() {
        cachedAd.getAndSet(null)?.destroy()
        lastAdUnit = null
        postState(NativeAdState.Empty)
    }

    private fun inflateAdView(
        activity: Activity,
        nativeAd: NativeAd,
        container: ViewGroup,
        style: Int,
        shimmer: ShimmerFrameLayout?,
        config: AdsConfig,
        colors: NativeAdColors?
    ) {
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
            _liveData.value = state
        }
    }
}
