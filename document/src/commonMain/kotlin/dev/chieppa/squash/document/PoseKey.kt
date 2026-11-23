package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The least and most a pose key scales a width or a blend by. */
const val MIN_KEY_WIDTH = 0.25f
const val MAX_KEY_WIDTH = 4f

/**
 * One place along a part's outline where a key scales its width: [x] along the outline, as its points read it, and the [scale] its
 * distance from the axis takes there.
 */
@Serializable
data class WidthScale(val x: Float, val scale: Float) {
    init {
        require(scale in MIN_KEY_WIDTH..MAX_KEY_WIDTH) { "A key scales a width from $MIN_KEY_WIDTH to $MAX_KEY_WIDTH, not $scale" }
    }
}

/**
 * How a key scales one view's outline of a part: the side of it above the axis by [plus] and the side below by [minus], each a run of
 * places along the outline in order of x, read smoothly between them and held level past either end; an empty run scales nothing.
 */
@Serializable
data class OutlineScale(
    @Doc("Scales of the side above the axis (+y), by x along the outline in order.")
    val plus: List<WidthScale> = emptyList(),
    @Doc("Scales of the side below the axis (-y), by x along the outline in order.")
    val minus: List<WidthScale> = emptyList(),
) {
    init {
        require(plus.zipWithNext().all { (a, b) -> a.x < b.x } && minus.zipWithNext().all { (a, b) -> a.x < b.x }) { "A key's width scales run in order of x" }
    }
}

/**
 * What a key changes on one [part]: each view's outline scaled across its axis by [widths], and the blend it joins its parent by scaled
 * by [blend]. A part's own settings stay where they are; the key's changes are read over them, so an edit of the part keeps them.
 */
@Serializable
data class PartChange(
    val part: PartId,
    val widths: Map<View, OutlineScale> = emptyMap(),
    @Range(min = 0.25, max = 4.0)
    val blend: Float = 1f,
) {
    init {
        require(blend in MIN_KEY_WIDTH..MAX_KEY_WIDTH) { "A key scales a blend from $MIN_KEY_WIDTH to $MAX_KEY_WIDTH, not $blend" }
    }

    /** Whether it changes nothing. */
    val isRest: Boolean get() = blend == 1f && widths.values.all { scale -> (scale.plus + scale.minus).all { it.scale == 1f } }
}

/**
 * A pose key: a correction of the body's shape the character takes in one pose, [pose], over the rest it is built in, and blends in as a
 * pose nears it by the angles of the bones the key's pose turns, falling to nothing at the rest. Its [changes] are read over the parts'
 * own settings, never in their place. See HLD: surface, Pose keys.
 */
@Serializable
data class PoseKey(
    val name: String,
    val pose: List<BonePose>,
    val changes: List<PartChange> = emptyList(),
) {
    init {
        require(pose.isNotEmpty() && pose.any { !it.isRest }) { "Pose key $name turns no bone" }
        require(changes.map { it.part }.toSet().size == changes.size) { "Pose key $name changes a part twice" }
    }
}

/** Sets the character's pose keys. */
@Serializable
@SerialName("setPoseKeys")
data class SetPoseKeys(val keys: List<PoseKey>) : DocumentCommand {
    init {
        require(keys.map { it.name }.toSet().size == keys.size) { "Two pose keys share a name" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> =
        Applied(document.copy(character = document.character.copy(poseKeys = keys)), SetPoseKeys(document.character.poseKeys))
}
