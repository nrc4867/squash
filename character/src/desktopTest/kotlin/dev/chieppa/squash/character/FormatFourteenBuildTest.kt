package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.readDocument
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A character saved in format 14 opens on one surface and builds it closed and manifold, its painted eyes on its head. */
class FormatFourteenBuildTest {
    @Test
    fun aFormatFourteenSaveOpensAndBuildsOneSurface() = runTest {
        val document = readDocument(File("../document/src/desktopTest/fixtures/format-14.json").readText())
        val session = CharacterSession(document, TemplateLibrary(shipped.parts, shipped.plans), backgroundScope, StandardTestDispatcher(testScheduler))
        session.settle()
        val surface = assertNotNull(session.characterSurface.output.value?.surface)
        assertEquals(document.character.parts.map { it.id }.toSet(), surface.parts)
        assertNull(closedManifoldProblem(surface.mesh))
        assertEquals(PartId("head"), document.character.paintedEyes.single().anchor.part)
    }
}
