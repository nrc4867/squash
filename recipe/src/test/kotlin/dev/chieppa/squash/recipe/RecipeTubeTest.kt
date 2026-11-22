package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A tube's widths drawn as curves along its bone, and a socket that places its part's root by its reach from the axis. */
class RecipeTubeTest {
    private val folder = TemplateFolder.shipped()
    private val library = folder.library()
    private val builder = RecipeBuilder(folder)

    private fun recipe(parts: String) = """{"format": 1, "id": "toy", "name": "Toy", "parts": [$parts]}"""

    private fun trunk(width: Float, reach: String = "") = """
        {"id": "trunk", "template": "tube", "rest": "up",
         "tube": {"length": 0.4, "closed": true, "profiles": {
           "front": {"through": [[0.05, 0.06], [0.2, 0.07], [0.35, 0.06]]},
           "back": {"through": [[0.05, 0.06], [0.2, 0.06], [0.35, 0.05]]},
           "left": {"through": [[0.05, $width], [0.2, ${width + 0.01f}], [0.35, $width]]}}},
         "sockets": [{"n": 1, "u": 0.75, "v": 0.75, "mirrored": true$reach}]}
    """.trimIndent()

    private val limb = """
        {"id": "limbs", "template": "tube", "on": "trunk", "socket": "socket.1", "pair": {}, "rest": "down", "outward": 30,
         "tube": {"length": 0.25, "profiles": {"front": {"through": [[0, 0.03], [0.2, 0.025]]}, "back": {"through": [[0, 0.03], [0.2, 0.025]]},
           "outer": {"through": [[0, 0.03], [0.2, 0.025]]}, "inner": {"through": [[0, 0.028], [0.2, 0.022]]}}}}
    """.trimIndent()

    @Test
    fun aCurveIsTheBuiltOutlineAsDrawn() {
        val character = builder.build(RecipeJson.read(recipe(trunk(0.08f)))).document.character
        val side = character.resolved(PartId("trunk"))!!.outlines.getValue(View.SIDE).points
        assertEquals(0f, side.first().x, "a closed tube starts on the axis at its socket end")
        assertEquals(0f, side.first().y)
        assertEquals(side.first(), side.last(), "and closes back there")
        val ends = side.filterIndexed { i, _ -> i % 3 == 0 }
        for (place in listOf(0.05f to 0.06f, 0.2f to 0.07f, 0.35f to 0.06f, 0.2f to -0.06f, 0.4f to 0f)) {
            assertTrue(ends.any { abs(it.x - place.first) < 1e-6f && abs(it.y - place.second) < 1e-6f }, "the Side outline passes through $place: $ends")
        }
        val top = character.resolved(PartId("trunk"))!!.outlines.getValue(View.TOP).points
        assertTrue(top.filterIndexed { i, _ -> i % 3 == 0 }.any { abs(it.x - 0.2f) < 1e-6f && abs(it.y + 0.09f) < 1e-6f }, "Top's missing right side mirrors its left")
    }

    @Test
    fun anOpenTubeRunsOutAlongOneSideAndBackAlongTheOther() {
        val character = builder.build(RecipeJson.read(recipe("${trunk(0.08f)}, $limb"))).document.character
        val top = character.resolved(PartId("limbs-l"))!!.outlines.getValue(View.TOP).points
        assertEquals(0f, top.first().x)
        assertEquals(0.028f, top.first().y, 1e-6f, "Top's +y faces the limb's right, its inner side, resting down")
        assertEquals(-0.03f, top.last().y, 1e-6f)
        assertTrue(top.any { abs(it.x - 0.25f) < 1e-6f && abs(it.y) < 1e-6f }, "it rounds over to the axis at its length")
    }

    @Test
    fun aTubeNeedsItsRestAndBothSideProfiles() {
        val noRest = assertFailsAt { builder.build(RecipeJson.read(recipe("""{"id": "t", "template": "tube", "tube": {"length": 0.2, "profiles": {"left": {"through": [[0, 0.1], [0.1, 0.1]]}}}}"""))) }
        assertEquals("$.parts[0].rest", noRest)
        val oneSide = assertFailsAt { builder.build(RecipeJson.read(recipe("""{"id": "t", "template": "tube", "rest": "up", "tube": {"length": 0.2, "profiles": {"front": {"through": [[0, 0.1], [0.1, 0.1]]}}}}"""))) }
        assertEquals("$.parts[0].tube.profiles", oneSide)
    }

    @Test
    fun aRootByReachStaysWhereItIsHoweverWideItsParentIsDrawn() {
        fun root(width: Float, reach: String): Pair<Float, Float> {
            val character = builder.build(RecipeJson.read(recipe("${trunk(width, reach)}, $limb"))).document.character
            val assembled = assembleNow(assemblyParts(character), { character.resolved(it) ?: PartData() }, library.plan(character.bodyPlan), library)
            val origin = assembled.part(PartId("limbs-l"))!!.origin
            return origin.y to origin.z
        }
        val narrow = root(0.08f, ", \"reach\": 0.05")
        val wide = root(0.11f, ", \"reach\": 0.05")
        assertEquals(narrow.first, wide.first, 1e-4f, "the root's height")
        assertEquals(narrow.second, wide.second, 1e-4f, "how far out the root sits")
        assertEquals(-0.05f, wide.second, 1e-3f, "the root sits its reach toward v from the axis")
        assertEquals(0.3f, wide.first, 1e-3f, "at the socket's u along the trunk")
        val sunk = root(0.08f, "").second to root(0.11f, "").second
        assertTrue(abs(sunk.first - sunk.second) > 0.01f, "a socket by sink moves its part out with the surface: $sunk")
    }

    @Test
    fun exportKeepsTheReach() {
        val built = builder.build(RecipeJson.read(recipe("${trunk(0.08f, ", \"reach\": 0.05")}, $limb"))).document
        val socket = exportRecipe(built).recipe.parts.first { it.id == "trunk" }.sockets.single()
        assertEquals(0.05f, socket.reach)
    }

    private fun assertFailsAt(block: () -> Unit): String = try {
        block()
        throw AssertionError("expected a recipe error")
    } catch (e: RecipeException) {
        e.path
    }
}
