package com.example.sampleads

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xai.adslibrary.core.AdsLibrary
import com.xai.adslibrary.nativead.ListNativeAdManager
import com.xai.adslibrary.nativead.NativeAdColors
import com.xai.adslibrary.nativead.NativeAdState

class MainActivity : AppCompatActivity() {

    private val interstitialId = "ca-app-pub-3940256099942544/1033173712"
    private val rewardedId = "ca-app-pub-3940256099942544/5224354917"
    private val bannerId = "ca-app-pub-3940256099942544/6300978111"
    private val nativeId = "ca-app-pub-3940256099942544/2247696110"
    private val appOpenId = "ca-app-pub-3940256099942544/9257395921"

    /** List-native helper – load once, bind to many rows. */
    private lateinit var listNative: ListNativeAdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ------------------------------------------------------------------
        // List native: load ONCE when activity is created
        // ------------------------------------------------------------------
        listNative = AdsLibrary.createListNativeAd(this)
        listNative.load(
            adUnitId = nativeId,
            style = 1,   // small – change to 2 or 3 for other layouts
            colors = NativeAdColors(
                backgroundColor   = Color.parseColor("#1E1D20"),
                adBadgeBackground = Color.parseColor("#FF3B30"),
                adBadgeTextColor  = Color.BLACK,
                ctaBackground     = Color.parseColor("#FFD60A"),
                ctaTextColor      = Color.BLACK,
                headlineTextColor = Color.WHITE,
                bodyTextColor     = Color.WHITE
            )
        )
        listNative.state.observe(this) { state ->
            when (state) {
                is NativeAdState.Ready ->
                    Toast.makeText(this, "List native ready – bind in adapter", Toast.LENGTH_SHORT).show()
                is NativeAdState.Loading -> Log.d("Sample", "List native loading…")
                is NativeAdState.Failed -> Log.d("Sample", "List native failed: ${state.message}")
                is NativeAdState.Empty -> { }
            }
        }

        // Demo: bind the same ad into the container (simulates list row)
        findViewById<Button>(R.id.btnNative).setOnClickListener {
            val c1 = findViewById<FrameLayout>(R.id.nativeContainer)
            listNative.bindTo(c1)
            // In a real adapter:
            // listNative.bindTo(holder.adContainer, holder.shimmer)
        }

        findViewById<Button>(R.id.btnInterstitial).setOnClickListener {
            AdsLibrary.loadAndShowInterstitial(
                activity = this,
                adUnitId = interstitialId,
                placement = "home"
            ) { loaded, failed, showed, dismissed ->
                Log.d("Sample", "Interstitial: loaded=$loaded failed=$failed showed=$showed dismissed=$dismissed")
            }
        }

        findViewById<Button>(R.id.btnRewarded).setOnClickListener {
            AdsLibrary.loadAndShowRewarded(
                activity = this,
                adUnitId = rewardedId
            ) { _, _, _, _, rewarded ->
                if (rewarded) Toast.makeText(this, "Reward earned!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnBanner).setOnClickListener {
            AdsLibrary.loadBanner(
                activity = this,
                bannerContainer = findViewById(R.id.bannerContainer),
                adUnitId = bannerId
            ) { loaded, failed ->
                Log.d("Sample", "Banner loaded=$loaded failed=$failed")
            }
        }

        findViewById<Button>(R.id.btnAppOpen).setOnClickListener {
            AdsLibrary.loadAppOpenAd(this, appOpenId, withCallback = true) { loaded, _ ->
                if (loaded) {
                    AdsLibrary.showAppOpenAd(this) { showed, _, _ ->
                        Log.d("Sample", "AppOpen showed=$showed")
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnPremium).setOnClickListener {
            AdsLibrary.setPremiumUser(true)
            Toast.makeText(this, "Premium ON – ads disabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        AdsLibrary.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        AdsLibrary.onActivityResume(this)
    }

    override fun onDestroy() {
        listNative.destroy()
        super.onDestroy()
    }
}
