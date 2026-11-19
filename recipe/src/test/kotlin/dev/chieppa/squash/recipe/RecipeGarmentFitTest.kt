package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CLOTH_INK_WEIGHT
import dev.chieppa.squash.recipe.build.RecipeBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A garment's fit, ink weight and hem roll: written through, a wrong one stopped at its path, and cloth in a see-through slot warned. */
class RecipeGarmentFitTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(garment: String, palette: String = """{"slot": "fur", "color": "#e6b36e"}, {"slot": "knit", "color": "#4a7fc1"}""") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [$palette],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}],
           "wardrobe": {"slots": [{"id": "body", "name": "Body"}], "garments": [$garment]}}"""

    private fun sweater(fields: String) =
        """{"id": "sweater", "name": "Sweater", "slot": "body", "color": "knit", $fields "areas": [{"id": "body", "paint": [{"on": "torso", "shape": "band", "u": [0.3, 0.9]}]}]}"""

    private fun error(text: String): RecipeException = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(text)) }

    @Test
    fun inkWeightAndHemRollAreWrittenAndTheInkWeightIsThinnerThanSkinWhenLeftOut() {
        val set = builder.build(RecipeJson.read(recipe(sweater(""""fit": 0.01, "inkWeight": 1.2, "hemRoll": 0.006,"""))))
        val garment = set.document.character.wardrobe.garments.single()
        assertEquals(1.2f, garment.inkWeight)
        assertEquals(0.006f, garment.hemRoll)
        val plain = builder.build(RecipeJson.read(recipe(sweater(""""fit": 0.01,""")))).document.character.wardrobe.garments.single()
        assertEquals(CLOTH_INK_WEIGHT, plain.inkWeight)
        assertTrue(plain.inkWeight < 1f)
        assertEquals(0f, plain.hemRoll)
    }

    @Test
    fun aNegativeFitPartFitOrEaseStopsAtItsPath() {
        assertEquals("$.wardrobe.garments[0].fit", error(recipe(sweater(""""fit": -0.01,"""))).path)
        assertEquals("$.wardrobe.garments[0].partFits.torso", error(recipe(sweater(""""fit": 0.01, "partFits": {"torso": -0.002},"""))).path)
        val cover = """{"id": "vest", "name": "Vest", "slot": "body", "covers": [{"part": "torso", "from": 0.3, "to": 0.9, "ease": -0.01}]}"""
        assertEquals("$.wardrobe.garments[0].covers[0].ease", error(recipe(cover)).path)
    }

    @Test
    fun anInkWeightOrHemRollOutOfRangeStopsAtItsPath() {
        assertEquals("$.wardrobe.garments[0].inkWeight", error(recipe(sweater(""""inkWeight": 3,"""))).path)
        assertEquals("$.wardrobe.garments[0].hemRoll", error(recipe(sweater(""""hemRoll": 0.5,"""))).path)
    }

    @Test
    fun clothInASeeThroughSlotWarns() {
        val palette = """{"slot": "fur", "color": "#e6b36e"}, {"slot": "knit", "color": "#4a7fc1", "opacity": 0.4}"""
        val built = builder.build(RecipeJson.read(recipe(sweater(""""fit": 0.01,"""), palette)))
        assertTrue(built.warnings.any { it.startsWith("$.wardrobe.garments[0].color:") && "opacity" in it }, built.warnings.toString())
    }
}
