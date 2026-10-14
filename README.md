# Ads Library – Reusable Android Ads SDK

A production-ready Android library that wraps the **Next-Gen Google Mobile Ads SDK** and provides a clean, configurable API for Interstitial, Rewarded, Native, Banner and App-Open ads.

Built by extracting and refactoring existing production ad managers so they can be shared across multiple apps via a Gradle dependency.

---

## Supported Ad Formats

| Format        | Load / Show | Preload | Cache / Fallback | Collapsible | LiveData |
|---------------|-------------|---------|------------------|-------------|----------|
| Interstitial  | ✅          | ✅      | ✅               | –           | –        |
| Rewarded      | ✅          | –       | ✅               | –           | –        |
| Native        | ✅          | ✅      | ✅               | –           | ✅       |
| Banner        | ✅          | –       | –                | ✅          | –        |
| App-Open      | ✅          | ✅      | ✅ (expiry)      | –           | –        |

---

## Architecture Overview

```
ads-library/
├── core/
│   ├── AdsLibrary.kt          ← Public facade
│   ├── AdsConfig.kt           ← Configuration + host-provided interfaces
│   └── PremiumManager.kt      ← Thread-safe premium flag
├── interstitial/
├── rewarded/
├── banner/
├── nativead/
│   ├── NativeAdManager.kt
│   └── NativeAdState.kt       ← LiveData sealed class
├── appopen/
├── callback/                  ← Public callback interfaces
└── util/
    ├── MainThreadDispatcher.kt
    └── NetworkHelper.kt
```

**Key design decisions**

- No dependency on host `Application`, `R` class, billing, Remote Config or SharedPreferences.
- Premium status is pushed **into** the SDK by the host.
- All public callbacks are delivered on the **main/UI thread**.
- Native ad preload state is exposed as `LiveData<NativeAdState>`.
- Progress dialogs, analytics and native layouts are supplied by the host via interfaces.

---

## Prerequisites

- `minSdk 24`
- `compileSdk 34` (or higher)
- Kotlin 1.9+
- Android Gradle Plugin 8.x
- Next-Gen Google Mobile Ads SDK (`com.google.android.libraries.ads.mobile.sdk`)

---

## Installation

### Option A – JitPack (recommended for public / easy sharing)

1. Add the repository in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

2. Add the dependency:

```kotlin
dependencies {
    implementation("com.github.OWNER:ads-sdk:1.0.0")
    // or a commit / branch: "com.github.OWNER:ads-sdk:main-SNAPSHOT"
}
```

> Replace `OWNER` with your GitHub username or organisation.

### Option B – GitHub Packages

1. In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/OWNER/ads-sdk")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}
```

2. Dependency:

```kotlin
implementation("com.xai.adslibrary:ads-library:1.0.0")
```

3. On each developer machine create `~/.gradle/gradle.properties` (never commit):

```
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PAT   # needs read:packages scope
```

---

## AndroidManifest

The library already declares:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Host application must still add** the Google Mobile Ads App ID:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"/>
```

---

## Initialisation

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        AdsLibrary.initialize(
            context = this,
            isPremium = Preferences.isPremiumUser(this), // your own check
            config = AdsConfig(
                adsEnabled = true,
                analyticsListener = { tag, msg -> Log.d(tag, msg) },
                progressProvider = MyProgressProvider(),
                nativeLayoutProvider = MyNativeLayoutProvider()
            )
        )
    }
}
```

### Updating premium status

```kotlin
// After purchase / restore / expiry
AdsLibrary.setPremiumUser(true)   // or false
```

When premium becomes `true` the SDK:

- Cancels all pending show operations
- Clears interstitial / rewarded / native / banner / app-open caches
- Skips every subsequent ad request

### Network check

```kotlin
if (!AdsLibrary.isNetworkAvailable(context)) {
    // Host decides: show dialog, retry, open settings …
}
```

---

## Usage Examples

### Interstitial

```kotlin
AdsLibrary.loadAndShowInterstitial(
    activity = this,
    adUnitId = "ca-app-pub-…/…",
    placement = "home",
    useAnyCachedFallback = true,
    showDelayMs = 0L
) { loaded, failed, showed, dismissed ->
    // Already on main thread – safe to update UI / navigate
}
```

Preload:

```kotlin
AdsLibrary.preloadInterstitial(context, adUnitId, "home") { success -> }
```

Counter-based (same logic as original `showInterstitialWithCounter`):

```kotlin
AdsLibrary.showInterstitialWithCounter(
    activity = this,
    adUnitId = RemoteConfig.getInterstitialId(),
    threshold = 3
) { loaded, failed, showed, dismissed -> }
```

### Rewarded

```kotlin
AdsLibrary.loadAndShowRewarded(
    activity = this,
    adUnitId = "ca-app-pub-…/…"
) { loaded, failed, showed, dismissed, rewarded ->
    if (rewarded) unlockFeature()
}
```

### Banner

```kotlin
AdsLibrary.loadBanner(
    activity = this,
    bannerContainer = binding.bannerContainer,
    adUnitId = "ca-app-pub-…/…",
    collapsible = 2,   // 0 = normal, 2 = collapsible
    top = false,
    shimmer = binding.shimmer
) { loaded, failed -> }
```

### Native + LiveData (single placement)

```kotlin
AdsLibrary.nativeAdLiveData.observe(this) { state ->
    when (state) {
        is NativeAdState.Ready -> { /* ad ready */ }
        is NativeAdState.Loading -> showShimmer()
        is NativeAdState.Failed -> hideAdContainer()
        is NativeAdState.Empty -> { }
    }
}

AdsLibrary.preloadNativeAd(this, nativeAdUnitId)

// Load & show with optional colour overrides
AdsLibrary.loadAndShowNativeAd(
    activity = this,
    adUnitId = nativeAdUnitId,
    container = binding.nativeContainer,
    style = 1,          // 1=small, 2=advance, 3=large
    shimmer = binding.shimmer,
    colors = NativeAdColors(
        backgroundColor   = Color.parseColor("#1E1D20"),
        adBadgeBackground = Color.parseColor("#FF3B30"),
        adBadgeTextColor  = Color.BLACK,
        ctaBackground     = Color.parseColor("#FFD60A"),
        ctaTextColor      = Color.BLACK,
        headlineTextColor = Color.WHITE,
        bodyTextColor     = Color.WHITE
    )
) { loaded, failed, fromCache -> }
```

### List / RecyclerView native (load once, show in many rows)

Same behaviour as the original `WidgetNativeAd` – one network request, many list positions.

```kotlin
// Activity.onCreate – load once
val listNative = AdsLibrary.createListNativeAd(this)
listNative.load(
    adUnitId = "ca-app-pub-…/…",
    style = 1,   // or 2 / 3
    colors = NativeAdColors(
        backgroundColor   = Color.parseColor("#1E1D20"),
        adBadgeBackground = Color.parseColor("#FF3B30"),
        ctaBackground     = Color.parseColor("#FFD60A"),
        ctaTextColor      = Color.BLACK
    )
)
listNative.state.observe(this) { /* Ready / Loading / Failed / Empty */ }

// RecyclerView.Adapter.onBindViewHolder – for every ad row
listNative.bindTo(holder.adContainer, holder.shimmer)

// Activity.onDestroy
listNative.destroy()
```

### Native layout provider (required)

Your layouts use these view IDs – map them in `NativeLayoutProvider`:

| Layout ID | Purpose |
|-----------|---------|
| `ad_headline` | Headline |
| `ad_body` | Body |
| `ad_call_to_action` | CTA button |
| `icon` | App icon |
| `ad_media` | MediaView |
| `ad_stars` | RatingBar |
| `adText` | "Ad" badge |

```kotlin
nativeLayoutProvider = object : NativeLayoutProvider {
    override fun getLayoutRes(style: Int) = when (style) {
        2 -> R.layout.native_ad_advance
        3 -> R.layout.native_ad_large_advance
        else -> R.layout.native_ad_small
    }
    override fun getHeadlineId() = R.id.ad_headline
    override fun getBodyId() = R.id.ad_body
    override fun getCallToActionId() = R.id.ad_call_to_action
    override fun getIconId() = R.id.icon
    override fun getMediaId() = R.id.ad_media
    override fun getStarRatingId() = R.id.ad_stars
    override fun getAdBadgeId() = R.id.adText
}
```

Layouts stay in the **host app** (they depend on your fonts, sdp dims, drawables, colours). The library only receives resource IDs via the provider.

### App-Open

```kotlin
// In Application or splash
AdsLibrary.loadAppOpenAd(this, appOpenAdUnitId, withCallback = true) { loaded, _ ->
    if (loaded) {
        AdsLibrary.showAppOpenAd(this) { showed, dismissed, failed -> }
    }
}

// Lifecycle (call from every Activity or a base Activity)
override fun onPause() {
    super.onPause()
    AdsLibrary.onActivityPause()
}
override fun onResume() {
    super.onResume()
    AdsLibrary.onActivityResume(this)
}
```

---

## Main-Thread Callbacks

**Every public callback is delivered on the Android main/UI thread.**

You never need:

```kotlin
runOnUiThread { … }   // unnecessary
```

The library uses a central `MainThreadDispatcher` that posts only when the calling thread is not already the main thread.

---

## Cleanup

```kotlin
AdsLibrary.clearInterstitialPlacement("home")
AdsLibrary.clearAll()          // everything
```

When the user becomes premium the SDK already calls `clearAll()` internally.

---

## Publishing a New Version

### With JitPack

1. Push a git tag: `git tag v1.0.1 && git push origin v1.0.1`
2. JitPack builds automatically. Consumers update the version in their dependency.

### With GitHub Packages

1. Push a tag: `git tag v1.0.1 && git push origin v1.0.1`
2. The GitHub Action (`.github/workflows/publish.yml`) publishes the AAR.
3. Consumers update the version string.

---

## Migration Guide (from original classes)

| Original class / method                          | New API |
|--------------------------------------------------|---------|
| `Ads.isNetworkAvailableWithoutPremium()`         | `AdsLibrary.isNetworkAvailable()` + premium check inside SDK |
| `Preferences.isPremiumUser()`                    | Host calls `AdsLibrary.setPremiumUser()` |
| `Ads.showadsinapp`                               | `AdsLibrary.setAdsEnabled()` / `AdsConfig.adsEnabled` |
| `Ads.loadAndShowInterstitial(...)`               | `AdsLibrary.loadAndShowInterstitial(...)` |
| `Ads.preloadInterstitial(...)`                   | `AdsLibrary.preloadInterstitial(...)` |
| `Ads.showAnyCachedInterstitial(...)`             | `AdsLibrary.showAnyCachedInterstitial(...)` |
| `Ads.showInterstitialWithCounter(...)`           | `AdsLibrary.showInterstitialWithCounter(...)` |
| `Ads.loadAndShowRewarded(...)`                   | `AdsLibrary.loadAndShowRewarded(...)` |
| `AdBanner.adBanner(...)` / `loadbannersplash`    | `AdsLibrary.loadBanner(...)` |
| `NativeAdManager.preloadNativeAd(...)`           | `AdsLibrary.preloadNativeAd(...)` + `nativeAdLiveData` |
| `NativeAdManager.loadAndShowNativeAd(...)`       | `AdsLibrary.loadAndShowNativeAd(...)` |
| `WidgetNativeAd`                                 | Use `AdsLibrary.preloadNativeAd` + LiveData or `loadAndShowNativeAd` |
| `AppOpenAdManager.loadAppOpenAd(...)`            | `AdsLibrary.loadAppOpenAd(...)` |
| `AppOpenAdManager.showAppOpenAdForSplash(...)`   | `AdsLibrary.showAppOpenAd(...)` |
| `RemoteConfigManager.*`                          | Host supplies ad-unit IDs and feature flags |
| `ProgressUtils.show/dismiss`                     | Implement `ProgressProvider` |
| `logEvent` / Firebase                            | Implement `AnalyticsListener` |
| `R.layout.native_ad_*`                           | Implement `NativeLayoutProvider` |
| `TattooApp.openAdShownRecently`                  | Host can gate calls itself if needed |

### Classes removed / replaced

- `Ads` object → `AdsLibrary` + internal managers
- `AdBanner` → `BannerAdManager` (internal) + `AdsLibrary.loadBanner`
- `NativeAdManager` / `WidgetNativeAd` → unified `NativeAdManager` with LiveData
- `AppOpenAdManager` (different package) → unified `AppOpenAdManager`
- Direct `Preferences`, `RemoteConfigManager`, `ProgressUtils`, `R` references → host-supplied interfaces

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `IllegalStateException: AdsLibrary.initialize() must be called` | Call `initialize` in `Application.onCreate` |
| Native ads never show | Supply a `NativeLayoutProvider` that returns valid layout + view IDs |
| Callbacks not on main thread | Already guaranteed – check you are not wrapping them again |
| Ads still show after premium | Call `AdsLibrary.setPremiumUser(true)` – it clears caches |
| Offline requests hang | They fail immediately and invoke the failure callback |
| Banner / native stay visible after premium | `setPremiumUser(true)` clears containers; also hide your own ViewGroups |

---

## License

Apache 2.0 – free to use in commercial and open-source projects.
