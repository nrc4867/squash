package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.MarkGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A part's own curve places and an expression that opens a mouth, built, failed where wrong, and exported back. */
class RecipeCurveAndMouthTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private val body = """{"id": "body", "template": "tube", "role": "body", "rest": "up", "sockets": [{"n": 1, "u": 0.2, "v": 0.5}, {"n": 2, "u": 1, "v": 0}]}"""

    private fun tail(curve: String) = """{"id": "tail", "template": "tail-curve", "on": "body", "socket": "socket.1", "curve": $curve}"""

    private val head = """
        {"id": "head", "template": "head-round", "on": "body", "socket": "socket.2",
         "marks": [{"id": "lip", "points": [[0.26, 0.95], [0.25, 0.0], [0.26, 0.05]]}],
         "groups": [{"id": "mouth", "name": "Mouth", "controls": {"open": 0}, "roles": [{"role": "lipLine", "marks": ["lip"]}]}]}
    """.trimIndent()

    private fun recipe(parts: String, expressions: String = "[]") =
        """{"format": 1, "id": "toy", "name": "Toy", "parts": [$parts], "expressions": $expressions}"""

    @Test
    fun aCurveWritesItsPlacesAndExportsBack() {
        val built = builder.build(RecipeJson.read(recipe("$body, ${tail("[[0.3, -0.1], [0.6, 0.1], [0.8, 0.3, 0.05], [0.7, 0.4]]")}"))).document
        val params = built.character.resolved(PartId("tail"))!!.params
        assertEquals(0.8f, params["curve.3.along"])
        assertEquals(0.05f, params["curve.3.side"])
        assertEquals(0.4f, params["curve.4.up"])
        val exported = exportRecipe(built).recipe.parts.first { it.id == "tail" }
        assertEquals(emptyMap(), exported.params)
        assertEquals(listOf(0.8f, 0.3f, 0.05f), exported.curve[2])
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exportRecipe(built).recipe))).document)
    }

    @Test
    fun aCurveOfOnePlaceOrABadPlaceFails() {
        assertEquals("$.parts[1].curve", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("$body, ${tail("[[0.3, 0.1]]")}"))) }.path)
        assertEquals("$.parts[1].curve[1]", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("$body, ${tail("[[0.3, 0.1], [0.5]]")}"))) }.path)
    }

    @Test
    fun anExpressionOpensAMouthByItsGroup() {
        val built = builder.build(RecipeJson.read(recipe("$body, $head", """[{"id": "shout", "name": "Shout", "open": {"mouth": 1}}]"""))).document
        val expression = built.character.expressions.single { it.id == ExpressionId("shout") }
        assertEquals(mapOf(MarkGroupId("mouth") to 1f), expression.open)
        assertEquals(mapOf("mouth" to 1f), exportRecipe(built).recipe.expressions.single().open)
    }

    @Test
    fun anExpressionOpeningNoGroupFails() {
        val e = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("$body, $head", """[{"id": "shout", "name": "Shout", "open": {"jaw": 1}}]""")))
        }
        assertEquals("$.expressions[0].open.jaw", e.path)
    }
}
