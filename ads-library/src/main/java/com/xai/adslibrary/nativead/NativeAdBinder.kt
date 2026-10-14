package com.xai.adslibrary.nativead

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.xai.adslibrary.core.NativeLayoutProvider

/**
 * Shared binding logic used by both [NativeAdManager] and [ListNativeAdManager].
 * Applies asset data and optional [NativeAdColors].
 */
internal object NativeAdBinder {

    fun bind(
        nativeAd: NativeAd,
        adView: NativeAdView,
        style: Int,
        provider: NativeLayoutProvider,
        colors: NativeAdColors? = null
    ) {
        val mediaView = adView.findViewById<MediaView?>(provider.getMediaId())

        adView.headlineView = adView.findViewById(provider.getHeadlineId())
        adView.bodyView = adView.findViewById(provider.getBodyId())
        adView.callToActionView = adView.findViewById(provider.getCallToActionId())
        adView.iconView = adView.findViewById(provider.getIconId())
        adView.starRatingView = adView.findViewById(provider.getStarRatingId())

        // Headline
        (adView.headlineView as? TextView)?.apply {
            text = nativeAd.headline
            colors?.headlineTextColor?.let { setTextColor(it) }
        }

        // Body – visible for style >= 2 when body text exists
        (adView.bodyView as? TextView)?.apply {
            text = nativeAd.body ?: ""
            visibility = if (style >= 2 && !nativeAd.body.isNullOrEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            colors?.bodyTextColor?.let { setTextColor(it) }
        }

        // CTA
        (adView.callToActionView as? TextView)?.apply {
            text = nativeAd.callToAction ?: ""
            visibility = if (nativeAd.callToAction.isNullOrEmpty()) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }
            colors?.ctaTextColor?.let { setTextColor(it) }
            colors?.ctaBackground?.let { bg ->
                background = GradientDrawable().apply {
                    setColor(bg)
                    cornerRadius = 20f
                }
            }
        }

        // Icon
        nativeAd.icon?.drawable?.let {
            (adView.iconView as? ImageView)?.setImageDrawable(it)
            adView.iconView?.visibility = View.VISIBLE
        } ?: run {
            adView.iconView?.visibility = View.GONE
        }

        // Optional "Ad" badge
        val badgeId = provider.getAdBadgeId()
        if (badgeId != 0) {
            adView.findViewById<TextView?>(badgeId)?.apply {
                colors?.adBadgeTextColor?.let { setTextColor(it) }
                colors?.adBadgeBackground?.let { bg ->
                    background = GradientDrawable().apply {
                        setColor(bg)
                        cornerRadius = 8f
                    }
                }
            }
        }

        // Root background
        colors?.backgroundColor?.let { bg ->
            adView.setBackgroundColor(bg)
        }

        // Next-Gen: register native ad + media view together
        adView.registerNativeAd(nativeAd, mediaView)
    }
}
