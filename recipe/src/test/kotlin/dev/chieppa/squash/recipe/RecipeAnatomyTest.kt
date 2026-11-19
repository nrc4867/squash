package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A part's named anatomy fields build the part parameters they stand for, and export lifts those parameters back into them. */
class RecipeAnatomyTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private val body = """
        {"id": "body", "template": "tube", "role": "body", "rest": "up",
         "generic": {"section": "egg", "views": ["side", "top"], "rings": 32, "segments": 12},
         "sockets": [{"n": 1, "u": 0.2, "v": 0.5, "count": 4, "to": 0.8, "size": 0.5, "falloff": 0.6}, {"n": 2, "u": 0.9, "v": 0.75, "mirrored": true, "sink": 0}],
         "joints": [{"n": 1, "u": 0.5, "hinge": "y", "low": -30, "high": 60, "stretch": true}],
         "landmarks": [{"n": 1, "u": 0.4, "v": 0.1}],
         "socketsAt": {"socket.2": [0.85, 0.7]}}
    """.trimIndent()

    private val arm = """{"id": "arms", "template": "tube", "on": "body", "socket": "socket.2", "pair": {}, "rest": "out", "inkJoin": false}"""

    private fun recipe(parts: String) = """{"format": 1, "id": "toy", "name": "Toy", "parts": [$parts]}"""

    @Test
    fun namedFieldsBuildTheirParameters() {
        val params = builder.build(RecipeJson.read(recipe("$body, $arm"))).document.character.let { it.resolved(PartId("body"))!!.params }
        val expected = mapOf(
            "move.rest" to 2f, "generic.section" to 1f, "generic.view.side" to 1f, "generic.view.top" to 1f, "generic.view.front" to 0f,
            "generic.rings" to 32f, "generic.segments" to 12f,
            "socket.1.count" to 4f, "socket.1.to" to 0.8f, "socket.1.size" to 0.5f, "socket.1.falloff" to 0.6f,
            "joint.1.u" to 0.5f, "joint.1.hinge" to 1f, "joint.1.low" to -30f, "joint.1.high" to 60f, "joint.1.stretch" to 1f,
            "landmark.1.u" to 0.4f, "landmark.1.v" to 0.1f, "move.socket.socket.2.u" to 0.85f, "move.socket.socket.2.v" to 0.7f,
        )
        expected.forEach { (key, value) -> assertEquals(value, params[key], key) }
    }

    @Test
    fun exportLiftsTheParametersBackIntoNamedFields() {
        val built = builder.build(RecipeJson.read(recipe("$body, $arm"))).document
        val exported = exportRecipe(built).recipe
        val part = exported.parts.first { it.id == "body" }
        assertEquals(emptyMap(), part.params)
        assertEquals(1, part.joints.size)
        assertEquals(4, part.sockets.first { it.n == 1 }.count)
        assertEquals(false, exported.parts.first { it.id == "arms" }.inkJoin)
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }

    @Test
    fun aKeySetByANamedFieldAndByParamsFails() {
        val e = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("""{"id": "body", "template": "tube", "rest": "up", "params": {"move.rest": 3}}""")))
        }
        assertEquals("$.parts[0].params.move.rest", e.path)
    }

    private val head = """{"id": "head", "template": "head-round", "role": "head", "socketsAt": {"ear": [0.8, 0.75, 0.5]}}"""
    private val ear = """{"id": "ears", "template": "ear", "version": 3, "on": "head", "socket": "ear", "pair": {}}"""

    @Test
    fun aTemplatesOwnSocketTakesASinkThroughSocketsAtAndExportsItBack() {
        val built = builder.build(RecipeJson.read(recipe("$head, $ear"))).document
        val params = built.character.resolved(PartId("head"))!!.params
        assertEquals(0.5f, params["move.socket.ear.sink"])
        assertEquals(0.8f, params["move.socket.ear.u"])
        val exported = exportRecipe(built).recipe
        assertEquals(listOf(0.8f, 0.75f, 0.5f), exported.parts.first { it.id == "head" }.socketsAt.getValue("ear"))
        assertEquals(built, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }

    @Test
    fun aSocketsAtSinkOutOfRangeOrAPlaceOfTheWrongLengthStopsAtItsPath() {
        val deep = head.replace("0.5]", "9]")
        assertEquals("$.parts[0].socketsAt.ear", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("$deep, $ear"))) }.path)
        val long = head.replace("0.5]", "0.5, 1]")
        assertEquals("$.parts[0].socketsAt.ear", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("$long, $ear"))) }.path)
    }
}
