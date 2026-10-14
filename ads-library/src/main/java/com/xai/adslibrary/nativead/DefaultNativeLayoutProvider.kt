package com.xai.adslibrary.nativead

import com.xai.adslibrary.R
import com.xai.adslibrary.core.NativeLayoutProvider

/**
 * Built-in layout provider that uses the XML layouts shipped inside the library.
 * Host apps no longer need to supply their own native layouts unless they want custom designs.
 *
 * style: 1 = small, 2 = advance, 3 = large
 */
internal object DefaultNativeLayoutProvider : NativeLayoutProvider {

    override fun getLayoutRes(style: Int): Int = when (style) {
        2 -> R.layout.ads_lib_native_advance
        3 -> R.layout.ads_lib_native_large
        else -> R.layout.ads_lib_native_small
    }

    override fun getHeadlineId(): Int = R.id.ad_headline
    override fun getBodyId(): Int = R.id.ad_body
    override fun getCallToActionId(): Int = R.id.ad_call_to_action
    override fun getIconId(): Int = R.id.icon
    override fun getMediaId(): Int = R.id.ad_media
    override fun getStarRatingId(): Int = R.id.ad_stars
    override fun getAdBadgeId(): Int = R.id.adText
}
