package dev.chieppa.squash.document

/**
 * A pair's shared anchors read as its left side's. An anchor on a sided part follows the reader to the twin on its side, with
 * v unchanged since that twin is mirrored already. An anchor on a part in the middle of the body reads 1 - v from the right.
 * An anchor on a part with no twin, or on no part, stays as it is.
 */
internal fun Character.anchorReadBy(anchor: Anchor, side: Side?): Anchor {
    val target = part(anchor.part) ?: return anchor
    return when {
        side == null -> anchor
        target.side == null -> if (side == Side.RIGHT) anchor.copy(v = 1f - anchor.v) else anchor
        else -> twinOn(target, side)?.let { anchor.copy(part = it.id) } ?: anchor
    }
}

/** The other side of the body from [mark], which is [mark] with its anchors across the middle or on their twins and what it floats through turned over. */
fun Character.mirrorOf(mark: Mark): Mark = mark.mapAnchors { anchor ->
    val target = part(anchor.part) ?: return@mapAnchors anchor
    when (target.side) {
        null -> anchor.copy(v = 1f - anchor.v)
        Side.LEFT -> twinOn(target, Side.RIGHT)?.let { anchor.copy(part = it.id) } ?: anchor
        Side.RIGHT -> twinOn(target, Side.LEFT)?.let { anchor.copy(part = it.id) } ?: anchor
    }
}.copy(id = MarkId(mark.id.value + MIRROR_SUFFIX), mirrored = false, flight = mark.flight.map { it.copy(across = -it.across) })

private const val MIRROR_SUFFIX = "~mirror"

/** An anchor as the right side sees it, turned to how its pair holds it. */
private fun Character.anchorHeldFromRight(anchor: Anchor): Anchor {
    val target = part(anchor.part) ?: return anchor
    return when (target.side) {
        null -> anchor.copy(v = 1f - anchor.v)
        else -> twinOn(target, Side.LEFT)?.let { anchor.copy(part = it.id) } ?: anchor
    }
}

private fun Character.twinOn(part: PartInstance, side: Side): PartInstance? =
    if (part.pair == null) null else parts.firstOrNull { it.pair == part.pair && it.side == side }

/** What a write to [part] does to the anchors it is given, so that a pair holds them as its left side reads them. */
internal fun Character.writeTransform(part: PartInstance, thisSide: Boolean, stored: Boolean): (Anchor) -> Anchor =
    if (stored || thisSide || part.pair == null || part.side != Side.RIGHT) { anchor -> anchor } else ::anchorHeldFromRight

/** The pair's [shared] values as [part] reads them. */
internal fun Character.sharedReadBy(shared: PartData, part: PartInstance): PartData =
    shared.copy(surface = shared.surface.mapAnchors { anchorReadBy(it, part.side) })

/** The marks of [group] in [expression] that [part] shows, by role. */
fun Character.expressionMarks(expression: ExpressionSet, part: PartInstance, group: MarkGroupId): Map<MarkRole, List<Mark>> =
    expression.marks[group]?.let { expressionMarks(it, part) }.orEmpty()

/** What [part] shows of [entry], by role: the shared marks read for its side, with its side's on top. */
internal fun Character.expressionMarks(entry: RoleMarks, part: PartInstance): Map<MarkRole, List<Mark>> {
    val shared = if (part.pair == null) entry.shared else entry.shared.mapValues { (_, marks) ->
        marks.map { mark -> mark.mapAnchors { anchorReadBy(it, part.side) } }
    }
    return shared + entry.ofSide(part.side)
}
