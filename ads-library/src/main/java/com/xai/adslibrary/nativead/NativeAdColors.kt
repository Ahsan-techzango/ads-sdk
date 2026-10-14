package com.xai.adslibrary.nativead

import androidx.annotation.ColorInt

/**
 * Optional color overrides applied when binding a native ad view.
 * Pass null for any field to keep the layout's default color / drawable.
 *
 * Example:
 * ```
 * NativeAdColors(
 *     backgroundColor   = Color.parseColor("#1E1D20"),
 *     adBadgeBackground = Color.parseColor("#FF0000"),
 *     ctaBackground     = Color.parseColor("#FFD700"),
 *     ctaTextColor      = Color.BLACK
 * )
 * ```
 */
data class NativeAdColors(
    /** Root / card background of the native ad view. */
    @ColorInt val backgroundColor: Int? = null,

    /** Background of the small "Ad" badge (id = adText). */
    @ColorInt val adBadgeBackground: Int? = null,

    /** Text color of the "Ad" badge. */
    @ColorInt val adBadgeTextColor: Int? = null,

    /** Background of the Call-To-Action button. */
    @ColorInt val ctaBackground: Int? = null,

    /** Text color of the Call-To-Action button. */
    @ColorInt val ctaTextColor: Int? = null,

    /** Headline text color. */
    @ColorInt val headlineTextColor: Int? = null,

    /** Body text color. */
    @ColorInt val bodyTextColor: Int? = null
)
