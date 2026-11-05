package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.geom.BodyView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import dev.chieppa.squash.geom.Vec3
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddPart
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.SetOutline
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.View
import dev.chieppa.squash.screens.AppState
import dev.chieppa.stretch.test.setStretchContent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalTestApi::class)
class SheetUiTest {
    private val folder = TemplateFolder.load(Path.of(System.getProperty("squash.templates")))

    @Test
    fun aNarrowStripFoldsTheLayoutControlIntoADropdown() = runDesktopComposeUiTest(700, 300) {
        var picked: SheetLayout? = null
        setStretchContent { SheetStrip(onLayout = { picked = it }) }
        onNodeWithText("Layout").assertDoesNotExist()
        onNodeWithText("Sheet").performClick()
        waitForIdle()
        onNodeWithText("Single").performClick()
        assertEquals(SheetLayout.SINGLE, picked)
    }

    @Test
    fun singleSheetAndFullFirstAreTheLayoutsThatCanBePicked() = runDesktopComposeUiTest(1900, 900) {
        var picked: SheetLayout? = null
        setStretchContent { SheetStrip(onLayout = { picked = it }) }
        onNodeWithText("Layout").assertExists()
        onNodeWithText("Sheet").assertIsSelected()
        for (label in listOf("Single", "Sheet", "Full first")) onNodeWithText(label).assertIsEnabled()
        onNodeWithText("Focus").assertIsNotEnabled()
        onNodeWithText("Single").performClick()
        assertEquals(SheetLayout.SINGLE, picked)
        onNodeWithText("Full first").performClick()
        assertEquals(SheetLayout.FULL_FIRST, picked)
    }

    @Test
    fun panesSitWhereTheRenderThreadDrawsThem() = runComposeUiTest {
        val placed = mutableMapOf<SheetPane, Pair<IntOffset, IntSize>>()
        setStretchContent {
            Box(Modifier.size(600.dp, 400.dp)) {
                SheetPaneGrid(2, Modifier.fillMaxSize()) { pane ->
                    Box(
                        Modifier.fillMaxSize().onGloballyPositioned {
                            val at = it.positionInRoot()
                            placed[pane] = IntOffset(at.x.toInt(), at.y.toInt()) to it.size
                        },
                    )
                }
            }
        }
        waitForIdle()
        val rects = with(density) { sheetPaneRects(600.dp.roundToPx(), 400.dp.roundToPx(), 2) }
        for (pane in SheetPane.entries) {
            val rect = rects[pane.ordinal]
            assertEquals(IntOffset(rect.x, rect.y) to IntSize(rect.width, rect.height), placed[pane], "$pane")
        }
    }

    @Test
    fun aPaneShowsItsLabelAndChipInPopupsOverTheSurface() = runComposeUiTest {
        setStretchContent {
            Box(Modifier.size(400.dp, 300.dp)) { PaneViewport(BodyView.SIDE, PaneChip.DRAWING, zoom = 0.86f) }
        }
        onNodeWithText("Side · part axes").assertExists()
        onNodeWithText("Drawing").assertExists()
        onNodeWithText("86%").assertExists()
    }

    @Test
    fun aFlippedPaneNamesTheSideItLooksFromAndItsLabelFlipsIt() = runComposeUiTest {
        var flips = 0
        setStretchContent {
            Box(Modifier.size(400.dp, 300.dp)) { PaneViewport(BodyView.TOP, null, flipped = true, onFlip = { flips++ }) }
        }
        onNodeWithText("Bottom · part axes").assertExists()
        onNodeWithTag(paneFlipTag(BodyView.TOP)).performClick()
        waitForIdle()
        assertEquals(1, flips)
    }

    @Test
    fun aToolsPointerInputGetsPanePixelsThatMapToPartSpace() = runComposeUiTest {
        val axes = PartAxes.of(Vec3(1f, 0f, 0f))
        var pressed: Offset? = null
        var camera by mutableStateOf<LockedCamera?>(null)
        val tool = object : PaneTool {
            override fun pointerInput(view: View, camera: LockedCamera) =
                Modifier.pointerInput(camera) { detectTapGestures { pressed = it } }
        }
        setStretchContent {
            Box(Modifier.size(600.dp, 400.dp).onSizeChanged { size ->
                val rects = sheetPaneRects(size.width, size.height, 2)
                val side = rects[SheetPane.SIDE.ordinal]
                camera = LockedCamera(View.SIDE, axes, SheetFit(Vec3.ZERO, 100f), side.width, side.height)
            }) {
                camera?.let { locked ->
                    SheetPaneGrid(2, Modifier.fillMaxSize()) { pane ->
                        val input = if (pane == SheetPane.SIDE) tool.pointerInput(View.SIDE, locked).testTag("side") else Modifier
                        PaneViewport(if (pane == SheetPane.SIDE) BodyView.SIDE else null, null, input)
                    }
                }
            }
        }
        onNodeWithTag("side").performMouseInput { click(Offset(60f, 120f)) }
        val locked = assertNotNull(camera)
        assertEquals(Offset(60f, 120f), pressed, "from the pane's own corner")
        val part = locked.toWorld(Offset(60f, 120f))
        assertEquals(Offset(60f, 120f).x, locked.toPixel(part).x, 0.01f)
    }

    @Test
    fun theSelectedPartComesThroughTheRealBuild() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val state = AppState(folder)
        val id = PartId("p")
        val document = SquashDocument(Character(CharacterId("c"), "Fennec", BodyPlanRef(BodyPlanId("p"), 1)))
        val session = CharacterSession(document, TemplateLibrary(folder.parts), scope)
        val leg = folder.parts.first { it.id.value == "leg" }
        session.send(AddPart(PartInstance(id, leg.ref)))
        var selection: SheetSelection? = null
        setStretchContent { selection = rememberSheetSelection(state) }
        waitForIdle()
        assertNull(selection, "nothing is open yet, so the panes stay empty")

        state.session.value = session
        state.selectedPart.value = id
        waitUntil(timeoutMillis = 10_000) { session.graph.settled.value && selection != null }
        val built = assertNotNull(selection)
        assertTrue(built.part.mesh.vertexCount > 0)
        assertEquals(id, built.part.id)
        assertTrue(built.outlines.isEmpty())

        val curve = OutlineCurve(listOf(CurvePoint(0f, 0f), CurvePoint(0f, 1f), CurvePoint(1f, 1f), CurvePoint(1f, 0f)))
        session.send(SetOutline(id, View.SIDE, curve))
        waitUntil(timeoutMillis = 10_000) { selection?.outlines?.containsKey(View.SIDE) == true }
        scope.cancel()
    }
}
