package com.xai.adslibrary.nativead

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * Observable state of the native-ad preload cache.
 * Observe via [com.xai.adslibrary.core.AdsLibrary.nativeAdLiveData].
 */
sealed class NativeAdState {
    /** Nothing cached and not loading. */
    object Empty : NativeAdState()

    /** A load is in progress. */
    object Loading : NativeAdState()

    /** A native ad is ready for use. */
    data class Ready(val ad: NativeAd, val adUnitId: String) : NativeAdState()

    /** Last load attempt failed. */
    data class Failed(val message: String) : NativeAdState()
}
