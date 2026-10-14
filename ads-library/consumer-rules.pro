# Ads Library – consumer ProGuard / R8 rules

# Keep Next-Gen Mobile Ads SDK
-keep class com.google.android.libraries.ads.mobile.sdk.** { *; }
-dontwarn com.google.android.libraries.ads.mobile.sdk.**

# Keep library public API
-keep class com.xai.adslibrary.** { *; }
-keepclassmembers class com.xai.adslibrary.** { *; }

# LiveData observers
-keepclassmembers class * extends androidx.lifecycle.LiveData {
    <methods>;
}
