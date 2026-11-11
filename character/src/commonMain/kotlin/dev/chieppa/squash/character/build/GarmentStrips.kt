package dev.chieppa.squash.character.build

/**
 * [branch], a garment's branch all the way round a part whose start a strip joins to its parent, with that strip carried over it: the
 * branch's rings over the part's trimmed rings left out, and a strip of its own from its first ring past them to the body strip's foot,
 * standing as far off the host as the branch stands off the part there. So a sleeve follows the shoulder's strip, at rest and as the
 * pose builds the strip again, instead of its own root swinging out over the shoulder. Itself when the part has no such strip or the
 * branch starts past it.
 */
internal fun stripOver(branch: PlacedPart, body: AssembledCharacter): PlacedPart {
    val covering = branch.covering ?: return branch
    if (!covering.allRound) return branch
    val covered = body.part(covering.part) ?: return branch
    val tube = covered.surface.patches.getOrNull(covering.bodyPatch) ?: return branch
    val join = covered.strips.firstOrNull { it.atStart && it.tube == tube.name } ?: return branch
    val host = body.part(join.host) ?: return branch
    val shell = branch.surface.patches.firstOrNull() ?: return branch
    val first = (0 until shell.nu).firstOrNull { covering.bodyRings[it] > join.ring } ?: return branch
    if (first == 0 || first >= shell.nu - 1) return branch
    val under = covering.bodyRings[first]
    val ease = (0 until shell.nv).map { j ->
        (shell.point(shell.index(first, j)) - tube.point(tube.index(under, covering.bodyColumns[j].coerceAtMost(tube.nv - 1)))).length
    }.average().toFloat()
    val foot = List(shell.nv) { j -> join.foot[covering.bodyColumns[j].coerceAtMost(join.foot.size - 1)] }
    val own = StripJoin(StripJoin.patchName(true), shell.name, first, true, join.host, join.hostPatch, foot, join.joined, ease, capped = join.capped)
    val shape = stripShape(branch, own, host) ?: return branch
    return branch.withStrip(shape, own).let { it.alwaysHiding(trimmed(it, own)) }
}
