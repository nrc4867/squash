package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.model.RecipeTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A recipe's targets, and a file of them, read as the proportion check takes them. */
class RecipeTargetsTest {
    private val recipe = """
        {"format": 1, "id": "toy", "name": "Toy",
         "targets": [{"name": "handLength", "of": "head", "value": 0.4}, {"name": "footWidth", "of": "height", "value": 0.05, "tolerance": 0.1}],
         "parts": [{"id": "body", "template": "tube", "role": "body"}]}
    """.trimIndent()

    @Test
    fun aRecipeCarriesItsTargetsToTheBuild() {
        val built = RecipeBuilder(TemplateFolder.shipped()).build(RecipeJson.read(recipe))
        assertEquals(listOf(RecipeTarget("handLength", "head", 0.4f), RecipeTarget("footWidth", "height", 0.05f, 0.1f)), built.targets)
    }

    @Test
    fun aFileOfTargetsIsAListOfThem() {
        val read = RecipeJson.readTargets("""[{"name": "eyeLine", "of": "head", "value": 0.55}]""")
        assertEquals(listOf(RecipeTarget("eyeLine", "head", 0.55f)), read)
    }

    @Test
    fun somethingElseIsRefusedAsNotTargets() {
        assertFailsWith<RecipeException> { RecipeJson.readTargets("""{"eyeLine": 0.55}""") }
    }
}
