package com.xai.adslibrary.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe central premium-status store.
 * Host applications update this value; every ad operation consults it.
 */
internal object PremiumManager {

    private val premium = AtomicBoolean(false)

    fun isPremium(): Boolean = premium.get()

    fun setPremium(value: Boolean) {
        premium.set(value)
    }
}
