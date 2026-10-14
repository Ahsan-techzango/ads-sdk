package com.xai.adslibrary.core

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.xai.adslibrary.appopen.AppOpenAdManager
import com.xai.adslibrary.banner.BannerAdManager
import com.xai.adslibrary.callback.*
import com.xai.adslibrary.interstitial.InterstitialAdManager
import com.xai.adslibrary.nativead.ListNativeAdManager
import com.xai.adslibrary.nativead.NativeAdColors
import com.xai.adslibrary.nativead.NativeAdManager
import com.xai.adslibrary.nativead.NativeAdState
import com.xai.adslibrary.rewarded.RewardedAdManager
import com.xai.adslibrary.util.NetworkHelper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Public entry-point for the Ads SDK.
 *
 * Initialise once in Application.onCreate():
 * ```
 * AdsLibrary.initialize(this, isPremium = false)
 * ```
 */
object AdsLibrary {

    private val initialized = AtomicBoolean(false)
    private lateinit var appContext: Context
    @Volatile
    private var config: AdsConfig = AdsConfig()

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Must be called once before any other AdsLibrary method.
     *
     * @param context   Application context (or any Context – library stores applicationContext)
     * @param isPremium current premium status of the user
     * @param config    optional configuration (analytics, progress UI, native layouts …)
     */
    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        isPremium: Boolean = false,
        config: AdsConfig = AdsConfig()
    ) {
        if (initialized.getAndSet(true)) return
        appContext = context.applicationContext
        this.config = config
        PremiumManager.setPremium(isPremium)
        config.analyticsListener?.logEvent("AdsLibrary", "SDK initialized, premium=$isPremium")
    }

    /**
     * Update premium status at any time (purchase, restore, expiry, logout …).
     * Thread-safe. Pending show operations are cancelled and cached ads will not be shown.
     */
    @JvmStatic
    fun setPremiumUser(isPremium: Boolean) {
        val wasPremium = PremiumManager.isPremium()
        PremiumManager.setPremium(isPremium)
        if (isPremium && !wasPremium) {
            // Invalidate everything so no delayed shows fire
            InterstitialAdManager.clearAll()
            RewardedAdManager.clear()
            NativeAdManager.clear()
            BannerAdManager.clear()
            AppOpenAdManager.clear()
            config.analyticsListener?.logEvent("AdsLibrary", "Premium activated – all pending ads cleared")
        }
    }

    /** Current premium status. */
    @JvmStatic
    fun isPremiumUser(): Boolean = PremiumManager.isPremium()

    /** Master ads-enabled flag (from config). Can be toggled at runtime. */
    @JvmStatic
    fun setAdsEnabled(enabled: Boolean) {
        config = config.copy(adsEnabled = enabled)
    }

    @JvmStatic
    fun isAdsEnabled(): Boolean = config.adsEnabled

    // -------------------------------------------------------------------------
    // Network
    // -------------------------------------------------------------------------

    /**
     * Returns true when a validated internet connection is available.
     * Does not guarantee the ad server is reachable.
     */
    @JvmStatic
    fun isNetworkAvailable(context: Context = appContext): Boolean =
        NetworkHelper.isNetworkAvailable(context)

    // -------------------------------------------------------------------------
    // Interstitial
    // -------------------------------------------------------------------------

    /**
     * Preload an interstitial for later use.
     */
    @JvmStatic
    @JvmOverloads
    fun preloadInterstitial(
        context: Context,
        adUnitId: String,
        placement: String,
        onResult: PreloadCallback? = null
    ) {
        ensureInit()
        InterstitialAdManager.preload(context, adUnitId, placement, config, onResult)
    }

    /**
     * Load (or use cache) and show an interstitial.
     * All callbacks arrive on the main thread.
     */
    @JvmStatic
    @JvmOverloads
    fun loadAndShowInterstitial(
        activity: Activity,
        adUnitId: String,
        placement: String,
        useAnyCachedFallback: Boolean = true,
        showDelayMs: Long = 0L,
        callback: InterstitialCallback? = null
    ) {
        ensureInit()
        InterstitialAdManager.loadAndShow(
            activity, adUnitId, placement, useAnyCachedFallback, showDelayMs, config, callback
        )
    }

    /**
     * Show any already-cached interstitial (placement-agnostic fallback).
     */
    @JvmStatic
    @JvmOverloads
    fun showAnyCachedInterstitial(
        activity: Activity,
        showDelayMs: Long = 0L,
        callback: ((showed: Boolean, dismissed: Boolean, failed: Boolean) -> Unit)? = null
    ) {
        ensureInit()
        InterstitialAdManager.showAnyCached(activity, showDelayMs, config, callback)
    }

    /**
     * Counter-based interstitial (same behaviour as original showInterstitialWithCounter).
     * Host supplies the threshold and ad-unit via parameters.
     */
    @JvmStatic
    @JvmOverloads
    fun showInterstitialWithCounter(
        activity: Activity,
        adUnitId: String,
        threshold: Int,
        callback: InterstitialCallback? = null
    ) {
        ensureInit()
        InterstitialAdManager.showWithCounter(activity, adUnitId, threshold, config, callback)
    }

    // -------------------------------------------------------------------------
    // Rewarded
    // -------------------------------------------------------------------------

    @JvmStatic
    @JvmOverloads
    fun loadAndShowRewarded(
        activity: Activity,
        adUnitId: String,
        showDelayMs: Long = 0L,
        callback: RewardedCallback? = null
    ) {
        ensureInit()
        RewardedAdManager.loadAndShow(activity, adUnitId, showDelayMs, config, callback)
    }

    // -------------------------------------------------------------------------
    // Banner
    // -------------------------------------------------------------------------

    /**
     * Load and display a banner (supports collapsible).
     *
     * @param collapsible 0 = normal, 2 = collapsible (matches original)
     * @param top         true = collapse from top, false = bottom
     */
    @JvmStatic
    @JvmOverloads
    fun loadBanner(
        activity: Activity,
        bannerContainer: FrameLayout,
        adUnitId: String,
        collapsible: Int = 0,
        top: Boolean = false,
        shimmer: ShimmerFrameLayout? = null,
        callback: BannerCallback? = null
    ) {
        ensureInit()
        BannerAdManager.load(
            activity, bannerContainer, adUnitId, collapsible, top, shimmer, config, callback
        )
    }

    // -------------------------------------------------------------------------
    // Native
    // -------------------------------------------------------------------------

    /**
     * Preload a native ad. Observe [nativeAdLiveData] to react when ready.
     */
    @JvmStatic
    fun preloadNativeAd(activity: Activity, adUnitId: String) {
        ensureInit()
        NativeAdManager.preload(activity, adUnitId, config)
    }

    /**
     * LiveData that emits the current preload state of the native ad cache.
     * Observe from an Activity / Fragment to update UI when the ad becomes ready.
     *
     * Example:
     * ```
     * AdsLibrary.nativeAdLiveData.observe(this) { state ->
     *     when (state) {
     *         is NativeAdState.Ready -> // show or bind
     *         is NativeAdState.Loading -> // show shimmer
     *         is NativeAdState.Failed -> // hide container
     *         is NativeAdState.Empty -> // nothing cached
     *     }
     * }
     * ```
     */
    @JvmStatic
    val nativeAdLiveData: LiveData<NativeAdState>
        get() = NativeAdManager.liveData

    /**
     * Load (or use cache) and show a native ad into [container].
     *
     * @param style  1 = small, 2 = advance, 3 = large (host supplies layouts via NativeLayoutProvider)
     * @param colors optional overrides for background, Ad-badge and CTA colours
     */
    @JvmStatic
    @JvmOverloads
    fun loadAndShowNativeAd(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        style: Int = 1,
        shimmer: ShimmerFrameLayout? = null,
        colors: NativeAdColors? = null,
        callback: NativeCallback? = null
    ) {
        ensureInit()
        NativeAdManager.loadAndShow(
            activity, adUnitId, container, style, shimmer, config, colors, callback
        )
    }

    /**
     * Returns the currently cached NativeAd (or null).
     * Prefer observing [nativeAdLiveData] for reactive UI.
     */
    @JvmStatic
    fun getCachedNativeAd(): NativeAd? = NativeAdManager.getCachedAd()

    /**
     * Create a list / RecyclerView native-ad helper.
     *
     * Loads **once** when you call [ListNativeAdManager.load], then the same ad
     * can be bound into multiple list rows via [ListNativeAdManager.bindTo].
     *
     * ```
     * // Activity.onCreate
     * val listNative = AdsLibrary.createListNativeAd(this)
     * listNative.load(
     *     adUnitId = "ca-app-pub-…/…",
     *     style = 1,
     *     colors = NativeAdColors(
     *         backgroundColor   = Color.parseColor("#1E1D20"),
     *         adBadgeBackground = Color.parseColor("#FF3B30"),
     *         ctaBackground     = Color.parseColor("#FFD60A"),
     *         ctaTextColor      = Color.BLACK
     *     )
     * )
     * listNative.state.observe(this) { … }
     *
     * // Adapter.onBindViewHolder (ad rows)
     * listNative.bindTo(holder.adContainer, holder.shimmer)
     *
     * // Activity.onDestroy
     * listNative.destroy()
     * ```
     */
    @JvmStatic
    fun createListNativeAd(activity: Activity): ListNativeAdManager {
        ensureInit()
        return ListNativeAdManager(activity)
    }

    // -------------------------------------------------------------------------
    // App-Open
    // -------------------------------------------------------------------------

    @JvmStatic
    @JvmOverloads
    fun loadAppOpenAd(
        activity: Activity,
        adUnitId: String,
        withCallback: Boolean = false,
        callback: ((loaded: Boolean, failed: Boolean) -> Unit)? = null
    ) {
        ensureInit()
        AppOpenAdManager.load(activity, adUnitId, withCallback, config, callback)
    }

    @JvmStatic
    @JvmOverloads
    fun showAppOpenAd(
        activity: Activity,
        callback: AppOpenCallback? = null
    ) {
        ensureInit()
        AppOpenAdManager.show(activity, config, callback)
    }

    /**
     * Call from Activity.onPause() so the library can defer app-open shows.
     */
    @JvmStatic
    fun onActivityPause() {
        AppOpenAdManager.onPause()
        InterstitialAdManager.setPaused(true)
        RewardedAdManager.setPaused(true)
    }

    /**
     * Call from Activity.onResume() to resume deferred shows.
     */
    @JvmStatic
    fun onActivityResume(activity: Activity) {
        AppOpenAdManager.onResume(activity)
        InterstitialAdManager.setPaused(false, activity)
        RewardedAdManager.setPaused(false, activity)
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /** Clear a specific interstitial placement. */
    @JvmStatic
    fun clearInterstitialPlacement(placement: String) {
        InterstitialAdManager.clearPlacement(placement)
    }

    /** Clear all cached ads of every format. */
    @JvmStatic
    fun clearAll() {
        InterstitialAdManager.clearAll()
        RewardedAdManager.clear()
        NativeAdManager.clear()
        BannerAdManager.clear()
        AppOpenAdManager.clear()
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    internal fun getConfig(): AdsConfig = config

    internal fun getAppContext(): Context {
        ensureInit()
        return appContext
    }

    private fun ensureInit() {
        check(initialized.get()) {
            "AdsLibrary.initialize() must be called before any other method."
        }
    }

    /**
     * Internal gate used by all managers.
     * Returns false (and invokes failure callback on main thread) when ads
     * should be skipped (premium / disabled / offline).
     */
    internal fun shouldRequestAd(context: Context): Boolean {
        if (!config.adsEnabled) return false
        if (PremiumManager.isPremium()) return false
        if (!NetworkHelper.isNetworkAvailable(context)) return false
        return true
    }
}
