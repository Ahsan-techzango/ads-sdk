package com.xai.adslibrary.util

import android.os.Handler
import android.os.Looper

/**
 * Guarantees that every public callback is delivered on the Android main/UI thread.
 * Callers never need to wrap callbacks in runOnUiThread themselves.
 */
internal object MainThreadDispatcher {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Executes [block] on the main thread.
     * If already on the main thread the block runs immediately (no post).
     */
    fun dispatch(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                block()
            } catch (t: Throwable) {
                // Never let a callback crash the SDK process
                android.util.Log.e("AdsLibrary", "Callback threw: ${t.message}", t)
            }
        } else {
            mainHandler.post {
                try {
                    block()
                } catch (t: Throwable) {
                    android.util.Log.e("AdsLibrary", "Callback threw: ${t.message}", t)
                }
            }
        }
    }

    /**
     * Posts [block] after [delayMs] milliseconds on the main thread.
     * Returns the Runnable so the caller can cancel it if needed.
     */
    fun postDelayed(delayMs: Long, block: () -> Unit): Runnable {
        val runnable = Runnable {
            try {
                block()
            } catch (t: Throwable) {
                android.util.Log.e("AdsLibrary", "Delayed callback threw: ${t.message}", t)
            }
        }
        mainHandler.postDelayed(runnable, delayMs)
        return runnable
    }

    fun removeCallbacks(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    fun removeAllCallbacks() {
        mainHandler.removeCallbacksAndMessages(null)
    }
}
