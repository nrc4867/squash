package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * A group's marks in an expression, by role. [shared] is what a pair's sides share, or the whole of it for a part with no
 * side; [left] and [right] replace the roles they name on that side. Read through [Character.expressionMarks].
 */
@Serializable
data class RoleMarks(
    internal val shared: Map<MarkRole, List<Mark>> = emptyMap(),
    internal val left: Map<MarkRole, List<Mark>> = emptyMap(),
    internal val right: Map<MarkRole, List<Mark>> = emptyMap(),
) {
    internal fun ofSide(side: Side?): Map<MarkRole, List<Mark>> = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
        null -> emptyMap()
    }

    internal fun withSide(side: Side, marks: Map<MarkRole, List<Mark>>): RoleMarks = when (side) {
        Side.LEFT -> copy(left = marks)
        Side.RIGHT -> copy(right = marks)
    }
}

/** A hinge joint's angle in degrees, the joint named by part and joint name. */
@Serializable
data class JointAngle(val part: PartId, val joint: String, val degrees: Float)

/** The joints an expression sets, each part named for itself so that one ear can differ from the other. */
@Serializable
data class FacePose(val joints: List<JointAngle> = emptyList()) {
    init {
        val named = joints.map { it.part to it.joint }
        require(named.size == named.toSet().size) { "A face pose sets a joint twice: $named" }
    }
}

/** A named expression: the marks it gives each group, by role, and its [pose]. See HLD: look, Surface work rules. */
@Serializable
data class ExpressionSet(
    val id: ExpressionId,
    val name: String,
    val marks: Map<MarkGroupId, RoleMarks> = emptyMap(),
    val pose: FacePose = FacePose(),
)
