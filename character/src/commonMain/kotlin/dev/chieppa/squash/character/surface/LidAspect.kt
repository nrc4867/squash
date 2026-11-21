package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.geom.LID_ASPECT

/** The narrowest and widest an opening is taken to be, its width for its height, as its lid mass measures distance. */
private const val LEAST_LID_ASPECT = 0.3f
private const val MOST_LID_ASPECT = 6f

private const val TINY = 1e-9f

/** How many times as wide as it is high [look]'s opening lies where its chart runs [along] and [around] body units a unit, held within reason. */
internal fun lidAspect(look: EyeLook, along: Float, around: Float): Float {
    val high = look.height * around
    return if (high <= TINY) LID_ASPECT else (look.width * along / high).coerceIn(LEAST_LID_ASPECT, MOST_LID_ASPECT)
}
