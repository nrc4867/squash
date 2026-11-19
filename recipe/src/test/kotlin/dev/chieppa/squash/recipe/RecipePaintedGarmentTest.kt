package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.dress.PaintStyle
import dev.chieppa.squash.character.dress.chartPlaces
import dev.chieppa.squash.character.dress.paintCommand
import dev.chieppa.squash.character.dress.placeOnBody
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetTuck
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A recipe's painted garments build as Dress paints them, and a garment painted in Dress is written as a recipe that builds into it. */
@Tag("integration")
class RecipePaintedGarmentTest {
    private val folder = TemplateFolder.shipped()
    private val builder = RecipeBuilder(folder)
    private val tee = builder.build(RecipeJson.read(File(System.getProperty("squash.recipes"), "painted-tee.recipe.json").readText()))

    @Test
    fun paintedAreasHolesAndColorsBuildFromTheirStrokes() {
        assertEquals(emptyList(), tee.warnings)
        val wardrobe = tee.document.character.wardrobe
        val shirt = checkNotNull(wardrobe.garment(GarmentId("tee")))
        assertEquals(listOf("body", "rib"), shirt.areas.map { it.id.value })
        assertEquals(SlotName("rib"), shirt.areas[1].color)
        assertEquals(setOf("torso", "arm-l", "neck"), shirt.branches.map { it.part.value }.toSet())
        assertTrue(shirt.branch(PartId("arm-l"))!!.to < 0.4f, "a short sleeve")
        val shorts = checkNotNull(wardrobe.garment(GarmentId("shorts")))
        assertEquals(BodyPartChange.Through(cuff = false), shorts.changes[PartId("tail")])
        assertEquals(listOf("tail"), shorts.holes.map { it.part.value })
        assertTrue(shorts.areas[0].cells.getValue(PartId("leg-l")).size > 4, "the side split leaves the leg's hem rows broken round")
    }

    @Test
    fun printsBuildOnTheBodyPartTheyNameAndExportAsTheyWereWritten() {
        val wardrobe = tee.document.character.wardrobe
        val shirt = checkNotNull(wardrobe.garment(GarmentId("tee")))
        assertEquals(listOf("stripe", "drop", "leaf"), shirt.regions.map { it.id.value })
        assertTrue(shirt.regions.all { region -> region.border.all { it.part == PartId("torso") } })
        assertEquals(listOf(false, true, true), shirt.regions.map { it.ink })
        assertEquals(setOf(4), shirt.regions[1].corners, "the drop's point")
        val shorts = checkNotNull(wardrobe.garment(GarmentId("shorts")))
        val stitch = shorts.marks.single()
        assertTrue(stitch.mirrored && stitch.width.stops.all { it.scale < 1f }, "a thin stitch on both pockets")
        assertEquals(SlotName("thread"), stitch.slot)
        val exported = exportRecipe(tee.document).recipe.wardrobe!!.garments
        assertEquals(listOf("torso"), exported.flatMap { g -> g.regions.map { it.on } + g.marks.map { it.on } }.distinct())
    }

    @Test
    fun aGarmentPaintedInDressExportsAndBuildsIntoItself() {
        val character = tee.document.character
        val body = assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, folder.plans.firstOrNull { it.ref == character.bodyPlan }, TemplateLibrary(folder.parts, folder.plans))
        val shorts = checkNotNull(character.wardrobe.garment(GarmentId("shorts")))
        val pocket = checkNotNull(placeOnBody(body, Anchor(PartId("torso"), BODY_PATCH, 0.25f, 0.1f)))
        val stroke = chartPlaces(character, body).within(pocket, 0.06f)
        var document = character.paintCommand(body, shorts, GarmentAreaId("pocket"), stroke, PaintStyle()).apply(tee.document).document
        document = SetTuck(GarmentId("shorts"), PartId("tail"), TuckKind.TAIL, false).apply(document).document
        val painted = checkNotNull(document.character.wardrobe.garment(GarmentId("shorts")))
        assertEquals(3, painted.areas.size)
        val exported = exportRecipe(document)
        assertEquals(emptyList(), exported.notCarried)
        val again = builder.build(RecipeJson.read(RecipeJson.write(exported.recipe)))
        assertEquals(painted, again.document.character.wardrobe.garment(GarmentId("shorts")))
        assertEquals(document.character.wardrobe, again.document.character.wardrobe)
    }
}
