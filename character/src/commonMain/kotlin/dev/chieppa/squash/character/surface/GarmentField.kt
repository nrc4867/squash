package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember

/**
 * How much narrower, in its ease, the blend a garment's run joins the run it grows from by is than the body's: the garment's surface is the
 * body's grown by the ease, which closes gaps narrower than twice it, so a blend narrowed by four eases closes no gap the body's blend
 * leaves open, and a sleeve stays apart from the body under the arm.
 */
private const val NARROWING_EASES = 4f

/** The narrowest a garment's run's blend into the run it grows from is, in its ease, so the two still meet along a join. */
private const val NARROWEST_EASES = 0.5f

/** How wide, in its garment's ease, the blend a cuff over a part outside the body's field joins the garment by is at least. */
private const val CUFF_BLEND_EASES = 2f

/** How many of its cells a garment's narrowest band spans at least: a garment narrower than that at the body's cell meshes finer. */
private const val BAND_CELLS = 4f

/**
 * A garment's field on a body's [field]: the first runs of the parts [worn] covers, whose (u, v) its branches read, each grown by its branch's ease and joined to the run it
 * grows from as the body joins them, where that run is covered too; a branch with a shell adds the shell's solid, grown already, its u
 * and v the part's, joined to the rest by a plain union, so the garment stands off the shell where it is drawn looser and off the run
 * where the shell would lie under the skin; a part that is not in the body's field but wears a cuff of the garment, as an ear does,
 * has its run swept by [solids] and joined to the garment's run over its socket parent nearest its socket, by a blend at least
 * [CUFF_BLEND_EASES] of its ease, so a cuff over a thin part flows into the garment. Null when it covers no run.
 * Each run it covers only part of is kept by [clips] to round what it covers, its cloth's [ClothReach]: the painted rows rounded out to
 * steps, so a small stroke keeps the same solid. Its runs keep their parts' own (u, v), so each of its
 * vertices knows where on the body it grows from. It meshes at the cell of the finest run it covers, no finer than [DETAIL_LEVEL] (see
 * [digitLevels]), so a shoe or a hood is as fine as the foot or the head under it, halved again up to [DIGIT_LEVEL] times from the body's
 * cell while its narrowest band, along a part or round it, spans fewer than [BAND_CELLS] cells, so a belt or a strap keeps its width.
 */
fun garmentField(field: CharacterField, worn: WornCover, body: AssembledCharacter, solids: RunSolids, clips: RunClips): CharacterField? {
    val kept = field.runs.indices.filter { field.runs[it].first && worn.branchOf(field.runs[it].partId) != null && worn.clothOf(field.runs[it].partId) != null }
    val renumbered = HashMap<Int, Int>().also { map -> kept.forEachIndexed { i, run -> map[run] = i } }
    val runs = ArrayList<FieldRun>()
    val members = ArrayList<JoinedMember>()
    val shells = ArrayList<Pair<FieldRun, JoinedMember>>()
    for (run in kept) {
        val at = field.runs[run]
        val member = field.field.members[run]
        val parent = renumbered[member.parent] ?: -1
        val branch = checkNotNull(worn.branchOf(at.partId))
        val reach = checkNotNull(worn.clothOf(at.partId)).reach
        val solid = clips.of(member.solid, at.patch, reach.from, reach.to, field.cell)
        runs += at
        val ease = member.ease + branch.ease
        members += JoinedMember(solid, member.origin, member.mirrored, maxOf(member.blend - ease * NARROWING_EASES, ease * NARROWEST_EASES), parent, member.part, ease)
        worn.shells[at.partId]?.let { shell -> shells += at to JoinedMember(solids.of(shell.surface.patches.first()), shell.origin, shell.mirrored, 0f, -1, member.part) }
    }
    for ((at, shell) in shells) {
        runs += at
        members += shell
    }
    var nextPart = (field.field.members.maxOfOrNull { it.part } ?: -1) + 1
    for (cuffed in worn.cuffs) {
        if (cuffed in field.parts) continue
        val placed = body.part(cuffed) ?: continue
        val index = firstRunIndex(placed.surface.patches)
        if (index < 0) continue
        val patch = placed.surface.patches[index]
        val band = worn.clothOf(cuffed)?.reach ?: continue
        val solid = clips.of(solids.of(patch), patch, band.from, band.to, field.cell)
        val parentId = placed.socket?.parent
        val parent = runs.indices.filter { runs[it].partId == parentId }
            .minByOrNull { members[it].own(placed.origin) } ?: -1
        val ease = checkNotNull(worn.branchOf(cuffed)).ease
        runs += FieldRun(placed, index, patch, true)
        val blend = if (parent < 0) 0f else maxOf(socketBlend(placed, solid), ease * CUFF_BLEND_EASES)
        members += JoinedMember(solid, placed.origin, placed.mirrored, blend, parent, nextPart++, ease)
    }
    if (runs.isEmpty()) return null
    return CharacterField(JoinedField(members), runs, bandCell(field, worn, kept))
}

/**
 * The cell [worn] meshes at over [field]'s runs [kept]: that of the finest of them, no finer than [DETAIL_LEVEL], halved further while
 * [worn]'s narrowest band over them spans fewer than [BAND_CELLS] cells, a painted part's band being its painted cells'.
 */
private fun bandCell(field: CharacterField, worn: WornCover, kept: List<Int>): Float {
    val levels = digitLevels(field)
    var narrowest = Float.MAX_VALUE
    for (run in kept) {
        val at = field.runs[run]
        val band = bandOf(at.partId, worn)
        if (!band.partial && band.allRound) continue
        val scales = RunScales(at.patch)
        if (band.partial) narrowest = minOf(narrowest, band.along * scales.along(band.middle))
        if (!band.allRound) narrowest = minOf(narrowest, band.around * scales.around(band.middle))
    }
    var level = kept.maxOfOrNull { minOf(levels[it], DETAIL_LEVEL) } ?: 0
    var cell = field.cell / (1 shl level)
    while (level < DIGIT_LEVEL && narrowest < BAND_CELLS * cell) {
        cell /= 2f
        level++
    }
    return cell
}

/** How far a garment covers one part: along it in u about [middle], [along] where [partial], and round it [around] where not [allRound]. */
private class Band(val partial: Boolean, val along: Float, val middle: Float, val allRound: Boolean, val around: Float)

/** The band [worn] covers of [part]: its painted cells' reach. */
private fun bandOf(part: PartId, worn: WornCover): Band {
    val reach = checkNotNull(worn.clothOf(part)).reach
    val middle = (reach.firstRow + reach.lastRow + 1f) / 2f / CHART_ROWS
    return Band(reach.partial, reach.along, middle, reach.allRound, reach.around)
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
