package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.AddMarks
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.FurRule
import dev.chieppa.squash.document.LineWeight
import dev.chieppa.squash.document.LineWeightId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetAreaFill
import dev.chieppa.squash.document.SetBaseSlot
import dev.chieppa.squash.document.SetFurRule
import dev.chieppa.squash.document.SetLineWeight
import dev.chieppa.squash.document.SetMark
import dev.chieppa.squash.document.SetMarkGroup
import dev.chieppa.squash.document.SetRegion
import dev.chieppa.squash.recipe.model.RecipeFur
import dev.chieppa.squash.recipe.model.RecipeFurPatch
import dev.chieppa.squash.recipe.model.RecipeMark
import dev.chieppa.squash.recipe.model.RecipePart
import kotlin.math.floor

/** What is drawn on [part], recipe entry [index], sent to [id], its left side when it is a pair. */
internal fun BuildContext.paintPart(index: Int, part: RecipePart, id: PartId) {
    val path = "$.parts[$index]"
    part.color?.let { send("$path.color", SetBaseSlot(id, slot("$path.color", it))) }
    part.areas.forEach { (area, slot) -> send("$path.areas.$area", SetAreaFill(id, area, slot("$path.areas.$area", slot))) }
    part.regions.forEachIndexed { i, r ->
        if (r.on != null) fail("$path.regions[$i].on", "on names the part only in a garment's regions; a part's own regions lie on it")
        val region = region("$path.regions[$i]", id, r)
        send("$path.regions[$i]", SetRegion(id, region.id, region))
    }
    part.marks.forEachIndexed { i, m ->
        if (m.on != null) fail("$path.marks[$i].on", "on names the part only in a garment's marks; a part's own marks lie on it")
        val mark = mark("$path.marks[$i]", id, m)
        send("$path.marks[$i]", SetMark(id, mark.id, mark))
    }
    part.groups.forEachIndexed { i, group ->
        val marks = document.character.resolved(id)?.surface?.marks?.map { it.id }.orEmpty().toSet()
        group.roles.forEachIndexed { r, role ->
            role.fill?.let { slot("$path.groups[$i].roles[$r].fill", it.value) }
            role.marks.filter { it !in marks }.forEach { fail("$path.groups[$i].roles[$r].marks", "no mark \"${it.value}\" on ${id.value}") }
        }
        send("$path.groups[$i]", SetMarkGroup(id, group.id, group))
    }
    part.fur?.let { fur(path, id, it) }
    part.lineWeights.forEachIndexed { i, w ->
        val p = "$path.lineWeights[$i]"
        val points = w.points.mapIndexed { j, point -> pair(p, "points[$j]", point, "[u, v]").let { (u, v) -> Anchor(id, w.patch, u, v) } }
        send(p, SetLineWeight(id, LineWeightId(w.id), guard(p) { LineWeight(LineWeightId(w.id), points, w.radius, w.amount) }))
    }
}

internal fun BuildContext.mark(path: String, part: PartId, mark: RecipeMark): Mark {
    val points = anchors(path, part, mark.patch, mark.points, mark.face, mark.anchors, "points")
    val slot = mark.slot?.let { slot("$path.slot", it) }
    return guard(path) {
        Mark(
        MarkId(mark.id), points, MarkWidth(mark.width), mark.ink, mark.cameraLocked, mark.versions.mapKeys { CameraBin(it.key) }, mark.kind,
            mark.flight, mark.mirrored, slot,
        )
    }
}

private fun BuildContext.fur(path: String, part: PartId, fur: RecipeFur) {
    if (fur.spacing != null || fur.length != null) {
        val rule = guard("$path.fur") { FurRule().let { FurRule(fur.spacing ?: it.spacingPixels, fur.length ?: it.lengthPixels) } }
        send("$path.fur", SetFurRule(part, rule))
    }
    fur.patches.forEachIndexed { i, patch -> send("$path.fur.patches[$i]", AddMarks(part, furRoots("$path.fur.patches[$i]", part, patch))) }
}

/** Fur stroke roots in staggered rows, as [RecipeFurPatch] says. */
private fun BuildContext.furRoots(path: String, part: PartId, patch: RecipeFurPatch): List<Mark> {
    if (patch.rows < 2 || patch.columns < 1) fail(path, "a fur patch has at least 2 rows and 1 column, not ${patch.rows} by ${patch.columns}")
    val (uFirst, uLast) = pair(path, "u", patch.u, "[from, to]")
    val (vFirst, vLast) = pair(path, "v", patch.v, "[from, to]")
    return (0 until patch.rows).flatMap { row ->
        (0 until patch.columns).map { column ->
            val stagger = if (row % 2 == 0) 0f else 0.5f
            val u = uFirst + (uLast - uFirst) * row / (patch.rows - 1)
            val v = vFirst + (vLast - vFirst) * (column + stagger) / patch.columns
            Mark(MarkId("${patch.name}-$row-$column"), listOf(Anchor(part, BODY_PATCH, u, v - floor(v))), kind = MarkKind.FUR)
        }
    }
}
