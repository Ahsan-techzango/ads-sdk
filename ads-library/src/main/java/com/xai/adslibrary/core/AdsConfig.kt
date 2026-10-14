package com.xai.adslibrary.core

/**
 * Optional configuration supplied at initialization.
 * All fields have sensible defaults so the host can start with minimal setup.
 */
data class AdsConfig(
    /** Master switch – when false every ad request is skipped. */
    val adsEnabled: Boolean = true,

    /** Hours after which a cached App-Open ad is considered expired. */
    val appOpenExpiryHours: Long = 4L,

    /** Default timeout (ms) for interstitial / rewarded load operations. */
    val loadTimeoutMs: Long = 10_000L,

    /** Timeout (ms) for pending-show when another load is already in progress. */
    val pendingShowTimeoutMs: Long = 12_000L,

    /**
     * Optional analytics listener.
     * The library never initialises Firebase itself.
     */
    val analyticsListener: AnalyticsListener? = null,

    /**
     * Optional progress-dialog provider.
     * Host supplies its own loading UI; library never hard-codes dialogs.
     */
    val progressProvider: ProgressProvider? = null,

    /**
     * Optional native-ad layout provider.
     * Host supplies layout resource IDs so the library never depends on
     * application-specific R.layout values.
     *
     * View IDs must match your layout files:
     * - ad_headline, ad_body, ad_call_to_action, icon, ad_media, ad_stars
     * - optional: adText (Ad badge)
     */
    val nativeLayoutProvider: NativeLayoutProvider? = null
)

/**
 * Host-provided analytics bridge.
 * Implement and pass via [AdsConfig.analyticsListener].
 */
fun interface AnalyticsListener {
    fun logEvent(tag: String, message: String)
}

/**
 * Host-provided loading indicator.
 * Implement to show / dismiss your own progress dialog or shimmer.
 */
interface ProgressProvider {
    fun show(activity: android.app.Activity, message: String? = null)
    fun dismiss()
}

/**
 * Host-provided native ad layout resources.
 *
 * style mapping (same as original code):
 * - 1 → small   (native_ad_small)
 * - 2 → advance (native_ad_advance)
 * - 3 → large   (native_ad_large_advance)
 *
 * View IDs must match the layouts you ship in the host app.
 * Your layouts use: ad_headline, ad_body, ad_call_to_action, icon, ad_media, ad_stars, adText.
 */
interface NativeLayoutProvider {
    /** Returns layout resource ID for the given style (1, 2 or 3). */
    fun getLayoutRes(style: Int): Int

    /** View IDs used when binding native ad assets. */
    fun getHeadlineId(): Int
    fun getBodyId(): Int
    fun getCallToActionId(): Int
    /** Icon ImageView – in your layouts this is R.id.icon */
    fun getIconId(): Int
    fun getMediaId(): Int
    fun getStarRatingId(): Int

    /**
     * Optional "Ad" badge TextView id (R.id.adText in your layouts).
     * Return 0 if the layout does not contain one.
     */
    fun getAdBadgeId(): Int = 0
}
