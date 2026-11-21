package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.DigitPatches
import dev.chieppa.squash.document.MOST_FOLD
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.ClothHang
import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.EaseKey
import dev.chieppa.squash.document.EaseProfile
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.LEAST_NET_HOLE
import dev.chieppa.squash.document.LEAST_NET_THREAD
import dev.chieppa.squash.document.MOST_CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.MOST_CREASE_STRENGTH
import dev.chieppa.squash.document.MOST_DROP
import dev.chieppa.squash.document.MOST_HEM_ROLL
import dev.chieppa.squash.document.MOST_NET_ANGLE
import dev.chieppa.squash.document.MOST_NET_HOLE
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.recipe.model.RecipeChange
import dev.chieppa.squash.recipe.model.RecipeGarment
import dev.chieppa.squash.recipe.model.RecipeNet

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
    val partFits = garment.partFits.entries.associate { (name, fit) ->
        if (fit < 0f) fail("$path.partFits.$name", "a fit is how far the cloth stands off the part, 0 or more: here $fit")
        partOf("$path.partFits.$name", name) to fit
    }
    val eases = garment.eases.entries.associate { (name, keys) -> partOf("$path.eases.$name", name) to easeProfile("$path.eases.$name", keys) }
    val drops = garment.drops.entries.associate { (name, drop) ->
        if (drop !in 0f..MOST_DROP) fail("$path.drops.$name", "a seam drops from 0 to $MOST_DROP along its part: here $drop")
        partOf("$path.drops.$name", name) to drop
    }
    val hang = garment.hang?.let { hang ->
        if (hang.limit < 0f) fail("$path.hang.limit", "a hang's limit is how far the cloth may stand off, 0 or more: here ${hang.limit}")
        if (hang.flare !in 0f..ClothHang.MOST_FLARE) fail("$path.hang.flare", "a hang's flare lies from 0 to ${ClothHang.MOST_FLARE}: here ${hang.flare}")
        ClothHang(hang.limit, hang.flare)
    }
    garment.areas.forEachIndexed { i, area -> area.gather?.let { if (it < 0f) fail("$path.areas[$i].gather", "a gather is the ease a band pulls the cloth in to, 0 or more: here $it") } }
    val (painted, holes, tucks, shapes) = if (paints(garment)) paintedCloth(path, garment, bare) else PaintedCloth()
    garment.areas.forEachIndexed { i, area -> area.opacity?.let { if (it !in 0f..1f) fail("$path.areas[$i].opacity", "an opacity runs 0 to 1: here $it") } }
    garment.areas.forEachIndexed { i, area -> area.fold?.let { if (it !in 0f..MOST_FOLD) fail("$path.areas[$i].fold", "a fold stands out 0 to $MOST_FOLD over the cloth beside it: here $it") } }
    val areas = painted.mapIndexed { i, area -> garment.areas.getOrNull(i).let { area.copy(gather = it?.gather, fold = it?.fold, opacity = it?.opacity, net = it?.net?.let { net -> clothNet("$path.areas[$i].net", net) }) } }
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
            eases = eases,
            hang = hang,
            drops = drops,
            shapes = shapes,
        )
    }
}

/** The ease profile [keys] give at [path], each [u, ease] or [u, front, back], in order along u. */
private fun BuildContext.easeProfile(path: String, keys: List<List<Float>>): EaseProfile {
    if (keys.isEmpty()) fail(path, "an ease profile needs at least one key [u, ease]")
    val read = keys.mapIndexed { i, key ->
        if (key.size !in 2..3) fail("$path[$i]", "an ease key is [u, ease] or [u, front, back]: here ${key.size} numbers")
        if (key.drop(1).any { it < 0f }) fail("$path[$i]", "an ease is how far the cloth stands off the part, 0 or more: here $key")
        EaseKey(key[0], key[1], key.getOrNull(2))
    }
    read.zipWithNext().forEachIndexed { i, (a, b) -> if (b.u <= a.u) fail("$path[${i + 1}]", "ease keys run along u in order: ${b.u} follows ${a.u}") }
    return EaseProfile(read)
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

/** Warns at [path] of a print placed on a digit's patch: prints lie on the cloth of a part's first run, so a print there shows nowhere. */
private fun BuildContext.printOffDigits(path: String, anchors: List<Anchor>) {
    anchors.map { it.part to it.patch }.distinct().filter { (part, patch) -> DigitPatches.digitOf(templateOf(part), patch) != null }.forEach { (part, patch) ->
        warn("$path.patch", "prints lie on the cloth over ${part.value}'s own run, not its digits, so a print on \"$patch\" shows nowhere")
    }
}

/** [net], at [path], as the document's net, failing where its spacing, thread or angle is out of range. */
private fun BuildContext.clothNet(path: String, net: RecipeNet): ClothNet {
    if (net.hole !in LEAST_NET_HOLE..MOST_NET_HOLE) fail("$path.hole", "a net's threads lie $LEAST_NET_HOLE to $MOST_NET_HOLE apart: here ${net.hole}")
    if (net.thread < LEAST_NET_THREAD || net.thread >= net.hole) fail("$path.thread", "a thread is $LEAST_NET_THREAD wide or more, under its hole ${net.hole}: here ${net.thread}")
    if (net.angle !in 0f..MOST_NET_ANGLE) fail("$path.angle", "a net's angle lies 0 to $MOST_NET_ANGLE degrees: here ${net.angle}")
    return ClothNet(net.pattern, net.hole, net.thread, net.angle)
}

/** The body part a print at [path] lies on: the part [on] names, or the part its [anchors] start on. */
private fun BuildContext.printedOn(path: String, on: String?, anchors: List<Anchor>?): PartId = when {
    on != null -> partOf("$path.on", on)
    !anchors.isNullOrEmpty() -> anchors.first().part
    else -> fail("$path.on", "a print on cloth names the body part it lies on with on, or gives anchors")
}
