package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CharacterShading
import dev.chieppa.squash.document.ShadingLight
import dev.chieppa.squash.document.ShadingMode
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A recipe's shading: the defaults when left out, written through once given, a far side too deep stopped at its path, and exported. */
class RecipeShadingTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(shading: String = "") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [{"slot": "fur", "color": "#e6b36e"}],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}]$shading}"""

    @Test
    fun shadingIsWrittenThroughCheckedAndExported() {
        assertEquals(CharacterShading(), builder.build(RecipeJson.read(recipe())).document.character.shading)
        val built = builder.build(RecipeJson.read(recipe(""", "shading": {"mode": "twoTone", "farSide": 0.35, "light": "body"}""")))
        val shading = built.document.character.shading
        assertEquals(CharacterShading(ShadingMode.TWO_TONE, true, 0.35f, ShadingLight.BODY), shading)
        val tooDeep = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe(""", "shading": {"farSide": 0.9}"""))) }
        assertEquals("$.shading.farSide", tooDeep.path)
        val exported = exportRecipe(built.document)
        assertEquals(0.35f, exported.recipe.shading?.farSide)
        assertTrue(exported.notCarried.none { "shading" in it }, "shading is carried: ${exported.notCarried}")
        assertNull(exportRecipe(builder.build(RecipeJson.read(recipe())).document).recipe.shading)
    }
}
