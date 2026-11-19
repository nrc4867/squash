package dev.chieppa.squash.screens.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetHemInk
import dev.chieppa.squash.document.SetLayers
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.dress.movedLayer
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
import dev.chieppa.squash.screens.fennec.BOOTS
import dev.chieppa.squash.screens.fennec.CAP
import dev.chieppa.squash.screens.fennec.HOODED
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SWEATER
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.WINTER
import dev.chieppa.squash.screens.fennec.savedFennecDressed
import dev.chieppa.squash.screens.library.LibraryTab
import dev.chieppa.squash.screens.settle
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900

/** From a layer row's content back to the middle of its grip. */
private val GRIP_REACH = 12.dp
private const val DRAG_STEPS = 20

private object NoDressFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Dress stage in the app whole, over the dressed fennec: W13's strip, inspector and status, W14's band, popover and layers, the Tuck rows. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class DressFlowTest {

    private val work = TestWork()

    private fun ComposeUiTest.dressedOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennecDressed())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoDressFiles) }
        settle(work, state)
        waitForIdle()
        key(Key.Six)
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun sixShowsDressWithItsStripInspectorAndStatusAndSevenShowsPose() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        assertEquals(Stage.DRESS, state.stage.value)
        assertEquals(BuildTool.PAINT, state.tool.value, "Dress opens on Paint")
        for (tool in listOf(BuildTool.PAINT, BuildTool.HOLE, BuildTool.EDIT_GARMENT, BuildTool.ERASE_GARMENT, BuildTool.SMOOTH_GARMENT, BuildTool.LOOSE_END)) {
            onNodeWithTag(ShellTags.tool(tool)).assertExists()
        }
        onNodeWithTag(ShellTags.NEW_GARMENT).assertExists()
        onNodeWithTag(ShellTags.SILHOUETTE).assertExists()
        onNodeWithTag(ShellTags.MIRROR).assertExists()
        assertEquals(GarmentLook.SHOWN, state.dress.lookInDress.value)
        assertEquals(SCARF, state.dress.garment.value, "the outermost worn garment is picked")
        state.dress.garment.value = SWEATER
        waitForIdle()
        onNodeWithTag(DressTags.FIT).assertExists()
        onNodeWithTag(ShellTags.STATUS_STAGE).onChildren().onFirst().assertTextContains("Dress · Paint", substring = true)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("Sweater · Body · 1 area", substring = true)
        val steps = session.history.steps.value.done.size
        onNodeWithTag(DressTags.INK_HEM).performScrollTo().performClick()
        waitForIdle()
        val inked = assertIs<SetHemInk>(session.history.steps.value.done.last())
        assertEquals(false, inked.inked)
        state.undo()
        assertEquals(steps, session.history.steps.value.done.size)

        key(Key.N)
        settle(work, state)
        waitForIdle()
        val added = session.document.value.character.wardrobe.garments.last()
        assertEquals(added.id, state.dress.garment.value, "New garment picks what it adds")
        assertTrue(added.id in session.document.value.character.wardrobe.worn.map { it.id }, "and wears it in the shown outfit")
        state.undo()

        key(Key.Seven)
        assertEquals(Stage.POSE, state.stage.value)
        key(Key.Six)
        assertEquals(Stage.DRESS, state.stage.value)
    }

    @Test
    fun garmentsShowOutfitsAndSlotsAndTheLayersListNamesWhoWins() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        state.library.tab.value = LibraryTab.GARMENTS
        waitForIdle()
        onNodeWithTag(DressTags.outfit(HOODED), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(HOODED, assertIs<ShowOutfit>(session.history.steps.value.done.last()).outfit)
        state.undo()
        waitForIdle()
        assertEquals(WINTER, session.document.value.character.wardrobe.shown)

        onNodeWithTag(DressTags.garmentTile(BOOTS), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        val pick = assertIs<PickGarment>(session.history.steps.value.done.last())
        assertNull(pick.garment, "a press on the worn boots takes them off")
        state.undo()
        waitForIdle()

        onNodeWithTag(DressTags.layer(SCARF), useUnmergedTree = true).assertExists()
        onNodeWithTag(DressTags.clash(TORSO)).assertExists()
        onNodeWithText("Scarf over Sweater", useUnmergedTree = true).assertExists()
        onNodeWithTag(DressTags.layerShow(SCARF)).performScrollTo().performClick()
        waitForIdle()
        assertTrue(SCARF in state.dress.hidden.value)
        assertTrue(SCARF in session.garmentView.value.hidden, "a garment out of view is left out of the session's dressing")
    }

    @Test
    fun theBootsToesRowShapesThemAndThePawBuildsItsToesAgain() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        state.dress.garment.value = BOOTS
        waitForIdle()
        onNodeWithTag(DressTags.tuck(BACK_PAW_L, TuckKind.TOES)).assertExists()
        onNode(hasText("Shaped") and hasAnyAncestor(hasTestTag(DressTags.tuckChoice(BACK_PAW_L, TuckKind.TOES))), useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state)
        assertIs<Together>(session.history.steps.value.done.last())
        val shaped = session.document.value.character.wardrobe.garment(BOOTS)?.changes?.get(BACK_PAW_L)
        assertNull((shaped as? BodyPartChange.Redraw)?.data?.params?.get("toes"), "shaped toes build as drawn, their claws tucked until chosen: $shaped")
        state.undo()
        assertIs<BodyPartChange.Redraw>(session.document.value.character.wardrobe.garment(BOOTS)?.changes?.get(BACK_PAW_L))
    }

    @Test
    fun draggingALayersGripLiftsItsRowAndMovesItInOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        val outerFirst = session.document.value.character.wardrobe.worn.reversed().map { it.id }
        val moving = outerFirst.first()
        val name = assertNotNull(session.document.value.character.wardrobe.garment(moving)).name
        val row = onNodeWithTag(DressTags.layer(moving), useUnmergedTree = true)
        row.performScrollTo()
        waitForIdle()
        val bounds = row.getUnclippedBoundsInRoot()
        val steps = session.history.steps.value.done.size
        fun shown() = onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes().size
        val named = shown()
        val grip = with(density) { Offset((bounds.left - GRIP_REACH).toPx(), ((bounds.top + bounds.bottom) / 2).toPx()) }
        val slot = with(density) { (bounds.bottom - bounds.top).toPx() }
        onAllNodes(isRoot()).onFirst().performMouseInput {
            moveTo(grip)
            press()
            repeat(DRAG_STEPS) { moveBy(Offset(0f, 2 * slot / DRAG_STEPS)) }
        }
        waitForIdle()
        assertEquals(named + 1, shown(), "the row lifts and follows the pointer")
        assertEquals(steps, session.history.steps.value.done.size, "nothing moves before the grip lets go")
        onAllNodes(isRoot()).onFirst().performMouseInput { release() }
        settle(work, state)
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size)
        assertIs<SetLayers>(session.history.steps.value.done.last())
        val after = session.document.value.character.wardrobe.worn.reversed().map { it.id }
        assertEquals(movedLayer(outerFirst, 0, 2), after)
        assertEquals(named, shown())
    }

    @Test
    fun pickingALayerLeavesItsRowWhereItWas() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        for (garment in listOf(SWEATER, CAP, BOOTS, SCARF)) {
            val row = onNodeWithTag(DressTags.layer(garment), useUnmergedTree = true)
            row.performScrollTo()
            waitForIdle()
            val before = row.getUnclippedBoundsInRoot().top
            row.performClick()
            waitForIdle()
            assertEquals(garment, state.dress.garment.value)
            assertEquals(before, row.getUnclippedBoundsInRoot().top, "picking $garment moved its row")
        }
    }
}
