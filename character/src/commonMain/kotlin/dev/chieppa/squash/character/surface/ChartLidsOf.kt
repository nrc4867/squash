package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LidPose
import dev.chieppa.squash.geom.ChartLids
import kotlin.math.PI
import kotlin.math.tan

/**
 * [look]'s lids standing at [pose] in its opening's chart: the closed line where the look puts it, its tilt read as the opening lies on the
 * head, [aspect] times as wide as it is high.
 */
internal fun chartLids(look: EyeLook, pose: LidPose, aspect: Float): ChartLids {
    val lids = look.lids
    val slope = tan(lids.closedTilt * PI.toFloat() / 180f) * aspect
    return ChartLids(pose.upperClose, pose.lowerRise, lids.closedAt, lids.closedBend, slope)
}
