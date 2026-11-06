package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** One role of a group: whether its marks are inked, the slot that fills them if any, and the marks by id on the same part. */
@Serializable
data class GroupRole(
    val role: MarkRole,
    val ink: Boolean = true,
    val fill: SlotName? = null,
    val marks: List<MarkId> = emptyList(),
)

/** The shape of role [by] clips the marks of role [clipped]. */
@Serializable
data class RoleClip(val by: MarkRole, val clipped: MarkRole)

/** The shape a mouth's lip line takes from its drawing. */
@Serializable
enum class MouthShape { SMILE, FROWN, O, GRIMACE }

/**
 * A group's controls. [open] runs 0 closed to 1 open, [expressionBlend] 0 at the group's own marks to 1 at the shown expression's, [dilation] adds to the
 * pupil's size as a share of it, [gazeU] and [gazeV] offset in the group's own u and v, [width] scales a mouth across, and [mouthShape] bends its lip line.
 * See HLD: look, Eyes and gaze and Mouth.
 */
@Serializable
data class MarkControls(
    val open: Float = 1f,
    val expressionBlend: Float = 1f,
    val dilation: Float = 0f,
    val gazeU: Float = 0f,
    val gazeV: Float = 0f,
    val width: Float = 1f,
    val mouthShape: MouthShape? = null,
)

/** Marks acting together, as an eye or a mouth does. [roles] run in draw order, first drawn first. */
@Serializable
data class MarkGroup(
    val id: MarkGroupId,
    val name: String,
    val roles: List<GroupRole>,
    val clips: List<RoleClip> = emptyList(),
    val controls: MarkControls = MarkControls(),
) {
    init {
        val named = roles.map { it.role }
        require(named.size == named.toSet().size) { "Group ${id.value} names a role twice: $named" }
        clips.forEach { clip ->
            require(clip.by != clip.clipped) { "Group ${id.value} has ${clip.by} clipping itself" }
            require(clip.by in named && clip.clipped in named) { "Group ${id.value} clips ${clip.clipped} by ${clip.by}, holding $named" }
        }
    }
}
