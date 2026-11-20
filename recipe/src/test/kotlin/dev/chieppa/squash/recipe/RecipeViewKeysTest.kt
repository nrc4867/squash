package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.FaceStyle
import dev.chieppa.squash.document.KeyFeature
import dev.chieppa.squash.document.KeyMove
import dev.chieppa.squash.document.KeyView
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.ViewKey
import dev.chieppa.squash.document.ViewKeys
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A recipe's view keys: the defaults when left out, written through with each part read as the recipe names it, exported and read back. */
class RecipeViewKeysTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(keys: String = "") =
        """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped", "palette": [{"slot": "fur", "color": "#e6b36e"}],
           "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"},
             {"id": "head", "template": "head", "on": "torso", "socket": "neck"},
             {"id": "ears", "template": "ear", "on": "head", "socket": "ear", "pair": {"left": "ear-l", "right": "ear-r"}}]
           $keys}"""

    @Test
    fun keysAreTheDefaultsWhenLeftOut() {
        assertEquals(ViewKeys(), builder.build(RecipeJson.read(recipe())).document.character.viewKeys)
        assertNull(exportRecipe(builder.build(RecipeJson.read(recipe())).document).recipe.viewKeys)
    }

    @Test
    fun keysAreWrittenThroughAndRoundTrip() {
        val text = recipe(
            """, "viewKeys": {"face": "fixed", "faceFollow": 0.8, "keepFace": true, "keys": [
                 {"view": "three-quarter", "moves": [{"feature": "muzzle", "yaw": 12, "left": 0.04}, {"part": "ear-l", "twist": 25}, {"feature": "head", "area": "cheeks", "push": 0.02}]},
                 {"view": "back", "moves": [{"feature": "head", "width": 1.06, "height": 0.95}]}]}""",
        )
        val built = builder.build(RecipeJson.read(text)).document
        val keys = built.character.viewKeys
        assertEquals(FaceStyle.FIXED, keys.face)
        assertEquals(0.8f, keys.faceFollow)
        assertEquals(
            ViewKey(
                KeyView.THREE_QUARTER,
                moves = listOf(KeyMove(KeyFeature.MUZZLE, yaw = 12f, left = 0.04f), KeyMove(part = PartId("ear-l"), twist = 25f), KeyMove(KeyFeature.HEAD, area = "cheeks", push = 0.02f)),
            ),
            keys.keys.first(),
        )
        val exported = exportRecipe(built).recipe
        assertEquals(keys, exported.viewKeys)
        val again = builder.build(RecipeJson.read(RecipeJson.write(exported))).document
        assertEquals(keys, again.character.viewKeys)
    }

    @Test
    fun aMoveOnAPartTheRecipeLacksStopsAtItsPath() {
        val error = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe(""", "viewKeys": {"keys": [{"view": "side", "moves": [{"part": "horn", "yaw": 5}]}]}""")))
        }
        assertEquals("$.viewKeys.keys[0].moves[0].part", error.path)
    }
}
