package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.recipe.build.RecipeBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A garment with a sheer area, a net area and a whole band, built through the real graph: it hides no skin, and its see-through cloth
 * holds each area's opacity and net at the vertices it covers, the net's counts round whole numbers.
 */
@Tag("integration")
@OptIn(ExperimentalCoroutinesApi::class)
class SeeThroughClothBuildTest {
    private val folder = TemplateFolder.shipped()
    private val recipe = """{"format": 1, "id": "pup", "name": "Pup", "plan": "canine-quadruped",
        "palette": [{"slot": "fur", "color": "#e6b36e"}, {"slot": "nylon", "color": "#202024"}],
        "parts": [{"id": "torso", "template": "torso", "role": "body", "color": "fur"}],
        "wardrobe": {"slots": [{"id": "body", "name": "Body"}], "garments": [
          {"id": "sock", "name": "Sock", "slot": "body", "color": "nylon", "areas": [
            {"id": "sheer", "opacity": 0.3, "paint": [{"on": "torso", "shape": "band", "u": [0.2, 0.55]}]},
            {"id": "net", "net": {"hole": 0.03, "thread": 0.004}, "paint": [{"on": "torso", "shape": "band", "u": [0.55, 0.85]}]},
            {"id": "band", "paint": [{"on": "torso", "shape": "band", "u": [0.85, 0.95]}]}]}],
          "outfits": [{"id": "on", "name": "On", "picks": {"body": "sock"}}], "shown": "on"}}"""

    @Test
    fun seeThroughClothHidesNoSkinAndHoldsEachAreasLook() = runTest {
        val built = RecipeBuilder(folder).build(RecipeJson.read(recipe))
        val session = CharacterSession(built.document, TemplateLibrary(folder.parts, folder.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val dress = assertNotNull(session.sessionSurface.dress.output.value?.dress)
        assertNull(dress.skin, "see-through cloth hides no skin")
        val cloth = assertNotNull(dress.garments.single().seeThrough)
        val byArea = cloth.area.indices.groupBy { cloth.area[it] }
        val sheer = assertNotNull(byArea[0])
        val net = assertNotNull(byArea[1])
        val band = assertNotNull(byArea[2])
        assertTrue(sheer.all { abs(cloth.opacity[it] - 0.3f) < 1e-4f && cloth.net[it] == 0f })
        assertTrue(net.all { cloth.opacity[it] == 1f && cloth.net[it] == 1f })
        assertTrue(band.all { cloth.opacity[it] == 1f && cloth.net[it] == 0f })
        for (v in net) {
            assertEquals(cloth.countA[v].roundToInt().toFloat(), cloth.countA[v], "a set's count round is whole")
            assertEquals(cloth.countA[v], cloth.countB[v], "a diamond net's sets cross as often round")
        }
        val trusted = net.count { cloth.trust[it] > 0.5f }
        assertTrue(trusted > net.size / 2, "the net is laid well over most of a plain run: $trusted of ${net.size}")
    }
}
