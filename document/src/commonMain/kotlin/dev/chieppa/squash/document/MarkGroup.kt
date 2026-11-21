package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** One role of a group: whether its marks are inked, the slot that fills them if any, the marks by id on the same part, and which of them draw over a closed lip. */
@Serializable
data class GroupRole(
    val role: MarkRole,
    val ink: Boolean = true,
    val fill: SlotName? = null,
    val marks: List<MarkId> = emptyList(),
    @Doc("Marks of this role that draw over the lips, open or closed, so fangs show over a shut mouth; the lip ink breaks under them. Teeth and tongue only.")
    val over: List<MarkId> = emptyList(),
)

/**
 * A mouth's controls: [open] and the six that shape it, which a spoken shape sets in place of them (see [MouthShapeName]) and [smile] adds to as a mood,
 * and [expressionBlend], 0 at the group's own marks to 1 at the shown expression's. See HLD: look, Mouth.
 */
@Serializable
@Doc("A mouth's controls.")
data class MarkControls(
    @Doc("How far the mouth opens: 0 closed, 1 open. Drives the head's jaw where its template has one and parts the lips on the surface either way: the inner, tongue and teeth roles show between the upper lip and a lower lip below it.") @Range(min = 0.0, max = 1.0)
    val open: Float = 0f,
    @Doc("Draws the corners out (1) or in (-1).") @Range(min = -1.0, max = 1.0)
    val wide: Float = 0f,
    @Doc("Puckers the mouth to a narrow, round opening.") @Range(min = 0.0, max = 1.0)
    val round: Float = 0f,
    @Doc("Lifts the corners (1) or drops them (-1); a mood a spoken shape's own smile adds to.") @Range(min = -1.0, max = 1.0)
    val smile: Float = 0f,
    @Doc("Raises the upper lip off the upper teeth.") @Range(min = 0.0, max = 1.0)
    val upperLip: Float = 0f,
    @Doc("Tucks the lower lip up under the upper teeth (1, as for F and V) or presses the lips together (-1, as for M, B and P).") @Range(min = -1.0, max = 1.0)
    val lowerLip: Float = 0f,
    @Doc("Lifts the tongue to the upper teeth by 0.5, then out past the lower lip by 1, as for a blep.") @Range(min = 0.0, max = 1.0)
    val tongue: Float = 0f,
    @Doc("0 shows the group's own marks, 1 the shown expression's.") @Range(min = 0.0, max = 1.0)
    val expressionBlend: Float = 1f,
) {
    /** These controls with [values] over them, a value left out keeping this one's, and [smile] added to the shape's as a mood. */
    fun speaking(values: MouthShapeValues): MarkControls = MarkControls(
        open = values.open ?: 0f,
        wide = values.wide ?: 0f,
        round = values.round ?: 0f,
        smile = ((values.smile ?: 0f) + smile).coerceIn(-1f, 1f),
        upperLip = values.upperLip ?: 0f,
        lowerLip = values.lowerLip ?: 0f,
        tongue = values.tongue ?: 0f,
        expressionBlend = expressionBlend,
    )

    /** Whether the lips hold their drawn line: no control but [open] moves them. */
    val drawnLine: Boolean get() = wide == 0f && round == 0f && smile == 0f && upperLip == 0f && lowerLip == 0f
}

/** Marks acting together, as a mouth does. [roles] run in draw order, first drawn first. */
@Serializable
data class MarkGroup(
    val id: MarkGroupId,
    val name: String,
    val roles: List<GroupRole>,
    val controls: MarkControls = MarkControls(),
    @Doc("The mouth style the group talks in, by preset name (lips, short-muzzle, long-muzzle, beak, wide-jaw, minimal); none takes its head template's.")
    val style: String? = null,
    @Doc("The spoken shapes this mouth changes from its style's, by name: each value set here replaces the style's.")
    val shapes: Map<MouthShapeName, MouthShapeValues> = emptyMap(),
) {
    init {
        val named = roles.map { it.role }
        require(named.size == named.toSet().size) { "Group ${id.value} names a role twice: $named" }
    }

    /** The marks of every role that draw over the lips. */
    val overMarks: Set<MarkId> get() = roles.flatMapTo(mutableSetOf()) { it.over }
}
