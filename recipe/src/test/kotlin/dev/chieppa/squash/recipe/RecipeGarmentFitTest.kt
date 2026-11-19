package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.ClothHang
import dev.chieppa.squash.document.EaseKey
import dev.chieppa.squash.document.PartId
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
    fun easeHangDropAndGatherAreWrittenThrough() {
        val fields = """"eases": {"torso": [[0.3, 0.03], [0.7, 0.012, 0.02], [0.9, 0.01]]}, "hang": {"limit": 0.02, "flare": 0.1}, "drops": {"torso": 0.1},"""
        val garment = builder.build(RecipeJson.read(recipe(sweater(fields)))).document.character.wardrobe.garments.single()
        val profile = checkNotNull(garment.eases[PartId("torso")])
        assertEquals(listOf(EaseKey(0.3f, 0.03f), EaseKey(0.7f, 0.012f, 0.02f), EaseKey(0.9f, 0.01f)), profile.keys)
        assertEquals(ClothHang(0.02f, 0.1f), garment.hang)
        assertEquals(0.1f, garment.drops[PartId("torso")])
        val ribbed = """{"id": "sweater", "name": "Sweater", "slot": "body", "areas": [{"id": "body", "gather": 0.004, "paint": [{"on": "torso", "shape": "band", "u": [0.3, 0.9]}]}]}"""
        assertEquals(0.004f, builder.build(RecipeJson.read(recipe(ribbed))).document.character.wardrobe.garments.single().areas.single().gather)
    }

    @Test
    fun anEaseProfileOutOfOrderOrNegativeStopsAtItsPath() {
        assertEquals("$.wardrobe.garments[0].eases.torso[1]", error(recipe(sweater(""""eases": {"torso": [[0.5, 0.01], [0.2, 0.02]]},"""))).path)
        assertEquals("$.wardrobe.garments[0].eases.torso[0]", error(recipe(sweater(""""eases": {"torso": [[0.5, -0.01]]},"""))).path)
        assertEquals("$.wardrobe.garments[0].drops.torso", error(recipe(sweater(""""drops": {"torso": 0.9},"""))).path)
        assertEquals("$.wardrobe.garments[0].hang.flare", error(recipe(sweater(""""hang": {"limit": 0.02, "flare": 2},"""))).path)
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
