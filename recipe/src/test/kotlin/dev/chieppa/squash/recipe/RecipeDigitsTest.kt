package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.socketsOn
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A socket, region or mark names a digit's patch on a hand, foot or paw; one the part does not build warns rather than vanishing. */
class RecipeDigitsTest {
    private val templates = TemplateFolder.shipped()
    private val builder = RecipeBuilder(templates)

    private val body = """{"id": "body", "template": "tube", "role": "body", "rest": "up", "sockets": [{"n": 1, "u": 0.5, "v": 0.25}]}"""

    private fun hand(extra: String = "", digits: Int = 4) =
        """{"id": "hand", "template": "hand", "on": "body", "socket": "socket.1", "role": "hand", "params": {"digits": $digits}$extra}"""

    private fun recipe(vararg parts: String) = """{"format": 1, "id": "toy", "name": "Toy", "palette": [{"slot": "gold", "color": "#e0b040"}], "parts": [${parts.joinToString()}]}"""

    @Test
    fun aSocketOnADigitLiesOnThatDigitsPatchAndExportsBack() {
        val ring = ""","sockets": [{"n": 1, "u": 0.7, "v": 0.25, "patch": "fingers:2"}, {"n": 2, "u": 0.6, "v": 0.25, "patch": "fingers:thumb"}]"""
        val built = builder.build(RecipeJson.read(recipe(body, hand(ring))))
        assertEquals(emptyList(), built.warnings)
        val instance = built.document.character.part(PartId("hand"))!!
        val params = built.document.character.resolvedShape(instance).params
        assertEquals(2f, params[HandSockets.digit(1)])
        assertEquals(-1f, params[HandSockets.digit(2)])
        val sockets = socketsOn(null, templates.template(instance.template)!!, params).associateBy { it.name }
        assertEquals("fingers:2", sockets.getValue("socket.1").patch)
        assertEquals("fingers:thumb", sockets.getValue("socket.2").patch)
        val exported = exportRecipe(built.document).recipe
        assertEquals(listOf("fingers:2", "fingers:thumb"), exported.parts.first { it.id == "hand" }.sockets.map { it.patch })
        assertEquals(built.document, builder.build(RecipeJson.read(RecipeJson.write(exported))).document)
    }

    @Test
    fun aDigitTheTemplateHasNoneOfFailsAndOneThePartDoesNotBuildWarns() {
        val e = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe(body, hand(""","sockets": [{"n": 1, "u": 0.7, "v": 0.25, "patch": "fingers:pinky"}]"""))))
        }
        assertEquals("$.parts[1].sockets[0].patch", e.path)
        val unbuilt = builder.build(RecipeJson.read(recipe(body, hand(""","sockets": [{"n": 1, "u": 0.7, "v": 0.25, "patch": "fingers:3"}]"""))))
        assertEquals(listOf("$.parts[1].sockets[0].patch"), unbuilt.warnings.map { it.substringBefore(": ") })
    }

    @Test
    fun aRegionOnADigitThePartDoesNotBuildWarns() {
        val region = ""","regions": [{"id": "band", "slot": "gold", "patch": "fingers:4", "border": [[0.7, 0.1], [0.75, 0.1], [0.75, 0.4]]}]"""
        val warnings = builder.build(RecipeJson.read(recipe(body, hand(region)))).warnings
        assertTrue(warnings.any { it.startsWith("$.parts[1].regions[0].patch") && "fingers:4" in it }, "$warnings")
        val kept = builder.build(RecipeJson.read(recipe(body, hand(region.replace("fingers:4", "fingers:2"))))).warnings
        assertEquals(emptyList(), kept)
    }

    @Test
    fun aPrintOnADigitWarnsThatClothDoesNotReachIt() {
        val glove = """{"garments": [{"id": "glove", "name": "Glove", "covers": [{"part": "hand"}],
            "marks": [{"id": "seam", "kind": "line", "on": "hand", "patch": "fingers:1", "points": [[0.7, 0.2], [0.8, 0.2]]}]}]}"""
        val text = recipe(body, hand()).removeSuffix("}") + ""","wardrobe": $glove}"""
        val warnings = builder.build(RecipeJson.read(text)).warnings
        assertTrue(warnings.any { it.startsWith("$.wardrobe.garments[0].marks[0].patch") && "fingers:1" in it }, "$warnings")
    }
}
