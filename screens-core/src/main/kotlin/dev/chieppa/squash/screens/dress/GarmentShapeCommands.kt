package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.surface.holds
import dev.chieppa.squash.document.ChartBox
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CoverPart
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetBranchAround
import dev.chieppa.squash.document.SetBranchRange
import dev.chieppa.squash.document.SetGarmentPaint
import dev.chieppa.squash.document.SetJoinInk
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.UncoverPart

/**
 * The commands that give [garment] [painted]'s areas and holes and write its branches, changes and inked joins from them, as one step:
 * a branch over each part the cloth reaches, its range along and round the part bounding what the cloth holds of it, kept where it had
 * one; a part on a socket inside the region the cloth's cells paint (see [holds]) that the cloth leaves out comes through bare, a hole's cuff being cloth of its own; the small parts
 * inside it tucked or shaped by [tucksOf]; and each part where two areas meet at its root inked as a seam. A branch running past an end
 * of its part keeps running past it while the cloth reaches that end. A change to a part the cloth says nothing of, now or before, is
 * kept, as a hood's to the hair under it; one the cloth said something of before goes with it. A garment left with no cloth covers nothing.
 */
fun shapeCommands(character: Character, body: AssembledCharacter, garment: Garment, painted: PaintedGarment): DocumentCommand {
    val set = SetGarmentPaint(garment.id, painted.savedAreas(), painted.holes)
    val worn = wornOf(character, body, garment, painted)
    val commands = ArrayList<DocumentCommand>()
    commands += set
    for (held in garment.branches) if (held.part !in worn.branches) commands += UncoverPart(garment.id, held.part)
    for ((part, box) in worn.branches) {
        val held = garment.branch(part)
        if (held == null) {
            commands += CoverPart(garment.id, GarmentBranch(part, box.from, box.to, fromV = box.fromV, toV = box.toV))
            continue
        }
        val from = if (box.from <= 0f) minOf(held.from, 0f) else box.from
        val to = if (box.to >= 1f) maxOf(held.to, 1f) else box.to
        if (held.from != from || held.to != to) commands += SetBranchRange(garment.id, part, from, to)
        if (held.fromV != box.fromV || held.toV != box.toV) commands += SetBranchAround(garment.id, part, box.fromV, box.toV)
    }
    val unsaid = garment.changes.keys - worn.said
    val kept = if (unsaid.isEmpty()) unsaid else unsaid - wornOf(character, body, garment, paintedOf(character, body, garment)).said
    for (part in garment.changes.keys + worn.changes.keys) {
        if (part in kept) continue
        val next = worn.changes[part]
        if (garment.changes[part] != next) commands += SetBodyPartChange(garment.id, part, next)
    }
    val joins = worn.seams.filterTo(HashSet()) { it in worn.branches }
    for (part in garment.joins + joins) {
        if (part !in worn.branches) continue
        if ((part in garment.joins) != (part in joins)) commands += SetJoinInk(garment.id, part, part in joins)
    }
    return Together(commands)
}

/** The branches, changes and seams a garment's cloth writes, and every part it [said] something of, cloth or not. */
private class WornShape(val branches: Map<PartId, ChartBox>, val changes: Map<PartId, BodyPartChange>, val seams: Set<PartId>, val said: Set<PartId>)

private fun wornOf(character: Character, body: AssembledCharacter, garment: Garment, painted: PaintedGarment): WornShape {
    val boxes = painted.boxes
    val tucks = tucksOf(character, body, garment, boxes)
    val changes = HashMap<PartId, BodyPartChange>()
    val hidden = tucks.filter { !it.kind.digits && it.tucked }.mapTo(HashSet()) { it.part }
    hidden.forEach { changes[it] = BodyPartChange.Hide }
    tucks.filter { it.kind.digits && it.tucked }.groupBy { it.part }.forEach { (part, items) ->
        changes[part] = BodyPartChange.Redraw(PartData(params = items.associate { digitParam(it.kind) to 0f }))
    }
    val branches = boxes.filterKeys { it !in hidden }
    for (part in painted.chart.parts) {
        val parent = part.parent ?: continue
        if (part.id in branches || part.id in hidden || parent !in branches) continue
        val reach = painted.reachOf(parent) ?: continue
        if (reach.holds(part.socketU, part.socketV)) changes[part.id] = BodyPartChange.Through(cuff = false)
    }
    val said = boxes.keys + tucks.map { it.part } + painted.holed
    return WornShape(branches, changes, painted.seams, said)
}
