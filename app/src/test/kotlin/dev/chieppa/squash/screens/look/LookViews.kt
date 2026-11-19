package dev.chieppa.squash.screens.look

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.smoke.probeAzimuth

/** The back view's azimuth, round from the front. */
private const val BACK_AZIMUTH = 180f

/**
 * The top view's direction toward the viewer: straight down from above, leant a little behind so the character's front is up in the
 * image and its right on the image's right.
 */
private val FROM_ABOVE = Vec3(-0.02f, 1f, 0f)

/** The back and top views, which the image `<outfit>-back-top` holds beside each other. */
val BACK_AND_TOP: List<Vec3> = listOf(probeAzimuth(BACK_AZIMUTH), FROM_ABOVE)

/** The suffix of the image holding the back and top views. */
const val BACK_TOP_SUFFIX = "-back-top"
