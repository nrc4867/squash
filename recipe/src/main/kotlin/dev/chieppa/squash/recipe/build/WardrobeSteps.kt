package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.DigitPatches
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
import dev.chieppa.squash.document.MOST_CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.MOST_CREASE_STRENGTH
import dev.chieppa.squash.document.MOST_HEM_ROLL
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
    garment.fit?.let { if (it < 0f) fail("$path.fit", "a fit is how far the cloth stands off the body, 0 or more: here $it") }
    if (garment.inkWeight !in 0f..MOST_CLOTH_INK_WEIGHT) fail("$path.inkWeight", "an ink weight lies from 0 to $MOST_CLOTH_INK_WEIGHT: here ${garment.inkWeight}")
    if (garment.hemRoll !in 0f..MOST_HEM_ROLL) fail("$path.hemRoll", "a hem roll lies from 0 to $MOST_HEM_ROLL: here ${garment.hemRoll}")
    listOf("creases" to garment.creases, "wrinkles" to garment.wrinkles, "drape" to garment.drape).forEach { (key, value) ->
        if (value !in 0f..MOST_CREASE_STRENGTH) fail("$path.$key", "a $key strength lies from 0 to $MOST_CREASE_STRENGTH: here $value")
    }
    val branches = garment.covers.mapIndexed { i, cover ->
        val p = "$path.covers[$i]"
        val part = partOf("$p.part", cover.part)
        if (cover.from >= cover.to) fail(p, "a cover's from lies below its to: here ${cover.from} to ${cover.to}")
        if (cover.fromV >= cover.toV) fail(p, "a cover's fromV lies below its toV: here ${cover.fromV} to ${cover.toV}")
        if (cover.ease < 0f) fail("$p.ease", "an ease is how far the cloth stands off the part, 0 or more: here ${cover.ease}")
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
    seeThroughCloth(path, garment)
    val partFits = garment.partFits.entries.associate { (name, fit) ->
        if (fit < 0f) fail("$path.partFits.$name", "a fit is how far the cloth stands off the part, 0 or more: here $fit")
        partOf("$path.partFits.$name", name) to fit
    }
    val (areas, holes, tucks) = if (paints(garment)) paintedCloth(path, garment, bare) else Triple(emptyList(), emptyList(), emptyList())
    val regions = garment.regions.mapIndexed { i, r -> "$path.regions[$i]".let { p -> region(p, printedOn(p, r.on, r.anchors), r).also { printOffDigits(p, it.border) } } }
    val marks = garment.marks.mapIndexed { i, m ->
        val p = "$path.marks[$i]"
        if (m.kind != MarkKind.LINE) fail("$p.kind", "a mark printed on cloth is a line, not ${m.kind.name.lowercase()}")
        mark(p, printedOn(p, m.on, m.anchors), m).also { printOffDigits(p, it.points) }
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
            inkWeight = garment.inkWeight,
            hemRoll = garment.hemRoll,
            creases = garment.creases,
            wrinkles = garment.wrinkles,
            drape = garment.drape,
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

/** Warns at [path] of a print placed on a digit's patch: cloth stops where a part's digits leave it, so a print there shows nowhere. */
private fun BuildContext.printOffDigits(path: String, anchors: List<Anchor>) {
    anchors.map { it.part to it.patch }.distinct().filter { (part, patch) -> DigitPatches.digitOf(templateOf(part), patch) != null }.forEach { (part, patch) ->
        warn("$path.patch", "cloth stops where ${part.value}'s digits leave it, so a print on \"$patch\" shows nowhere")
    }
}

/** Warns where [garment]'s cloth, at [path], is coloured in a palette slot with an opacity, which cloth draws whole. */
private fun BuildContext.seeThroughCloth(path: String, garment: RecipeGarment) {
    val seeThrough = recipe.palette.filter { it.opacity != null && it.opacity < 1f }.map { it.slot }.toSet()
    if (garment.color in seeThrough) warn("$path.color", "cloth draws whole: slot \"${garment.color}\" has an opacity, which only a part of its own outside the one surface draws at")
    garment.areas.forEachIndexed { i, area ->
        if (area.color in seeThrough) warn("$path.areas[$i].color", "cloth draws whole: slot \"${area.color}\" has an opacity, which only a part of its own outside the one surface draws at")
    }
}

/** The body part a print at [path] lies on: the part [on] names, or the part its [anchors] start on. */
private fun BuildContext.printedOn(path: String, on: String?, anchors: List<Anchor>?): PartId = when {
    on != null -> partOf("$path.on", on)
    !anchors.isNullOrEmpty() -> anchors.first().part
    else -> fail("$path.on", "a print on cloth names the body part it lies on with on, or gives anchors")
}
