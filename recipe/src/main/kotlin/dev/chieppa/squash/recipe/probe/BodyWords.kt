package dev.chieppa.squash.recipe.probe

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

/** A component smaller than this share of a direction is left out of its name. */
private const val NAMED_SHARE = 0.3f

/**
 * [direction] in body words: +x forward, +y up, -z the character's left, +z its right, its largest components first, as "down" or
 * "up, left".
 */
fun bodyWords(direction: Vec3): String {
    val unit = direction.normalized()
    val parts = listOf(
        abs(unit.x) to if (unit.x > 0f) "forward" else "back",
        abs(unit.y) to if (unit.y > 0f) "up" else "down",
        abs(unit.z) to if (unit.z > 0f) "right" else "left",
    )
    return parts.filter { it.first >= NAMED_SHARE }.sortedByDescending { it.first }.joinToString(", ") { it.second }
}
