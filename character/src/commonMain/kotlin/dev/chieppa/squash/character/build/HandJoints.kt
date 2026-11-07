package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters
import kotlin.math.roundToInt

private const val PREFIX = "joint."
private const val FLAG_ON = 1f

/**
 * Joints a person places on a part with the Move tool, held in that part's parameters as `joint.<n>.u`, `.hinge` (the place of its
 * hinge among x, y and z), `.low`, `.high` (its limits in degrees) and `.stretch`, so pairs, overrides, undo and saving treat them
 * like any other. Joint `<n>` is named `joint.<n>`, which the rig names its bone by. [asBuilt] adds them to the template a part
 * builds with, with a [TIP] at the part's end when it has none, so every hand-made joint owns the span to the next. See HLD:
 * data-model, Joints and landmarks by hand.
 */
object HandJoints {
    /** The joint a part with hand-made joints gains at its end when its template has none there, which carries what sits on the end. */
    const val TIP = "tip"

    /** The tip's limits, in degrees. */
    val TIP_LIMITS = Limits(-30f, 30f)

    /** The hinges a joint turns about, in the order its `.hinge` parameter counts them. */
    val HINGES: List<Hinge> = Hinge.entries

    /** How near each other, in u, two joints may sit, and how near the part's ends a hand-made one may go. */
    const val GAP = 0.05f

    /** The furthest a limit reaches either way, in degrees. */
    const val MAX_LIMIT = 180f

    /** The limits a new joint takes. */
    val DEFAULT_LIMITS = Limits(-40f, 40f)

    fun name(id: Int) = "$PREFIX$id"

    fun u(id: Int) = "${name(id)}.u"

    fun hinge(id: Int) = "${name(id)}.hinge"

    fun low(id: Int) = "${name(id)}.low"

    fun high(id: Int) = "${name(id)}.high"

    fun stretch(id: Int) = "${name(id)}.stretch"

    /** Whether parameter [name] holds a hand-made joint. */
    fun isHandJoint(name: String) = name.startsWith(PREFIX)

    /** The id of the hand-made joint called [joint], or null for any other joint. */
    fun idOf(joint: String): Int? = if (joint.startsWith(PREFIX)) joint.removePrefix(PREFIX).toIntOrNull() else null

    /** The ids of the hand-made joints [params] hold, in order. */
    fun ids(params: Map<String, Float>): List<Int> =
        params.keys.filter(::isHandJoint).mapNotNull { it.removePrefix(PREFIX).substringBefore('.').toIntOrNull() }.distinct().sorted()

    fun nextId(params: Map<String, Float>): Int = (ids(params).maxOrNull() ?: 0) + 1

    /** The hand-made joints [params] hold. */
    fun specs(params: Map<String, Float>): List<JointSpec> = ids(params).mapNotNull { id ->
        val u = params[u(id)] ?: return@mapNotNull null
        val hinge = HINGES.getOrElse(params[hinge(id)]?.roundToInt() ?: 0) { Hinge.X }
        val limits = Limits(params[low(id)] ?: DEFAULT_LIMITS.min, params[high(id)] ?: DEFAULT_LIMITS.max)
        JointSpec(name(id), u, null, hinge, limits, (params[stretch(id)] ?: 0f) != 0f)
    }

    /** The command that places joint [id] on [part] at [u], turning about [hinge] within [limits]: one undo step. */
    fun add(part: PartId, id: Int, u: Float, hinge: Hinge, limits: Limits, stretch: Boolean, thisSide: Boolean = false): SetParameters =
        SetParameters(part, values(id, hinge, limits, stretch) + (u(id) to u), thisSide)

    /** The command that gives joint [id] of [part] [hinge], [limits] and [stretch]: one undo step. */
    fun edit(part: PartId, id: Int, hinge: Hinge, limits: Limits, stretch: Boolean, thisSide: Boolean = false): SetParameters =
        SetParameters(part, values(id, hinge, limits, stretch), thisSide)

    /** The command that clears joint [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int, thisSide: Boolean = false): SetParameters =
        SetParameters(part, listOf(u(id), hinge(id), low(id), high(id), stretch(id)).associateWith { null }, thisSide)

    private fun values(id: Int, hinge: Hinge, limits: Limits, stretch: Boolean): Map<String, Float?> = mapOf(
        hinge(id) to HINGES.indexOf(hinge).toFloat(),
        low(id) to minOf(limits.min, limits.max),
        high(id) to maxOf(limits.min, limits.max),
        stretch(id) to if (stretch) FLAG_ON else null,
    )
}
