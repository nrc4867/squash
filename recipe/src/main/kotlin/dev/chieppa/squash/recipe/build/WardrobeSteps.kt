package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.recipe.model.RecipeChange
import dev.chieppa.squash.recipe.model.RecipeGarment

/** The wardrobe's slots and garments, which the parts garments own need in place before them. */
internal fun BuildContext.garments() {
    val wardrobe = recipe.wardrobe ?: return
    wardrobe.slots.forEachIndexed { i, slot -> send("$.wardrobe.slots[$i]", AddGarmentSlot(slot)) }
    val bare = BareBody(this)
    wardrobe.garments.forEachIndexed { i, garment ->
        val path = "$.wardrobe.garments[$i]"
        send(path, AddGarment(garment(path, garment, bare)))
        if (paints(garment)) writeClothShape(path, GarmentId(garment.id), bare)
    }
}

/** The outfits and the one shown, once every garment's own parts are placed. */
internal fun BuildContext.outfits() {
    val wardrobe = recipe.wardrobe ?: return
    wardrobe.outfits.forEachIndexed { i, outfit ->
        val path = "$.wardrobe.outfits[$i]"
        outfit.picks.forEach { (slot, garment) ->
            val held = document.character.wardrobe.garment(garment) ?: fail("$path.picks.${slot.value}", "no garment \"${garment.value}\"")
            if (held.slot != slot) fail("$path.picks.${slot.value}", "garment ${garment.value} goes in slot ${held.slot?.value ?: "none"}, not ${slot.value}")
        }
        send(path, AddOutfit(outfit))
    }
    wardrobe.shown?.let { send("$.wardrobe.shown", ShowOutfit(OutfitId(it))) }
}

private fun BuildContext.garment(path: String, garment: RecipeGarment, bare: BareBody): Garment {
    val slot = garment.slot?.let { id ->
        if (recipe.wardrobe?.slots?.none { it.id.value == id } != false) fail("$path.slot", "no wardrobe slot \"$id\"")
        GarmentSlotId(id)
    }
    val branches = garment.covers.mapIndexed { i, cover ->
        val p = "$path.covers[$i]"
        val part = partOf("$p.part", cover.part)
        if (cover.from >= cover.to) fail(p, "a cover's from lies below its to: here ${cover.from} to ${cover.to}")
        if (cover.fromV >= cover.toV) fail(p, "a cover's fromV lies below its toV: here ${cover.fromV} to ${cover.toV}")
        val outlines = curves(p, cover.outlines)
        guard(p) { GarmentBranch(part, cover.from, cover.to, cover.ease, outlines, fromV = cover.fromV, toV = cover.toV) }
    }
    val changes = garment.changes.entries.associate { (name, change) ->
        val p = "$path.changes.$name"
        val part = partOf(p, name)
        part to change(p, part, change)
    }
    val joins = garment.joins.mapIndexed { i, name -> partOf("$path.joins[$i]", name) }.toSet()
    val color = garment.color?.let { slot("$path.color", it) }
    val partFits = garment.partFits.entries.associate { (name, fit) -> partOf("$path.partFits.$name", name) to fit }
    val (areas, holes, tucks) = if (paints(garment)) paintedCloth(path, garment, bare) else Triple(emptyList(), emptyList(), emptyList())
    val regions = garment.regions.mapIndexed { i, r -> "$path.regions[$i]".let { p -> region(p, printedOn(p, r.on, r.anchors), r) } }
    val marks = garment.marks.mapIndexed { i, m ->
        val p = "$path.marks[$i]"
        if (m.kind != MarkKind.LINE) fail("$p.kind", "a mark printed on cloth is a line, not ${m.kind.name.lowercase()}")
        mark(p, printedOn(p, m.on, m.anchors), m)
    }
    return guard(path) {
        Garment(
            id = GarmentId(garment.id),
            name = garment.name,
            slot = slot,
            layer = garment.layer,
            branches = branches,
            joins = joins,
            inkHem = garment.inkHem,
            color = color,
            changes = changes,
            looseEnds = garment.looseEnds,
            fit = garment.fit,
            partFits = partFits,
            tucks = tucks,
            areas = areas,
            holes = holes,
            regions = regions,
            marks = marks,
        )
    }
}

private fun BuildContext.change(path: String, part: PartId, change: RecipeChange): BodyPartChange = when (change) {
    RecipeChange.Hide -> BodyPartChange.Hide
    is RecipeChange.Through -> BodyPartChange.Through(change.cuff, change.cuffSize, change.inkCuff)
    is RecipeChange.Redraw -> BodyPartChange.Redraw(partData("$path.shape", part, change.shape))
    is RecipeChange.Swap -> {
        val template = template("$path.template", change.template, change.version)
        checkParams("$path.shape", template, shapeParams(change.shape.size, change.shape.lean, change.shape.outward, change.shape.params))
        BodyPartChange.Swap(template.ref, partData("$path.shape", part, change.shape))
    }
}

/** The body part a print at [path] lies on: the part [on] names, or the part its [anchors] start on. */
private fun BuildContext.printedOn(path: String, on: String?, anchors: List<Anchor>?): PartId = when {
    on != null -> partOf("$path.on", on)
    !anchors.isNullOrEmpty() -> anchors.first().part
    else -> fail("$path.on", "a print on cloth names the body part it lies on with on, or gives anchors")
}
