package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shipped recipes build into characters that open as saved files, and a character written as a recipe builds into itself again. */
class RecipeBuildTest {
    private val recipes = File(System.getProperty("squash.recipes"))
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun built(name: String) = builder.build(RecipeJson.read(File(recipes, "$name.recipe.json").readText()))

    private fun opened(document: SquashDocument): SquashDocument = readDocument(Json.encodeToString(SquashDocument.serializer(), document))

    @Test
    fun theFennecBuildsAndOpens() {
        val fennec = built("fennec")
        val character = fennec.document.character
        assertEquals(13, character.parts.size)
        assertEquals(Side.RIGHT, character.part(PartId("front-paw-r"))?.side)
        assertEquals(PartId("front-leg-r"), character.part(PartId("front-paw-r"))?.socket?.parent)
        assertEquals(Role.HAND, character.part(PartId("front-paw-l"))?.role)
        assertEquals(1544, character.resolved(PartId("torso"))!!.surface.marks.size + character.resolved(PartId("tail"))!!.surface.marks.size)
        assertEquals(setOf("walk-0", "walk-2"), fennec.poses.keys)
        assertEquals(emptyList(), fennec.warnings)
        assertEquals(fennec.document, opened(fennec.document))
    }

    @Test
    fun theDressedWomanBuildsAndOpens() {
        val woman = built("woman-dressed")
        val character = woman.document.character
        assertTrue(character.wardrobe.outfits.size >= 2, "outfits ${character.wardrobe.outfits.map { it.id.value }}")
        assertTrue(character.parts.any { it.garment != null }, "a garment owns parts")
        assertTrue(!character.displayPose.isRest)
        assertEquals(woman.document, opened(woman.document))
        val again = builder.build(RecipeJson.read(RecipeJson.write(exportRecipe(woman.document).recipe))).document
        assertEquals(woman.document, again)
    }

    @Test
    fun aRecipesFlatEyesBuildFlatAndWriteBackAsARecipe() {
        val recipe = RecipeJson.read(File(recipes, "fennec.recipe.json").readText())
        assertTrue(builder.build(recipe).document.character.paintedEyes.none { it.flat }, "eyes follow the head's curve unless made flat")
        val flat = recipe.copy(eyes = recipe.eyes.map { it.copy(flat = true) })
        val document = builder.build(RecipeJson.read(RecipeJson.write(flat))).document
        assertTrue(document.character.paintedEyes.isNotEmpty() && document.character.paintedEyes.all { it.flat })
        assertEquals(document, opened(document))
        assertEquals(document, builder.build(RecipeJson.read(RecipeJson.write(exportRecipe(document).recipe))).document)
    }

    @Test
    fun aCharacterWrittenAsARecipeBuildsIntoItself() {
        val fennec = built("fennec").document
        val exported = exportRecipe(fennec)
        assertEquals(emptyList(), exported.notCarried)
        val again = builder.build(RecipeJson.read(RecipeJson.write(exported.recipe))).document
        assertEquals(fennec, again)
    }
}
