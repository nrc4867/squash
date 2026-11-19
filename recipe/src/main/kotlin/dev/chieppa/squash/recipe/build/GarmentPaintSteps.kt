package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.dress.PaintStyle
import dev.chieppa.squash.character.dress.PaintedArea
import dev.chieppa.squash.character.dress.PaintedGarment
import dev.chieppa.squash.character.dress.bodyChart
import dev.chieppa.squash.character.dress.chartPlaces
import dev.chieppa.squash.character.dress.middlePartsOf
import dev.chieppa.squash.character.dress.painted
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.character.dress.shapeCommands
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentHole
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.TuckChoice
import dev.chieppa.squash.recipe.model.RecipeGarment

/** The body as the recipe has built it so far, every part bare, assembled once for painting garments on. */
internal class BareBody(private val context: BuildContext) {
    val body: AssembledCharacter by lazy {
        val character = context.document.character
        val library = TemplateLibrary(context.templates.parts, context.templates.plans)
        assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, context.plan, library)
    }

    val places: BodyPlaces by lazy { BodyPlaces(chartPlaces(context.document.character, body), body) }
}

/** Whether [garment]'s cloth is painted, holed or tucked, so its shape is written from its cells as Dress writes it. */
internal fun paints(garment: RecipeGarment): Boolean = garment.areas.isNotEmpty() || garment.holes.isNotEmpty() || garment.tucks.isNotEmpty()

/**
 * [garment]'s areas, each laid stroke by stroke in order on the bare body's chart (see [painted]), and its holes and Tuck choices, read
 * at [path].
 */
internal fun BuildContext.paintedCloth(path: String, garment: RecipeGarment, bare: BareBody): Triple<List<GarmentArea>, List<GarmentHole>, List<TuckChoice>> {
    val character = document.character
    val chart = bodyChart(character, bare.body)
    val ids = garment.areas.mapIndexed { i, area -> GarmentAreaId(area.id).also { if (garment.areas.take(i).any { it.id == area.id }) fail("$path.areas[$i].id", "area \"${area.id}\" is named twice") } }
    var cloth = PaintedGarment(
        chart,
        garment.areas.mapIndexed { i, area -> PaintedArea(ids[i], area.name, SurfaceMask.empty(chart), area.color?.let { slot("$path.areas[$i].color", it) }) },
        emptyList(),
    )
    val middle = middlePartsOf(character)
    garment.areas.forEachIndexed { i, area ->
        area.paint.forEachIndexed { j, stroke ->
            val p = "$path.areas[$i].paint[$j]"
            if (stroke.clean !in 0f..1f) fail("$p.clean", "clean lies from 0 to 1, not ${stroke.clean}")
            val cells = strokeCells(p, stroke, chart, bare.places)
            if (cells.isEmpty) warn(p, "the stroke holds no cell's middle; a cell is 1/64 of u by 1/32 of v")
            cloth = painted(cloth, ids[i], cells, PaintStyle(erase = stroke.unpaint, clean = stroke.clean, mirrored = if (stroke.mirrored) middle else emptySet()))
        }
    }
    cloth.areas.forEachIndexed { i, area -> if (area.mask.isEmpty) warn("$path.areas[$i]", "the area holds no cells once every stroke is laid") }
    val holes = garment.holes.mapIndexed { i, hole ->
        val part = partOf("$path.holes[$i].part", hole.part)
        GarmentHole(chart.leaderOf(part), hole.cuff)
    }
    if (holes.distinctBy { it.part }.size != holes.size) fail("$path.holes", "two holes round one part")
    val tucks = garment.tucks.mapIndexed { i, tuck -> TuckChoice(chart.leaderOf(partOf("$path.tucks[$i].part", tuck.part)), tuck.kind, tuck.tucked) }
    if (garment.joins.isNotEmpty() && garment.areas.isNotEmpty()) warn("$path.joins", "a garment with areas inks the joins where two areas meet; these are not used")
    return Triple(cloth.savedAreas(), holes, tucks)
}

/**
 * Writes the shape of the garment [id], as the document holds it, from its painted cells: its branches' ranges, the parts that come out
 * through it or tuck inside it and its inked seams (see [shapeCommands]); warns of each of the recipe's own [changes] a part's cells
 * said otherwise of.
 */
internal fun BuildContext.writeClothShape(path: String, id: GarmentId, bare: BareBody) {
    val character = document.character
    val held: Garment = checkNotNull(character.wardrobe.garment(id))
    send(path, shapeCommands(character, bare.body, held, paintedOf(character, bare.body, held)))
    val written = checkNotNull(document.character.wardrobe.garment(id))
    held.changes.forEach { (part, change) ->
        if (written.changes[part] != change) warn("$path.changes.${part.value}", "the painted cells say ${written.changes[part]?.let { it::class.simpleName?.lowercase() } ?: "nothing"} of this part, which replaces it")
    }
}
