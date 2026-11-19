package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** One role of a group: whether its marks are inked, the slot that fills them if any, and the marks by id on the same part. */
@Serializable
data class GroupRole(
    val role: MarkRole,
    val ink: Boolean = true,
    val fill: SlotName? = null,
    val marks: List<MarkId> = emptyList(),
)

/** The shape a mouth's lip line takes from its drawing. */
@Serializable
enum class MouthShape { SMILE, FROWN, O, GRIMACE }

/**
 * A mouth's controls. [open] runs 0 closed to 1 open, [expressionBlend] 0 at the group's own marks to 1 at the shown expression's, [width] scales it
 * across, and [mouthShape] bends its lip line. See HLD: look, Mouth.
 */
@Serializable
@Doc("A mouth's controls.")
data class MarkControls(
    @Doc("How far the mouth opens: 0 closed, 1 open, and 1 when left out. Drives the head's jaw where its template has one and parts the lips on the surface either way: the inner, tongue and teeth roles show between the lip line and a lower lip below it.") @Range(min = 0.0, max = 1.0)
    val open: Float = 1f,
    @Doc("0 shows the group's own marks, 1 the shown expression's.") @Range(min = 0.0, max = 1.0)
    val expressionBlend: Float = 1f,
    @Doc("Scales the mouth across.") @Range(min = 0.2, max = 3.0)
    val width: Float = 1f,
    @Doc("Bends the lip line from its drawing: SMILE, FROWN, O or GRIMACE.")
    val mouthShape: MouthShape? = null,
)

/** Marks acting together, as a mouth does. [roles] run in draw order, first drawn first. */
@Serializable
data class MarkGroup(
    val id: MarkGroupId,
    val name: String,
    val roles: List<GroupRole>,
    val controls: MarkControls = MarkControls(),
) {
    init {
        val named = roles.map { it.role }
        require(named.size == named.toSet().size) { "Group ${id.value} names a role twice: $named" }
    }
}
