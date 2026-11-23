package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.JoinParams
import dev.chieppa.squash.character.build.NeckJoin
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.NeckStyle
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** A recipe's neck style: blended when left out, set, carried to the head's join alone, and exported. */
class RecipeNeckTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())

    private fun recipe(neck: String = "") =
        """{"format": 1, "id": "pup", "name": "Pup", "palette": [{"slot": "fur", "color": "#e6b36e"}],
           "parts": [
             {"id": "torso", "template": "tube", "role": "body", "color": "fur", "rest": "up", "sockets": [{"n": 1, "u": 1, "v": 0, "sink": 0}]},
             {"id": "neck", "template": "tube", "role": "neck", "color": "fur", "on": "torso", "socket": "socket.1", "rest": "up", "sockets": [{"n": 1, "u": 1, "v": 0, "sink": 0}]},
             {"id": "head", "template": "head-round", "role": "head", "color": "fur", "on": "neck", "socket": "socket.1"}
           ]$neck}"""

    @Test
    fun theStyleIsBlendedWhenLeftOutAndSetWhenGiven() {
        assertEquals(NeckStyle.BLENDED, builder.build(RecipeJson.read(recipe())).document.character.neck)
        assertEquals(NeckStyle.DEFINED, builder.build(RecipeJson.read(recipe(""", "neck": "defined""""))).document.character.neck)
    }

    @Test
    fun onlyTheHeadsJoinCarriesTheStyle() {
        val character = builder.build(RecipeJson.read(recipe(""", "neck": "between""""))).document.character
        val placements = assemblyParts(character).associate { it.id to it.placement }
        assertEquals(NeckJoin.BETWEEN, NeckJoin.of(placements.getValue(PartId("head"))))
        assertFalse(JoinParams.NECK in placements.getValue(PartId("neck")))
        assertFalse(JoinParams.NECK in placements.getValue(PartId("torso")))
        val blended = builder.build(RecipeJson.read(recipe())).document.character
        assertFalse(assemblyParts(blended).any { JoinParams.NECK in it.placement })
    }

    @Test
    fun theStyleExportsAndIsLeftOutWhenBlended() {
        assertEquals(NeckStyle.DEFINED, exportRecipe(builder.build(RecipeJson.read(recipe(""", "neck": "defined""""))).document).recipe.neck)
        assertNull(exportRecipe(builder.build(RecipeJson.read(recipe())).document).recipe.neck)
    }
}
