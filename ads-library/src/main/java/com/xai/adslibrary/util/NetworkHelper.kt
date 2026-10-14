package com.xai.adslibrary.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Independent internet connectivity helper.
 *
 * Checks NET_CAPABILITY_INTERNET and NET_CAPABILITY_VALIDATED.
 * A positive result means a validated internet connection is present;
 * it does NOT guarantee that the ad server is reachable.
 */
object NetworkHelper {

    /**
     * Returns true when the device has a validated internet connection.
     * Safe on all supported API levels (minSdk 24).
     */
    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false

            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false

            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * Returns true when any network transport is present (even if not validated).
     * Useful for distinguishing "airplane mode" from "captive portal".
     */
    @JvmStatic
    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (t: Throwable) {
            false
        }
    }
}
