package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.edges
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val PAW_WIDTH = 1440
private const val PAW_HEIGHT = 900

private val DRAWN_SIDE = edges(0f to 0.1f, 0.15f to 0.11f, 0.26f to 0.07f, 0.3f to 0f, 0.26f to -0.07f, 0.15f to -0.11f, 0f to -0.1f)
private val AROUND_THE_LEG = edges(-0.12f to 0f, -0.08f to 0.1f, 0.1f to 0.12f, 0.26f to 0.06f, 0.3f to 0f, 0.26f to -0.09f, 0.1f to -0.1f, -0.08f to -0.09f, -0.12f to 0f)
private val DRAWN_TOP = edges(0f to 0.09f, 0.15f to 0.12f, 0.26f to 0.08f, 0.3f to 0f, 0.26f to -0.08f, 0.15f to -0.12f, 0f to -0.09f)

/** A dog's front paws with no toes, then three, then none and drawn, through the real build and scene, left for a person to read. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class PawToesSmokeTest {
    private val work = TestWork()

    @Test
    fun thePawReadsInTheRestPoseAtEachSetting() = runComposeUiTest {
        val folder = TemplateFolder.shipped()
        val state = AppState(folder, work)
        val plan = folder.plans.first()
        val new = newCharacter(plan, folder, "Fennec")
        val session = state.open(new.document)
        new.commands.forEach(session::send)
        fun place(socketName: String): List<PartInstance> {
            val socket = openSockets(session.document.value, plan, folder).first { it.name == socketName }
            return placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value).also { session.send(AddParts(it)) }
        }
        place("front-leg")
        val paw = place("paw").first().id
        val errors = CopyOnWriteArrayList<String>()
        fun keep(name: String, toes: Int) {
            settle(work) {
                session.graph.settled.value &&
                    session.assembly.output.value?.part(paw)?.surface?.patches?.count { it.name.startsWith("toes:") } == toes
            }
            val selection = assertNotNull(sheetSelection(assertNotNull(session.assembly.output.value), paw, emptyMap()))
            val image = renderPanes(selection, SMOKE_LOOK, sheetPaneRects(PAW_WIDTH, PAW_HEIGHT, 2), PAW_WIDTH, PAW_HEIGHT, errors::add)
            keepImage(image.pixels, image.width, name)
            assertNotBlank(image.pixels, name)
        }
        keep("paw-toes-0", 0)
        session.send(SetParameter(paw, "toes", 3f))
        keep("paw-toes-3", 3)
        session.send(SetParameter(paw, "toes", 0f))
        session.send(SetOutline(paw, View.SIDE, DRAWN_SIDE))
        session.send(SetOutline(paw, View.TOP, DRAWN_TOP))
        keep("paw-toes-0-drawn", 0)
        session.send(SetOutline(paw, View.SIDE, AROUND_THE_LEG))
        session.send(SetOutline(paw, View.TOP, null))
        keep("paw-around-the-leg", 0)
        assertEquals(emptyList(), errors)
    }
}
