package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ColliderShape
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.hairOutlineOn
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.document.PartingKind
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.document.Whorl
import dev.chieppa.squash.document.scalpFlowOn
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
        assertEquals(listOf("tail", "fringe"), built.lockGroups.map { it.id.value })
        assertTrue(built.lockGroups.all { it.on == PartId("head") && it.color == SlotName("hair") })
        assertEquals(SlotName("ribbon"), built.lockGroups.first().band)
        assertEquals(listOf(ColliderShape.SPHERE), built.colliders.map { it.shape })
    }

    @Test
    fun aGroupChangesThePresetsOfItsIdOrAddsOne() {
        val hair = """{"on": "head", "preset": "bob", "reach": 0.2,
            "groups": [{"id": "fringe", "count": 4, "tip": "forked", "locks": [{"index": 1, "hidden": true}]},
                       {"id": "back", "removed": true},
                       {"id": "strays", "kind": "strays", "count": 3}]}"""
        val built = builder.build(RecipeJson.read(recipe(hair))).document.character
        val fringe = assertNotNull(built.lockGroups.firstOrNull { it.id == LockGroupId("fringe") })
        assertEquals(4, fringe.count)
        assertEquals(LockTip.FORKED, fringe.tip)
        assertEquals(true, fringe.overrideOf(1)?.hidden)
        assertTrue(built.lockGroups.none { it.id == LockGroupId("back") })
        assertEquals(LockGroupKind.STRAYS, built.lockGroups.last().kind)
    }

    @Test
    fun mistakesFailAtTheirPaths() {
        assertEquals("$.hair[0].groups[0].kind", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "groups": [{"id": "new"}]}"""))) }.path)
        assertEquals("$.hair[0].on", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "nobody", "preset": "bob"}"""))) }.path)
        assertEquals("$.hair[0].groups[0]", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "groups": [{"id": "g", "kind": "back", "count": 99}]}"""))) }.path)
    }

    @Test
    fun aFlowChangesThePresetsAndExportsAndBuildsBackTheSame() {
        val preset = builder.build(RecipeJson.read(recipe("""{"on": "head", "preset": "sideSwept", "reach": 0.2}"""))).document.character
        assertEquals(PartingKind.SIDE, preset.scalpFlowOn(PartId("head")).parting.kind, "a side-swept preset parts at the side")
        val hair = """{"on": "head", "preset": "longStraight", "reach": 0.2,
            "flow": {"part": {"kind": "side", "at": -0.5, "clean": 0.6, "color": "hair"}, "sweepBack": 0.4,
                     "cowlicks": [{"around": 20, "up": 50, "radius": 15, "turn": 0.3, "lift": 25}]},
            "groups": [{"id": "crown", "flow": 0.5}]}"""
        val built = builder.build(RecipeJson.read(recipe(hair))).document
        val flow = built.character.scalpFlowOn(PartId("head"))
        assertEquals(Parting(PartingKind.SIDE, at = -0.5f, clean = 0.6f, color = SlotName("hair")), flow.parting)
        assertEquals(0.4f, flow.sweepBack)
        assertEquals(Whorl(), flow.whorl, "what the flow leaves out is the preset's")
        assertEquals(listOf(Cowlick(20f, 50f, 15f, turn = 0.3f, lift = 25f)), flow.cowlicks)
        assertEquals(0.5f, built.character.lockGroups.first { it.id == LockGroupId("crown") }.flow)
        val again = builder.build(exportRecipe(built).recipe).document.character
        assertEquals(built.character.scalpFlows, again.scalpFlows)
        assertEquals(built.character.lockGroups, again.lockGroups)
        assertEquals("$.hair[0].flow.part.color", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "flow": {"part": {"kind": "centre", "color": "nothing"}}}"""))) }.path)
    }

    @Test
    fun aFittedMassIsFittedToTheHeadAndItsGroupsRootOnIt() {
        val built = builder.build(RecipeJson.read(recipe("""{"on": "head", "preset": "bob", "fitMass": {"params": {"back": 0.6}}}"""))).document.character
        val mass = assertNotNull(built.part(PartId("head-hair")), "a fitted mass takes the head's id with -hair")
        assertEquals(PartId("head"), mass.socket?.parent)
        assertEquals(0.6f, built.resolved(mass).shape.params["back"], "a param given replaces the fitted one")
        assertTrue(built.lockGroups.all { it.mass == mass.id })
        assertEquals("$.hair[0].fitMass", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "mass": "head", "fitMass": {}}"""))) }.path)
    }

    @Test
    fun anOutlineIsHeldExportedAndBuildsBackTheSame() {
        val square = "[[-0.2, 0.0], [0.2, 0.0], [0.2, 0.4], [-0.2, 0.4]]"
        val hair = """{"on": "head", "preset": "bob", "reach": 0.2,
            "outline": {"views": [{"view": "front", "points": $square, "tips": [2, 3]}, {"view": "back", "points": $square}], "fit": false}}"""
        val built = builder.build(RecipeJson.read(recipe(hair))).document
        val outline = assertNotNull(built.character.hairOutlineOn(PartId("head")))
        assertEquals(listOf(OutlineView.FRONT, OutlineView.BACK), outline.views.map { it.view })
        assertEquals(listOf(2, 3), outline.view(OutlineView.FRONT)?.tips)
        assertEquals(false, outline.fit)
        val again = builder.build(exportRecipe(built).recipe).document.character
        assertEquals(built.character.hairOutlines, again.hairOutlines)
        assertEquals(built.character.lockGroups, again.lockGroups)
        assertEquals("$.hair[0].outline.views", assertFailsWith<RecipeException> { builder.build(RecipeJson.read(recipe("""{"on": "head", "preset": "bob", "outline": {"views": [{"view": "back", "points": $square}]}}"""))) }.path)
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
