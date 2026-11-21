package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.endPointsDown
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
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

/** How many times a garment's cells are halved from the body's at most where its cloth is drawn and a piece of it or a gap is that narrow. */
const val DRAWN_LEVEL = 3

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
 * cell while its narrowest band, along a part or round it, spans fewer than [BAND_CELLS] cells, so a belt or a strap keeps its width;
 * where its cloth is drawn, up to [DRAWN_LEVEL] times while its narrowest drawn piece or gap does, so a strap, a tie or the gap between two
 * cups is several cells across.
 * A covered run whose ease varies along or round it, or whose cloth hangs, is grown ring by ring by [grownRuns] past its branch's least ease (see
 * [GrownRuns]).
 */
fun garmentField(field: CharacterField, worn: WornCover, body: AssembledCharacter, solids: RunSolids, clips: RunClips, grownRuns: GrownRuns = GrownRuns()): CharacterField? {
    val kept = field.runs.indices.filter { field.runs[it].first && worn.branchOf(field.runs[it].partId) != null && worn.clothOf(field.runs[it].partId) != null }
    val near = joinedNear(field, worn, kept)
    val grown = (kept + near.keys).sorted()
    val renumbered = HashMap<Int, Int>().also { map -> grown.forEachIndexed { i, run -> map[run] = i } }
    val runs = ArrayList<FieldRun>()
    val members = ArrayList<JoinedMember>()
    val shells = ArrayList<Pair<FieldRun, JoinedMember>>()
    for (run in grown) {
        val at = field.runs[run]
        val member = field.field.members[run]
        val parent = renumbered[member.parent] ?: -1
        val branch = worn.branchOf(at.partId)
        val neighbour = near[run]
        val ease = member.ease + (neighbour?.ease ?: checkNotNull(branch).ease)
        val solid = if (neighbour != null) clips.of(member.solid, at.patch, neighbour.from, neighbour.to, field.cell)
        else checkNotNull(worn.clothOf(at.partId)).reach.let { reach ->
            val cloth = worn.eases[at.partId]
            val hang = worn.garment.hang?.takeIf { at.part.role in HANGING }
            val reachOut = (cloth?.most ?: checkNotNull(branch).ease) + (hang?.limit ?: 0f)
            val clear = if (cloth == null && hang == null) null else clearanceOf(field, worn, run, reachOut)
            val grownRun = grownRuns.of(member.solid, cloth, hang, checkNotNull(branch).ease, reach.from, reach.to, clear)
            val past = if (grownRun == null) 0f else (cloth?.most ?: branch.ease) - branch.ease + (hang?.limit ?: 0f)
            clips.of(grownRun ?: member.solid, at.patch, reach.from, reach.to, field.cell, past)
        }
        runs += at
        members += JoinedMember(solid, member.origin, member.mirrored, maxOf(member.blend - ease * NARROWING_EASES, ease * NARROWEST_EASES), parent, member.part, ease, member.angles)
        if (neighbour == null) worn.shells[at.partId]?.let { shell -> shells += at to JoinedMember(solids.of(shell.surface.patches.first()), shell.origin, shell.mirrored, 0f, -1, member.part) }
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
        val blend = if (parent < 0) 0f else maxOf(socketBlend(placed, solid, patch), ease * CUFF_BLEND_EASES)
        members += JoinedMember(solid, placed.origin, placed.mirrored, blend, parent, nextPart++, ease)
    }
    if (runs.isEmpty()) return null
    return CharacterField(JoinedField(members), runs, bandCell(field, worn, kept))
}

/**
 * What [field]'s run [run] keeps its grown cloth clear of: every other part's run whose box lies within [reach] of its own, a part [worn]
 * covers too taking half the gap, since its cloth grows toward it as well.
 */
private fun clearanceOf(field: CharacterField, worn: WornCover, run: Int, reach: Float): Clearance {
    val members = field.field.members
    val own = members[run]
    val near = { other: JoinedMember ->
        maxOf(other.low.x - own.high.x, own.low.x - other.high.x, other.low.y - own.high.y, own.low.y - other.high.y, other.low.z - own.high.z, own.low.z - other.high.z) < reach
    }
    val others = members.indices.filter { members[it].part != own.part && near(members[it]) }.map { j ->
        members[j] to if (worn.branchOf(field.runs[j].partId) != null) 0.5f else 1f
    }
    return Clearance(own.origin, own.mirrored, others)
}

/** How far along an uncovered part joined to a covered one, in its u, the garment grows over it from the join. */
private const val NEAR_JOIN_U = 0.25f

/** How many chart rows from a join a covered part's cloth reaches for the garment to grow over the uncovered part joined there. */
private const val NEAR_JOIN_ROWS = 4

/** The roles of the parts cloth hangs over: a body and the legs under it, never a sleeve. */
private val HANGING = setOf(Role.BODY, Role.SPINE, Role.LEG)

/** How far along a covered part from its root, in chart rows, its cloth starts at most for a garment whose ease varies to grow over the part it sits on near the join. */
private const val NEAR_PARENT_ROWS = 32

/** An uncovered run a garment grows over, from [from] to [to] along it, standing off it by [ease], the ease of the covered run it sits on. */
private class NearJoin(val from: Float, val to: Float, val ease: Float)

/**
 * The first runs of [field] that [worn] does not cover, each a part on a socket of one of its covered runs [kept] past where the covered
 * run's cloth ends, within [NEAR_JOIN_ROWS] rows of it, short of the run's end: a foot below a trouser hem just above the ankle. The
 * garment grows over the [NEAR_JOIN_U] of each by the join, as the body blends into it, so near the join it follows the body's blend
 * rather than the covered run alone, and the skin there never stands out through the cloth. The uncovered run's share cuts the cloth
 * there away, as any uncovered part's does. A part coming out through a hole is left to it, and so is one coming out through an end the
 * cloth reaches that does not point down at rest, which the cloth closes over (a neck at a torso's top); one coming out through a downward
 * end, a foot below a legging cut at the ankle, is grown over as any other, so the cloth at fit 0 follows the body's blend into it and the
 * skin never stands out ragged through the hem. Where the garment's ease over a covered run varies and its cloth starts within
 * [NEAR_PARENT_ROWS] rows of the run's root, the uncovered run it sits on is grown the same way over [NEAR_JOIN_U] either side of its
 * socket, so a snug collar on a neck widening into the shoulders follows the widening and the shoulders never stand out through it.
 */
private fun joinedNear(field: CharacterField, worn: WornCover, kept: List<Int>): Map<Int, NearJoin> {
    val near = HashMap<Int, NearJoin>()
    val covered = kept.toSet()
    val members = field.field.members
    val leftOut = worn.holes - worn.outThroughEnds
    for (k in kept) {
        val reach = worn.clothOf(field.runs[k].partId)?.reach ?: continue
        val ease = worn.branchOf(field.runs[k].partId)?.ease ?: continue
        for (c in field.runs.indices) {
            val child = field.runs[c]
            if (c in covered || !child.first || members[c].parent != k || child.partId in leftOut) continue
            val socket = child.part.socketSpec?.u ?: continue
            if (child.partId in worn.outThroughEnds && !field.runs[k].part.endPointsDown(high = socket >= 0.5f)) continue
            if (!reachesNear(reach, socket)) continue
            near.putIfAbsent(c, NearJoin(0f, NEAR_JOIN_U, ease))
        }
        val parent = members[k].parent
        val socket = field.runs[k].part.socketSpec?.u
        val varies = worn.eases[field.runs[k].partId] != null
        if (varies && parent >= 0 && parent !in covered && members[k].blend > 0f && socket != null && reach.firstRow < NEAR_PARENT_ROWS) {
            near.putIfAbsent(parent, NearJoin(socket - NEAR_JOIN_U, socket + NEAR_JOIN_U, ease))
        }
    }
    return near
}

/** Whether cloth [reach] ends short of [u], a socket's place on its part past its last row, by at most [NEAR_JOIN_ROWS] rows. */
private fun reachesNear(reach: ClothReach, u: Float): Boolean {
    val past = u * CHART_ROWS - (reach.lastRow + 1)
    return past >= 0f && past <= NEAR_JOIN_ROWS
}

/**
 * The cell [worn] meshes at over [field]'s runs [kept]: that of the finest of them, no finer than [DETAIL_LEVEL], halved further while
 * [worn]'s narrowest band over them spans fewer than [BAND_CELLS] cells, a painted part's band being its painted cells' and a drawn
 * part's also its narrowest drawn piece or gap, up to [DRAWN_LEVEL] halvings there and [DIGIT_LEVEL] elsewhere.
 */
private fun bandCell(field: CharacterField, worn: WornCover, kept: List<Int>): Float {
    val levels = digitLevels(field)
    var narrowest = Float.MAX_VALUE
    var finest = DIGIT_LEVEL
    for (run in kept) {
        val at = field.runs[run]
        val scales = RunScales(at.patch)
        worn.clothOf(at.partId)?.drawnNarrowest(scales::along, scales::around)?.let { drawn ->
            narrowest = minOf(narrowest, drawn)
            finest = DRAWN_LEVEL
        }
        val band = bandOf(at.partId, worn)
        if (!band.partial && band.allRound) continue
        if (band.partial) narrowest = minOf(narrowest, band.along * scales.along(band.middle))
        if (!band.allRound) narrowest = minOf(narrowest, band.around * scales.around(band.middle))
    }
    var level = kept.maxOfOrNull { minOf(levels[it], DETAIL_LEVEL) } ?: 0
    var cell = field.cell / (1 shl level)
    while (level < finest && narrowest < BAND_CELLS * cell) {
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
