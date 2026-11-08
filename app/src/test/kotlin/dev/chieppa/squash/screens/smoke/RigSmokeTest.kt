package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.rig.BoneName
import dev.chieppa.squash.rig.SessionRig
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.sheet.RigShading
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.squash.screens.surface.rig.heatLayers
import dev.chieppa.squash.screens.surface.rig.rigPartColors
import dev.chieppa.squash.screens.surface.rig.seamLayers
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val RIG_WIDTH = 1200
private const val RIG_HEIGHT = 800

/** The fennec as the Rig stage shows it in every pane: its parts in their colors with the seams blending, and a knee's weight as a heat map. */
@Tag("slow")
class RigSmokeTest {
    private val work = TestWork()

    @AfterTest
    fun stop() = work.close()

    @Test
    fun theFennecShowsItsRig() {
        val folder = TemplateFolder.shipped()
        val session = CharacterSession(savedFennec(), TemplateLibrary(folder.parts, folder.plans), work.scope, work.dispatcher)
        val node = SessionRig(session).rig
        work.settle(session)
        val rig = assertNotNull(node.output.value)
        val assembled = assertNotNull(session.assembly.output.value)
        val selection = assertNotNull(sheetSelection(assembled, PartId("torso"), emptyMap()))
        val colors = rigPartColors(session.document.value.character)
        val seams = seamLayers(rig, colors)
        val knee = assertNotNull(rig.indexOf(BoneName(PartId("front-leg-l"), "knee")))
        val heat = heatLayers(rig, knee)
        val errors = CopyOnWriteArrayList<String>()
        val rects = sheetPaneRects(RIG_WIDTH, RIG_HEIGHT, 2)
        for ((name, layers) in listOf("rig-parts" to seams, "rig-heat-knee" to seams.mapValues { (id, it) -> it + heat[id].orEmpty() })) {
            val image = renderPanes(selection, SMOKE_LOOK, rects, RIG_WIDTH, RIG_HEIGHT, errors::add, RigShading(colors, layers))
            keepImage(image.pixels, image.width, name)
            assertNotBlank(image.pixels, name)
            val full = rects[SheetPane.FULL.ordinal]
            assertTrue(image.countIn(full, colors.getValue(PartId("torso"))) > 0, "$name shows the torso in its Rig color")
        }
        assertEquals(emptyList(), errors)
    }
}
