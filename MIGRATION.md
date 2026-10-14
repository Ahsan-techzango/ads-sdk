# Migration from Original Ads Classes

## Summary of Changes

### What moved into the library (reusable)

| Original | Library location |
|----------|------------------|
| `Ads.kt` (interstitial + rewarded logic) | `interstitial/InterstitialAdManager.kt`, `rewarded/RewardedAdManager.kt` |
| `AdBanner.kt` | `banner/BannerAdManager.kt` |
| `NativeAdManager.kt` | `nativead/NativeAdManager.kt` (+ LiveData) |
| `WidgetNativeAd.kt` | Unified into `NativeAdManager` (no separate widget class needed) |
| `AppOpenAdManager.kt` | `appopen/AppOpenAdManager.kt` |
| Network check | `util/NetworkHelper.kt` |
| Callback threading | `util/MainThreadDispatcher.kt` |

### What stayed in the host application

- Premium / billing status → call `AdsLibrary.setPremiumUser()`
- Remote Config values (ad unit IDs, counters, feature flags) → pass as method parameters
- Progress dialogs → implement `ProgressProvider`
- Analytics / Firebase → implement `AnalyticsListener`
- Native ad XML layouts & view IDs → implement `NativeLayoutProvider`
- Application subclass, activities, themes, billing

### New capabilities

1. **LiveData for native preload** – `AdsLibrary.nativeAdLiveData`
2. **Thread-safe premium updates** that cancel pending shows
3. **Main-thread guarantee** for every public callback
4. **No R-class / package-name coupling**

### API mapping cheatsheet

```kotlin
// OLD
Ads.loadAndShowInterstitial(activity, id, "home", true, 0L) { l, f, s, d -> }

// NEW
AdsLibrary.loadAndShowInterstitial(activity, id, "home", true, 0L) { l, f, s, d -> }

// OLD
AdBanner.adBanner(activity, container, id, type, shimmer, top)

// NEW
AdsLibrary.loadBanner(activity, container, id, collapsible = type, top = top, shimmer = shimmer)

// OLD
NativeAdManager.preloadNativeAd(activity, id)
// (no observation)

// NEW
AdsLibrary.preloadNativeAd(activity, id)
AdsLibrary.nativeAdLiveData.observe(this) { state -> … }

// OLD
AppOpenAdManager.loadAppOpenAd(activity, …)
AppOpenAdManager.showAppOpenAdForSplash(activity, …)

// NEW
AdsLibrary.loadAppOpenAd(activity, adUnitId, …)
AdsLibrary.showAppOpenAd(activity, …)
```


## Native colours

Pass `NativeAdColors` to either single-placement or list native APIs:

```kotlin
NativeAdColors(
    backgroundColor   = Color.parseColor("#1E1D20"),  // card bg
    adBadgeBackground = Color.parseColor("#FF3B30"),  // "Ad" badge bg
    adBadgeTextColor  = Color.BLACK,
    ctaBackground     = Color.parseColor("#FFD60A"),  // CTA button bg
    ctaTextColor      = Color.BLACK,
    headlineTextColor = Color.WHITE,
    bodyTextColor     = Color.WHITE
)
```

Any field left `null` keeps the layout's default colour/drawable.
