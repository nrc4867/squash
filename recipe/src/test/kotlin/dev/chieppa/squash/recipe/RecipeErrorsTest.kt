package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.build.RecipeBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A wrong recipe fails at the JSON path of what is wrong, saying it in recipe terms. */
class RecipeErrorsTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private val torso = """{"id": "torso", "template": "torso", "role": "body", "color": "fur"}"""

    private fun recipe(parts: String, palette: String = """[{"slot": "fur", "color": "#e6b36e"}]""") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": $palette, "parts": [$parts]}"""

    private fun error(text: String): RecipeException = assertFailsWith<RecipeException> { builder.build(RecipeJson.read(text)) }

    @Test
    fun aSmallRecipeBuilds() {
        val built = builder.build(RecipeJson.read(recipe("$torso, {\"id\": \"head\", \"template\": \"head\", \"on\": \"torso\", \"socket\": \"neck\", \"x-note\": \"a note\"}")))
        assertEquals(2, built.document.character.parts.size)
    }

    @Test
    fun anUnknownKeyIsNamedWithItsPath() {
        val e = error(recipe("""{"id": "torso", "template": "torso", "colour": "fur"}"""))
        assertEquals("$.parts[0]", e.path)
        assertTrue("colour" in e.message!!, e.message)
    }

    @Test
    fun anUnknownTemplateListsTheTemplates() {
        val e = error(recipe("""{"id": "torso", "template": "torsoo"}"""))
        assertEquals("$.parts[0].template", e.path)
        assertTrue("torso" in e.message!! && "no template" in e.message!!, e.message)
    }

    @Test
    fun aSocketThePartLacksListsWhatItOffers() {
        val e = error(recipe("""$torso, {"id": "head", "template": "head", "on": "torso", "socket": "nek"}"""))
        assertEquals("$.parts[1].socket", e.path)
        assertTrue("neck" in e.message!!, e.message)
    }

    @Test
    fun aPartOnAPartNotYetPlacedIsNamed() {
        val e = error(recipe("""{"id": "head", "template": "head", "on": "torso", "socket": "neck"}, $torso"""))
        assertEquals("$.parts[0].on", e.path)
    }

    @Test
    fun aSlotThePaletteLacksIsNamed() {
        val e = error(recipe("""{"id": "torso", "template": "torso", "color": "sand"}"""))
        assertEquals("$.parts[0].color", e.path)
        assertTrue("fur" in e.message!!, e.message)
    }

    @Test
    fun aBadColourAndABadOutlineAreNamed() {
        assertEquals("$.palette[0].color", error(recipe(torso, """[{"slot": "fur", "color": "#e6b3"}]""")).path)
        val outline = error(recipe("""{"id": "torso", "template": "torso", "outlines": {"side": {"through": [[0, 0], [0.5]]}}}"""))
        assertEquals("$.parts[0].outlines.side.through[1]", outline.path)
        val bezier = error(recipe("""{"id": "torso", "template": "torso", "outlines": {"side": {"bezier": [[0, 0], [0.1, 0.1], [0.2, 0.1]]}}}"""))
        assertEquals("$.parts[0].outlines.side.bezier", bezier.path)
    }

    @Test
    fun aCommandsOwnRefusalCarriesThePath() {
        val e = error(recipe("""{"id": "torso", "template": "torso", "regions": [{"id": "patch", "slot": "fur", "border": [[0.1, 0.1], [0.2, 0.2]]}]}"""))
        assertEquals("$.parts[0].regions[0]", e.path)
        assertTrue("anchors" in e.message!!, e.message)
    }

    @Test
    fun notJsonSaysSo() {
        assertEquals("$", error("{\"id\": ").path)
    }
}
