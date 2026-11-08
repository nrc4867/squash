package dev.chieppa.squash.screens.smoke

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import dev.chieppa.squash.character.build.PlacementParams
import dev.chieppa.squash.character.build.moveSocket
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.edit.TEST_CURVE_LOOK
import dev.chieppa.squash.screens.move.MoveTool
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import dev.chieppa.squash.screens.sheet.sheetSelection
import dev.chieppa.stretch.gpu.GpuOptions
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Tag

private const val MOVE_WIDTH = 1440
private const val MOVE_HEIGHT = 900

private const val LEANED_DEGREES = 25f

/** How far a paw stands above the ground with a pair leaned, in body units. */
private const val LEANED_PAW_RESIDUAL = 0.005f

/** A dog's front legs moved back and leaned out, through the real build and scene, with the Move tool's dots over the panes. */
@Tag("slow")
@OptIn(ExperimentalTestApi::class)
class MoveSmokeTest {
    private val work = TestWork()

    @AfterTest
    fun stopWork() = work.close()

    @Test
    fun legsLeanedOutShowInEveryPaneWithTheirDots() = runComposeUiTest {
        val folder = TemplateFolder.shipped()
        val state = AppState(folder, work)
        val plan = folder.plans.first()
        val new = newCharacter(plan, folder, "Fennec")
        val session = state.open(new.document)
        new.commands.forEach(session::send)
        val socket = openSockets(session.document.value, plan, folder).first { it.name == "front-leg" }
        val parts = placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value)
        session.send(AddParts(parts))
        val leg = parts.first { it.side == Side.LEFT }.id
        state.selectedPart.value = leg
        session.send(moveSocket(session.document.value.character, new.root, "front-leg", Side.LEFT, 0.7f, 0.3f, false, thisSide = false))
        session.send(SetParameter(leg, PlacementParams.OUTWARD, 25f))
        session.send(SetParameter(leg, PlacementParams.joint("knee"), 0.35f))
        settle(work) {
            session.graph.settled.value && session.assembly.output.value?.part(leg)?.placement?.containsKey(PlacementParams.OUTWARD) == true
        }
        val character = assertNotNull(session.assembly.output.value)
        val selection = assertNotNull(sheetSelection(character, leg, emptyMap()))
        val rects = sheetPaneRects(MOVE_WIDTH, MOVE_HEIGHT, 2)
        val input = panesInput(selection, SMOKE_LOOK, rects, MOVE_WIDTH, MOVE_HEIGHT)
        val tool = MoveTool(state, SMOKE_LOOK, TEST_CURVE_LOOK)
        val full = assertNotNull(input.full)
        val fullRect = rects[SheetPane.FULL.ordinal]
        val frame = sheetFrame(
            SheetInput(
                rects, MOVE_WIDTH, MOVE_HEIGHT, SMOKE_LOOK, selection, input.cameras, full,
                toolOverlays = input.cameras.mapValues { (view, camera) -> tool.overlay(view, camera) },
                fullOverlay = tool.fullOverlay(FullView({ full }, fullRect.width, fullRect.height)),
            ),
        )
        val errors = CopyOnWriteArrayList<String>()
        val image = renderSnapshot(frame, MOVE_WIDTH, MOVE_HEIGHT, options = GpuOptions(onError = errors::add)) { ViewsScene(it) }
        keepImage(image.pixels, image.width, "move-sheet")
        assertEquals(emptyList(), errors)
        assertNotBlank(image.pixels, "the panes")
        assertNotEquals(0, image.countIn(rects[SheetPane.FULL.ordinal], SMOKE_LOOK.active), "the selected leg's dot shows in Full")
    }

    @Test
    fun aFrontPairLeanedOutStandsOnTheGroundWithTheOtherLegsShortened() = runComposeUiTest {
        leanedDog(PlacementParams.OUTWARD, "move-leaned")
    }

    @Test
    fun aFrontPairLeanedForwardStandsOnTheGroundWithTheOtherLegsShortened() = runComposeUiTest {
        leanedDog(PlacementParams.LEAN, "move-leaned-forward")
    }

    private fun ComposeUiTest.leanedDog(angle: String, image: String) {
        val folder = TemplateFolder.shipped()
        val state = AppState(folder, work)
        val plan = folder.plans.first()
        val new = newCharacter(plan, folder, "Fennec")
        val session = state.open(new.document)
        new.commands.forEach(session::send)
        fun place(name: String) = openSockets(session.document.value, plan, folder).first { it.name == name }.let { socket ->
            placeOnSocket(socket, assertNotNull(folder.template(socket.takes.first())), session.document.value).also { session.send(AddParts(it)) }
        }
        val leg = place("front-leg").first { it.side == Side.LEFT }.id
        place("back-leg")
        state.selectedPart.value = leg
        session.send(SetParameter(leg, angle, LEANED_DEGREES))
        settle(work) {
            session.graph.settled.value && session.assembly.output.value?.part(leg)?.placement?.containsKey(angle) == true
        }
        val character = assertNotNull(session.assembly.output.value)
        val paws = character.parts.filter { it.template.id.value == "paw" }
        assertEquals(4, paws.size)
        paws.forEach { assertEquals(character.floor, assertNotNull(it.box).low.y, LEANED_PAW_RESIDUAL, "every paw on the ground") }
        val selection = assertNotNull(sheetSelection(character, leg, emptyMap()))
        val rects = sheetPaneRects(MOVE_WIDTH, MOVE_HEIGHT, 2)
        val errors = CopyOnWriteArrayList<String>()
        val rendered = renderPanes(selection, SMOKE_LOOK, rects, MOVE_WIDTH, MOVE_HEIGHT, errors::add)
        keepImage(rendered.pixels, rendered.width, image)
        assertEquals(emptyList(), errors)
        assertNotBlank(rendered.pixels, "the panes")
    }
}
