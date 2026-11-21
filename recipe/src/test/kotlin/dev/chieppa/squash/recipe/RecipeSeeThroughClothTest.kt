package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.NetPattern
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sheer and net cloth areas: written through, carried by export, a net out of range stopped at its path, and a see-through slot taken without a warning. */
class RecipeSeeThroughClothTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(areas: String, palette: String = """{"slot": "fur", "color": "#e6b36e"}, {"slot": "nylon", "color": "#202024"}""") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [$palette],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}],
           "wardrobe": {"slots": [{"id": "body", "name": "Body"}], "garments": [
             {"id": "sock", "name": "Sock", "slot": "body", "color": "nylon", "areas": [$areas]}]}}"""

    private fun area(id: String, from: Float, to: Float, fields: String = "") =
        """{"id": "$id", $fields "paint": [{"on": "torso", "shape": "band", "u": [$from, $to]}]}"""

    @Test
    fun opacityAndNetAreWrittenOnTheirAreasAndExportedAgain() {
        val areas = listOf(
            area("sheer", 0.3f, 0.7f, """"opacity": 0.3,"""),
            area("net", 0.7f, 0.9f, """"net": {"pattern": "square", "hole": 0.05, "thread": 0.005, "angle": 10},"""),
            area("band", 0.9f, 1f),
        ).joinToString()
        val built = builder.build(RecipeJson.read(recipe(areas)))
        val held = built.document.character.wardrobe.garments.single().areas.associateBy { it.id.value }
        assertEquals(0.3f, held.getValue("sheer").opacity)
        assertEquals(ClothNet(NetPattern.SQUARE, 0.05f, 0.005f, 10f), held.getValue("net").net)
        assertNull(held.getValue("band").opacity)
        assertNull(held.getValue("band").net)
        val exported = exportRecipe(built.document).recipe.wardrobe?.garments?.single()?.areas.orEmpty().associateBy { it.id }
        assertEquals(0.3f, exported.getValue("sheer").opacity)
        assertEquals(NetPattern.SQUARE, exported.getValue("net").net?.pattern)
        assertEquals(0.005f, exported.getValue("net").net?.thread)
    }

    @Test
    fun aNetOrOpacityOutOfRangeStopsAtItsPath() {
        fun error(fields: String) = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe(area("leg", 0.3f, 0.9f, fields)))) }.path
        assertEquals("$.wardrobe.garments[0].areas[0].net.thread", error(""""net": {"hole": 0.02, "thread": 0.03},"""))
        assertEquals("$.wardrobe.garments[0].areas[0].net.hole", error(""""net": {"hole": 0.5},"""))
        assertEquals("$.wardrobe.garments[0].areas[0].opacity", error(""""opacity": 1.5,"""))
    }

    @Test
    fun clothInASeeThroughSlotIsSheerWithNoWarning() {
        val palette = """{"slot": "fur", "color": "#e6b36e"}, {"slot": "nylon", "color": "#202024", "material": {"preset": "cloth", "seeThrough": 0.4}}"""
        val built = builder.build(RecipeJson.read(recipe(area("leg", 0.3f, 0.9f), palette)))
        assertTrue(built.warnings.none { "opacity" in it }, built.warnings.toString())
    }
}
