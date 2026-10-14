package com.xai.adslibrary.callback

/**
 * All callbacks are delivered on the main/UI thread by the library.
 */

/** Interstitial load + show lifecycle. */
fun interface InterstitialCallback {
    /**
     * @param loaded   true when the ad finished loading successfully
     * @param failed   true when load or show failed
     * @param showed   true when the ad was displayed full-screen
     * @param dismissed true when the user closed the ad
     */
    fun onResult(loaded: Boolean, failed: Boolean, showed: Boolean, dismissed: Boolean)
}

/** Rewarded load + show + reward lifecycle. */
fun interface RewardedCallback {
    /**
     * @param loaded    true when the ad finished loading
     * @param failed    true when load or show failed
     * @param showed    true when the ad was displayed
     * @param dismissed true when the user closed the ad
     * @param rewarded  true when the user earned the reward
     */
    fun onResult(
        loaded: Boolean,
        failed: Boolean,
        showed: Boolean,
        dismissed: Boolean,
        rewarded: Boolean
    )
}

/** Banner load result. */
fun interface BannerCallback {
    fun onResult(loaded: Boolean, failed: Boolean)
}

/** Native ad load result. */
fun interface NativeCallback {
    /**
     * @param loaded    true when the ad is ready
     * @param failed    true on failure
     * @param fromCache true when a previously cached ad was used
     */
    fun onResult(loaded: Boolean, failed: Boolean, fromCache: Boolean)
}

/** App-Open ad show result. */
fun interface AppOpenCallback {
    fun onResult(showed: Boolean, dismissed: Boolean, failed: Boolean)
}

/** Simple success / failure for preload operations. */
fun interface PreloadCallback {
    fun onResult(success: Boolean)
}
