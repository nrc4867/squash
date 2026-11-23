package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A recipe's pose keys given as changes: each width read as its part's rest turns it, sent as the character's keys, and exported back. */
class RecipeKeyTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(keys: String) = """
        {"format": 1, "id": "toy", "name": "Toy", "parts": [
          {"id": "trunk", "template": "tube", "rest": "up",
           "tube": {"length": 0.4, "closed": true, "profiles": {
             "front": {"through": [[0.05, 0.06], [0.35, 0.06]]}, "back": {"through": [[0.05, 0.06], [0.35, 0.05]]},
             "left": {"through": [[0.05, 0.08], [0.35, 0.08]]}}},
           "sockets": [{"n": 1, "u": 0.75, "v": 0.75, "mirrored": true, "reach": 0.05}]},
          {"id": "limbs", "template": "tube", "on": "trunk", "socket": "socket.1", "pair": {}, "rest": "down", "outward": 30, "blend": 0.03,
           "tube": {"length": 0.25, "profiles": {"front": {"through": [[0, 0.03], [0.2, 0.025]]}, "back": {"through": [[0, 0.03], [0.2, 0.025]]},
             "outer": {"through": [[0, 0.03], [0.2, 0.025]]}, "inner": {"through": [[0, 0.028], [0.2, 0.022]]}}}}
        ],
        "keys": [$keys]}
    """.trimIndent()

    private val raised = """[{"part": "limbs-l", "joint": "base", "lean": 40, "mode": "free"}, {"part": "limbs-r", "joint": "base", "lean": 40, "mode": "free"}]"""

    @Test
    fun aKeysChangesAreSentForBothSidesOfAPair() {
        val keys = """{"name": "raised", "pose": $raised, "changes": [
            {"part": "limbs", "widths": {"outer": [[0, 1], [0.05, 1.2], [0.1, 1]], "front": [[0, 1.1]]}, "blend": 1.5},
            {"part": "trunk", "widths": {"left": [[0.3, 1], [0.38, 1.1]]}}]}"""
        val character = builder.build(RecipeJson.read(recipe(keys))).document.character
        val key = character.poseKeys.single()
        assertEquals("raised", key.name)
        assertEquals(listOf(PartId("limbs-l"), PartId("limbs-r")), key.pose.map { it.part })
        val limb = key.changes.first { it.part == PartId("limbs-l") }
        assertEquals(1.5f, limb.blend)
        assertEquals(key.changes.first { it.part == PartId("limbs-r") }.widths, limb.widths, "both sides take the pair's change")
        assertEquals(1.2f, limb.widths.getValue(View.TOP).minus[1].scale, "a limb resting down faces Top's -y to its outer side")
        assertTrue(limb.widths.getValue(View.TOP).plus.isEmpty(), "its inner side, drawn apart, keeps its widths")
        assertEquals(1.1f, limb.widths.getValue(View.SIDE).plus.single().scale, "Side's +y faces forward")
        val trunk = key.changes.first { it.part == PartId("trunk") }.widths.getValue(View.TOP)
        assertEquals(trunk.plus, trunk.minus, "a trunk's Top side given alone scales its other side too")
    }

    @Test
    fun aWidthThePartCannotTakeFailsAtItsPath() {
        val error = assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("""{"name": "raised", "pose": $raised, "changes": [{"part": "limbs", "widths": {"sideways": [[0, 1.1]]}}]}""")))
        }
        assertEquals("$.keys[0].changes[0].widths.sideways", error.path)
        assertFailsWith<RecipeException> {
            builder.build(RecipeJson.read(recipe("""{"name": "still", "pose": [{"part": "limbs-l", "joint": "base"}]}""")))
        }
    }

    @Test
    fun anExportedKeyBuildsBackTheSameKey() {
        val keys = """{"name": "raised", "pose": $raised, "changes": [{"part": "limbs", "widths": {"outer": [[0, 1], [0.05, 1.2]]}, "blend": 1.5}]}"""
        val document = builder.build(RecipeJson.read(recipe(keys))).document
        val exported = exportRecipe(document).recipe
        assertTrue(exported.keys.single().changes.all { change -> change.widths.keys.all { it.startsWith("side") || it.startsWith("top") } })
        assertEquals(document.character.poseKeys, builder.build(exported).document.character.poseKeys)
    }
}
