package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ColliderShape
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Lock hair in a recipe: a preset's groups, groups changed and added by id, the body's collision shapes, and the export back. */
class RecipeLockHairTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(hair: String) = """
        {"format": 1, "id": "toy", "name": "Toy",
         "palette": [{"slot": "hair", "color": "#6b4226"}, {"slot": "ribbon", "color": "#c03040"}],
         "parts": [{"id": "head", "template": "blob", "role": "head"}],
         "hair": [$hair]}
    """.trimIndent()

    @Test
    fun aPresetGivesItsGroupsSizedToTheHeadAndTheHeadItsCollider() {
        val built = builder.build(RecipeJson.read(recipe("""{"on": "head", "preset": "ponytail", "color": "hair", "band": "ribbon"}"""))).document.character
        assertEquals(listOf("tail", "left", "right", "fringe"), built.lockGroups.map { it.id.value })
        assertTrue(built.lockGroups.all { it.on == PartId("head") && it.color == SlotName("hair") })
        assertEquals(SlotName("ribbon"), built.lockGroups.first().band)
        assertEquals(listOf(ColliderShape.SPHERE), built.colliders.map { it.shape })
    }

    @Test
    fun aGroupChangesThePresetsOfItsIdOrAddsOne() {
        val hair = """{"on": "head", "preset": "bob", "reach": 0.2,
            "groups": [{"id": "fringe", "count": 4, "tip": "forked", "locks": [{"index": 1, "hidden": true}]},
                       {"id": "crown", "removed": true},
                       {"id": "strays", "kind": "strays", "count": 3}]}"""
        val built = builder.build(RecipeJson.read(recipe(hair))).document.character
        val fringe = assertNotNull(built.lockGroups.firstOrNull { it.id == LockGroupId("fringe") })
        assertEquals(4, fringe.count)
        assertEquals(LockTip.FORKED, fringe.tip)
        assertEquals(true, fringe.overrideOf(1)?.hidden)
        assertTrue(built.lockGroups.none { it.id == LockGroupId("crown") })
        assertEquals(LockGroupKind.STRAYS, built.lockGroups.last().kind)
    }

    @Test
    fun mistakesFailAtTheirPaths() {
        assertEquals("$.hair[0].groups[0].kind", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "groups": [{"id": "new"}]}"""))) }.path)
        assertEquals("$.hair[0].on", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "nobody", "preset": "bob"}"""))) }.path)
        assertEquals("$.hair[0].groups[0]", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "groups": [{"id": "g", "kind": "back", "count": 99}]}"""))) }.path)
    }

    @Test
    fun lockHairExportsAndBuildsBackTheSame() {
        val built = builder.build(RecipeJson.read(recipe("""{"on": "head", "preset": "curly", "reach": 0.2, "groups": [{"id": "back", "locks": [{"index": 2, "curl": -2.0, "bones": 5}]}]}"""))).document
        val exported = exportRecipe(built).recipe
        val again = builder.build(exported).document.character
        assertEquals(built.character.lockGroups, again.lockGroups)
        assertEquals(built.character.colliders, again.colliders)
    }
}
