package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.KeyLight
import dev.chieppa.squash.document.MaterialFade
import dev.chieppa.squash.document.MaterialPreset
import dev.chieppa.squash.document.MaterialRim
import dev.chieppa.squash.document.SlotMaterial
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A slot's material in a recipe: a preset by name or with settings changed, checked at its path, and exported as the name alone where it changes nothing. */
class RecipeMaterialTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(slots: String, shading: String = "") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [{"slot": "fur", "color": "#e6b36e"}, $slots],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}]$shading}"""

    @Test
    fun aPresetByNameOrWithSettingsReachesTheSlot() {
        val built = builder.build(
            RecipeJson.read(recipe("""{"slot": "buckle", "color": "#c0c0c8", "material": "metal"}, {"slot": "gem", "color": "#40a0ff", "material": {"preset": "metal", "rim": {"width": 0.1}, "shadowSlot": "fur"}}""")),
        ).document.character.palette
        assertEquals(SlotMaterial(MaterialPreset.METAL), built.material(SlotName("buckle")))
        assertEquals(SlotMaterial(MaterialPreset.METAL, shadowSlot = SlotName("fur"), rim = MaterialRim(0.1f)), built.material(SlotName("gem")))
        assertEquals(built.color(SlotName("fur")), built.shadow(SlotName("gem")))
    }

    @Test
    fun aMaterialIsWrittenAsItsNameWhenItChangesNothing() {
        val built = builder.build(RecipeJson.read(recipe("""{"slot": "lamp", "color": "#ffe080", "material": "glow"}, {"slot": "lens", "color": "#203040", "material": {"preset": "glass", "steps": 1}}"""))).document
        val text = RecipeJson.write(exportRecipe(built).recipe)
        assertTrue("\"material\": \"glow\"" in text, text)
        assertTrue("\"steps\": 1" in text, text)
        assertEquals(built, builder.build(RecipeJson.read(text)).document)
    }

    @Test
    fun aWrongMaterialFailsAtItsPath() {
        val unknown = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"slot": "x", "color": "#ffffff", "material": "chrome"}"""))) }
        assertEquals("$.palette[1].material", unknown.path)
        val noSlot = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"slot": "x", "color": "#ffffff", "material": {"shadowSlot": "none"}}"""))) }
        assertEquals("$.palette[1].material.shadowSlot", noSlot.path)
        val tooMany = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"slot": "x", "color": "#ffffff", "material": {"steps": 4}}"""))) }
        assertEquals("$.palette[1].material", tooMany.path)
    }

    @Test
    fun aFadeReachesTheSlotRoundTripsAndNamesASlotThePaletteHolds() {
        val built = builder.build(
            RecipeJson.read(recipe("""{"slot": "tip", "color": "#302020"}, {"slot": "claw", "color": "#f0e8d8", "material": {"preset": "plastic", "fade": {"slot": "tip", "from": 0.4}}}""")),
        ).document
        assertEquals(MaterialFade(SlotName("tip"), from = 0.4f), built.character.palette.material(SlotName("claw"))?.fade)
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exportRecipe(built).recipe))).document)
        val noSlot = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"slot": "x", "color": "#ffffff", "material": {"fade": {"slot": "none"}}}"""))) }
        assertEquals("$.palette[1].material.fade.slot", noSlot.path)
    }

    @Test
    fun aKeyLightIsWrittenThroughAndExported() {
        val built = builder.build(RecipeJson.read(recipe("""{"slot": "x", "color": "#ffffff"}""", """, "shading": {"mode": "twoTone", "key": {"x": 0.2, "y": 1, "z": 0.4}}"""))).document
        assertEquals(KeyLight(0.2f, 1f, 0.4f), built.character.shading.key)
        assertEquals(0.4f, exportRecipe(built).recipe.shading?.key?.z)
    }
}
