package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.probe.RecipeProbe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The probe reads a built recipe's frame: a place's body point and back, and each part's axis, v directions, joints and sockets. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeProbeTest {
    private val folder = TemplateFolder.shipped()

    private val recipe = """
        {"format": 1, "id": "toy", "name": "Toy", "parts": [
          {"id": "body", "template": "tube", "role": "body", "sockets": [{"n": 1, "u": 0.8, "v": 0.25, "mirrored": true}]},
          {"id": "legs", "template": "tube", "on": "body", "socket": "socket.1", "pair": {}, "rest": "down", "size": 0.6}
        ]}
    """.trimIndent()

    @Test
    fun aPlaceOnTheSurfaceProbesBackToItsUAndV() = runTest {
        val built = RecipeBuilder(folder).build(RecipeJson.read(recipe)).document
        val session = CharacterSession(built, TemplateLibrary(folder.parts, folder.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val probe = RecipeProbe(built.character, checkNotNull(session.assembly.output.value), folder, null)
        val place = probe.at("body", 0.5f, 0.3f)
        val found = checkNotNull(probe.nearest(place.point))
        assertEquals(PartId("body"), found.part)
        assertEquals(0.5f, found.u, 0.02f)
        assertEquals(0.3f, found.v, 0.02f)
        val body = probe.describe("body")
        assertTrue(body.any { "u runs forward" in it && "v 0 up" in it && "v 0.25 left" in it }, body.joinToString("\n"))
        assertTrue(body.any { "socket socket.1:" in it && "faces left" in it }, body.joinToString("\n"))
        val leg = probe.describe("legs-r")
        assertTrue(leg.any { "u runs down" in it && "right side" in leg.first() }, leg.joinToString("\n"))
        assertTrue(leg.any { "joint base:" in it }, leg.joinToString("\n"))
    }

    @Test
    fun aSocketMovedBySocketsAtIsReportedWhereItWasMoved() = runTest {
        val moved = recipe.replace("\"sockets\": [", "\"socketsAt\": {\"socket.1\": [0.4, 0.25]}, \"sockets\": [")
        val built = RecipeBuilder(folder).build(RecipeJson.read(moved)).document
        val session = CharacterSession(built, TemplateLibrary(folder.parts, folder.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        val probe = RecipeProbe(built.character, checkNotNull(session.assembly.output.value), folder, null)
        val body = probe.describe("body")
        assertTrue(body.any { "socket socket.1: u 0.4," in it }, body.joinToString("\n"))
    }
}
