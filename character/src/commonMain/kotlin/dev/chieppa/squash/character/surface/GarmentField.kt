package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember

/** How wide, in its garment's ease, the blend a cuff over a part outside the body's field joins the garment by is at least. */
private const val CUFF_BLEND_EASES = 2f

/**
 * A garment's field on a body's [field]: the first runs of the parts [worn] covers, whose (u, v) its branches read, each grown by its branch's ease and joined to the run it
 * grows from as the body joins them, where that run is covered too; a branch with a shell has its part's first run read as the shell's
 * solid, grown already, joined as the run was, its u and v the part's; a part that is not in the body's field but wears a cuff of the garment, as an ear does,
 * has its run swept by [solids] and joined to the garment's run over its socket parent nearest its socket, by a blend at least
 * [CUFF_BLEND_EASES] of its ease, so a cuff over a thin part flows into the garment. Null when it covers no run.
 * Each run it covers only part of is kept by [clips] to round what it covers. Its runs keep their parts' own (u, v), so each of its
 * vertices knows where on the body it grows from.
 */
fun garmentField(field: CharacterField, worn: WornCover, body: AssembledCharacter, solids: RunSolids, clips: RunClips): CharacterField? {
    val kept = field.runs.indices.filter { field.runs[it].first && worn.branchOf(field.runs[it].partId) != null }
    val renumbered = HashMap<Int, Int>().also { map -> kept.forEachIndexed { i, run -> map[run] = i } }
    val runs = ArrayList<FieldRun>()
    val members = ArrayList<JoinedMember>()
    for (run in kept) {
        val at = field.runs[run]
        val member = field.field.members[run]
        val parent = renumbered[member.parent] ?: -1
        val shell = worn.shells[at.partId]?.takeIf { at.first }
        runs += at
        members += if (shell != null) {
            JoinedMember(solids.of(shell.surface.patches.first()), shell.origin, shell.mirrored, member.blend, parent, member.part)
        } else {
            val branch = checkNotNull(worn.branchOf(at.partId))
            val solid = clips.of(member.solid, at.patch, branch.from, branch.to, field.cell)
            JoinedMember(solid, member.origin, member.mirrored, member.blend, parent, member.part, member.ease + branch.ease)
        }
    }
    var nextPart = (field.field.members.maxOfOrNull { it.part } ?: -1) + 1
    for (cuffed in worn.cuffs) {
        if (cuffed in field.parts) continue
        val placed = body.part(cuffed) ?: continue
        val index = placed.surface.patches.indexOfFirst(::isRun)
        if (index < 0) continue
        val patch = placed.surface.patches[index]
        val solid = clips.of(solids.of(patch), patch, checkNotNull(worn.branchOf(cuffed)).from, checkNotNull(worn.branchOf(cuffed)).to, field.cell)
        val parentId = placed.socket?.parent
        val parent = runs.indices.filter { runs[it].partId == parentId }
            .minByOrNull { members[it].own(placed.origin) } ?: -1
        val ease = checkNotNull(worn.branchOf(cuffed)).ease
        runs += FieldRun(placed, index, patch, true)
        val blend = if (parent < 0) 0f else maxOf(socketBlend(placed, solid), ease * CUFF_BLEND_EASES)
        members += JoinedMember(solid, placed.origin, placed.mirrored, blend, parent, nextPart++, ease)
    }
    if (runs.isEmpty()) return null
    return CharacterField(JoinedField(members), runs, field.cell)
}

/** The members of [body]'s parts in [parts], each placed as it is: those of [field]'s runs, and each other part's first swept run. */
fun membersOf(parts: Set<PartId>, field: CharacterField, body: AssembledCharacter, solids: RunSolids): List<JoinedMember> =
    parts.flatMap { id ->
        val inField = field.runs.indices.filter { field.runs[it].partId == id }.map { field.field.members[it] }
        inField.ifEmpty {
            val placed = body.part(id) ?: return@ifEmpty emptyList()
            val patch = placed.surface.patches.firstOrNull(::isRun) ?: return@ifEmpty emptyList()
            listOf(JoinedMember(solids.of(patch), placed.origin, placed.mirrored, 0f))
        }
    }
