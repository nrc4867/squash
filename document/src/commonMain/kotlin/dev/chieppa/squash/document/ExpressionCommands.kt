package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Adds, replaces or removes the expression [id]. It keeps its place when replaced; [index] places a new one, last if null. */
@Serializable
@SerialName("setExpression")
data class SetExpression(val id: ExpressionId, val expression: ExpressionSet?, val index: Int? = null) : DocumentCommand {
    init {
        require(expression == null || expression.id == id) { "Expression ${expression?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val character = document.character
        val old = character.expressions.locate { it.id == id }
        val next = character.expressions.withItem(expression, index) { it.id == id }
        return Applied(
            document.copy(character = character.copy(expressions = next)),
            SetExpression(id, old.value, old.restoreIndex),
        )
    }
}

/**
 * Sets what [role] of [group] draws in [expression], for [part]: on the pair for a paired part, or with [thisSide] as that
 * side's override alone. A part with a side and no pair keeps its own on that side, and a part with no side has one place.
 * Null clears the role, and clearing an override shows the pair's again. Anchors are given as the part's side sees them,
 * unless [stored] says they are as the pair holds them, as an undo's are.
 */
@Serializable
@SerialName("setExpressionMarks")
data class SetExpressionMarks(
    val expression: ExpressionId,
    val part: PartId,
    val group: MarkGroupId,
    val role: MarkRole,
    val marks: List<Mark>?,
    val thisSide: Boolean = false,
    val stored: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val character = document.character
        val set = character.requireExpression(expression)
        val entry = set.marks[group] ?: RoleMarks()
        val side = instance.side.takeIf { thisSide || instance.pair == null }
        val held = if (side == null) entry.shared else entry.ofSide(side)
        val transform = character.writeTransform(instance, thisSide, stored)
        val written = marks?.map { it.mapAnchors(transform) }
        val next = if (written == null) held - role else held + (role to written)
        val updated = if (side == null) entry.copy(shared = next) else entry.withSide(side, next)
        val groups = if (updated == RoleMarks()) set.marks - group else set.marks + (group to updated)
        return Applied(
            document.withExpression(set.copy(marks = groups)),
            SetExpressionMarks(expression, part, group, role, held[role], thisSide, stored = true),
        )
    }
}

/** Sets the joints [expression] moves. */
@Serializable
@SerialName("setFacePose")
data class SetFacePose(val expression: ExpressionId, val pose: FacePose) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val set = document.character.requireExpression(expression)
        return Applied(document.withExpression(set.copy(pose = pose)), SetFacePose(expression, set.pose))
    }
}

private fun Character.requireExpression(id: ExpressionId): ExpressionSet =
    requireNotNull(expressions.firstOrNull { it.id == id }) { "No expression ${id.value}" }

private fun SquashDocument.withExpression(set: ExpressionSet): SquashDocument =
    copy(character = character.copy(expressions = character.expressions.map { if (it.id == set.id) set else it }))
