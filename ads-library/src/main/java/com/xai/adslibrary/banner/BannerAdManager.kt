package com.xai.adslibrary.banner

import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.xai.adslibrary.callback.BannerCallback
import com.xai.adslibrary.core.AdsConfig
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.core.PremiumManager
import com.xai.adslibrary.util.MainThreadDispatcher
import java.lang.ref.WeakReference

internal object BannerAdManager {

    private const val TAG = "BannerAdManager"

    @Volatile
    private var currentContainer: WeakReference<FrameLayout>? = null

    fun load(
        activity: Activity,
        container: FrameLayout,
        adUnitId: String,
        collapsible: Int,
        top: Boolean,
        shimmer: ShimmerFrameLayout?,
        config: AdsConfig,
        callback: BannerCallback?
    ) {
        if (!AdsLibrary.shouldRequestAd(activity)) {
            container.visibility = View.GONE
            MainThreadDispatcher.dispatch { callback?.onResult(false, true) }
            return
        }

        currentContainer = WeakReference(container)
        container.visibility = View.VISIBLE
        container.minimumHeight = 110
        container.layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT

        config.analyticsListener?.logEvent(TAG, "Loading banner ($adUnitId)")

        val adView = AdView(activity)
        val adSize = getAdSize(activity)

        val requestBuilder = BannerAdRequest.Builder(adUnitId, adSize)

        // Collapsible support (Next-Gen uses Google extras) – same as original
        if (collapsible == 2) {
            val extras = Bundle().apply {
                putString("collapsible", if (top) "top" else "bottom")
            }
            requestBuilder.setGoogleExtrasBundle(extras)
        }

        val adRequest = requestBuilder.build()

        adView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    MainThreadDispatcher.dispatch {
                        if (PremiumManager.isPremium()) {
                            container.visibility = View.GONE
                            callback?.onResult(false, true)
                            return@dispatch
                        }
                        container.layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT
                        shimmer?.stopShimmer()
                        shimmer?.visibility = View.INVISIBLE
                        container.removeAllViews()
                        container.addView(adView)
                        callback?.onResult(true, false)
                        Log.d(TAG, "Banner loaded")
                        config.analyticsListener?.logEvent(TAG, "Banner loaded")
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    MainThreadDispatcher.dispatch {
                        container.visibility = View.GONE
                        Log.d(TAG, "Banner failed: ${error.message}")
                        config.analyticsListener?.logEvent(TAG, "Banner failed: ${error.message}")
                        callback?.onResult(false, true)
                    }
                }
            }
        )
    }

    /** Hide and clear any banner currently managed by the SDK (e.g. after premium). */
    fun clear() {
        currentContainer?.get()?.let { container ->
            MainThreadDispatcher.dispatch {
                container.removeAllViews()
                container.visibility = View.GONE
            }
        }
        currentContainer = null
    }

    private fun getAdSize(activity: Activity): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        val adWidth = (outMetrics.widthPixels / outMetrics.density).toInt()
        return AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}
