package dev.chieppa.squash.screens.dress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.dress.bodyChart
import dev.chieppa.squash.character.dress.placeOnBody
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.surface.CLOTH_HEM_LEVEL
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetGarmentMark
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.Stage
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.squash.screens.woman.HEAD
import dev.chieppa.squash.screens.woman.NECK
import dev.chieppa.squash.screens.woman.savedWoman
import dev.chieppa.squash.screens.woman.TORSO
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import org.junit.jupiter.api.Tag
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900

/** Where the tee's cloth runs on the torso, in u. */
private const val TEE_FROM = 0.2f
private const val TEE_TO = 0.97f

/** A view straight down the x axis, as Front shows a character facing x: z to the left and y up, a place nearer the viewer with a larger x. */
private class FrontSight(private val scale: Float = 400f, private val origin: Offset = Offset(500f, 500f)) : SurfaceView {
    override fun toPixel(point: Vec3): Offset = Offset(origin.x - point.z * scale, origin.y - point.y * scale)

    override fun toViewer(point: Vec3): Vec3 = Vec3(1f, 0f, 0f)

    override fun depthOf(point: Vec3): Float = -point.x

    override fun pixelsPerUnitAt(point: Vec3): Float = scale
}

private object NoPrintFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Dress's Print region and Print line on a tee painted on the woman, and the inspector's prints and area colours. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class PrintFlowTest {
    private val work = TestWork()
    private val sight = FrontSight()

    @AfterTest
    fun close() = work.close()

    /** The app over the woman wearing a tee painted round her torso, picked. */
    private fun dressed(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        val session = state.open(savedWoman())
        work.settle(session)
        val character = session.document.value.character
        val tee = newGarmentIn(character.wardrobe) { "Tee" }
        session.send(character.newGarmentCommand(tee, null, NewGarmentNames("Tee", "Tee")))
        work.settle(session)
        state.dress.garment.value = tee.id
        GarmentPaintTool(state, work.scope, SMOKE_LOOK).lay(bandStroke(state.character, state.body, TORSO, TEE_FROM, TEE_TO))
        work.settle(session)
        return state
    }

    private val AppState.open: CharacterSession get() = assertNotNull(session.value)
    private val AppState.character: Character get() = open.document.value.character
    private val AppState.body: AssembledCharacter get() = assertNotNull(open.assembly.output.value)
    private val AppState.garment: Garment get() = assertNotNull(dress.garment.value?.let(character.wardrobe::garment))
    private val AppState.worn: SurfaceGarment get() = assertNotNull(open.sessionSurface.dress.output.value?.dress?.garments?.single())

    /** The pixel the body's [part] shows at, at [u] and [v], through [sight]. */
    private fun AppState.pixelOf(part: PartId, u: Float, v: Float): Offset {
        val patch = assertNotNull(bodyChart(character, body).part(part)).patch
        return sight.toPixel(assertNotNull(placeOnBody(body, Anchor(part, patch, u, v))))
    }

    @Test
    fun printsAnchorOnTheClothInFrontOfTheBodyAndPaintTheKeptGarmentAgain() {
        val state = dressed()
        val middle = state.pixelOf(TORSO, 0.6f, 0.0f)
        val hit = assertNotNull(state.garmentHit(state.garment.id, middle, sight), "the cloth over the torso's front is found")
        assertEquals(TORSO, hit.part)
        assertNull(state.garmentHit(state.garment.id, state.pixelOf(HEAD, 0.5f, 0.0f), sight), "the bare head is no cloth")
        val border = listOf(Offset(-12f, -12f), Offset(12f, -12f), Offset(12f, 12f), Offset(-12f, 12f)).map {
            assertNotNull(state.garmentHit(state.garment.id, middle + it, sight)).anchor
        }
        val before = state.worn
        val layers = before.paint?.layers?.size ?: 0
        state.open.send(assertNotNull(printRegionCommand(state.garment, border, SlotName("cream"), ink = true, mirrored = true)))
        work.settle(state.open)
        val printed = state.worn
        assertSame(before.surface, printed.surface, "a print builds nothing: the garment's grown surface is kept")
        assertEquals(layers + 2, printed.paint?.layers?.size, "the region and its other side are painted on the cloth")
        val borders = assertNotNull(printed.paint).marks.values.flatten()
        assertEquals(2, borders.size, "each side's border is inked")
        val line = listOf(state.pixelOf(TORSO, 0.5f, 0.0f), state.pixelOf(TORSO, 0.95f, 0.0f)).map {
            assertNotNull(state.garmentHit(state.garment.id, it, sight)).anchor
        }
        val neck = Anchor(NECK, line.last().patch, 0.6f, 0.0f)
        state.open.send(assertNotNull(printMarkCommand(state.garment, line + neck, null, 0.5f, MarkWidth(), mirrored = false)))
        work.settle(state.open)
        val mark = state.garment.marks.single()
        assertEquals(listOf(0.5f, 0.5f), mark.width.stops.map { it.scale }, "drawn at the print weight from end to end")
        val lines = assertNotNull(state.worn.paint).marks.values.flatten().filter { it.source == mark.id }
        assertTrue(lines.isNotEmpty(), "the line is drawn on the cloth")
        val cloth = state.worn.cover
        assertTrue(
            lines.flatMap { it.points }.all { (cloth.clothOf(it.anchor.part)?.at(it.anchor.u, it.anchor.v) ?: 0f) >= CLOTH_HEM_LEVEL },
            "and ends where the cloth does, short of the bare neck",
        )
        state.open.send(SetGarmentMark(state.garment.id, mark.id, null))
        work.settle(state.open)
        assertEquals(2, assertNotNull(state.worn.paint).marks.values.flatten().size, "taken away, only the region's borders are left")
    }

    @Test
    fun theInspectorListsPrintsTakesThemAwayAndColorsAnArea() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoPrintFiles) }
        settle(work, state)
        state.showStage(Stage.DRESS)
        state.armTool(BuildTool.PRINT_REGION)
        settle(work, state)
        onNodeWithTag(DressTags.PRINT_INK).assertExists()
        val middle = state.pixelOf(TORSO, 0.6f, 0.0f)
        val border = listOf(Offset(-12f, -12f), Offset(12f, -12f), Offset(0f, 12f)).map { assertNotNull(state.garmentHit(state.garment.id, middle + it, sight)).anchor }
        state.open.send(assertNotNull(printRegionCommand(state.garment, border, SlotName("cream"), ink = true, mirrored = false)))
        settle(work, state)
        val id = state.garment.regions.single().id.value
        onNodeWithTag(DressTags.removePrint(id)).performScrollTo().performClick()
        settle(work, state)
        assertEquals(emptyList(), state.garment.regions)
        val area = state.garment.areas.first().id
        onNodeWithTag(DressTags.areaColor(area)).performScrollTo().performClick()
        settle(work, state)
        onNodeWithTag(DressTags.areaColor(area) + ".cream").performClick()
        settle(work, state)
        assertEquals(SlotName("cream"), state.garment.area(area)?.color)
        onNodeWithTag(DressTags.areaColor(area)).performScrollTo().performClick()
        settle(work, state)
        onNodeWithTag(DressTags.areaGarmentColor(area)).performClick()
        settle(work, state)
        assertNull(state.garment.area(area)?.color, "back to the garment's color")
    }
}
