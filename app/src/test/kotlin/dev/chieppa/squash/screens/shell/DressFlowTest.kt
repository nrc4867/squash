package dev.chieppa.squash.screens.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.GarmentLook
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.SetBodyPartChange
import dev.chieppa.squash.document.SetEdgeInk
import dev.chieppa.squash.document.SetLooseEnd
import dev.chieppa.squash.screens.dress.DressHit
import dev.chieppa.squash.screens.dress.LooseEndTool
import dev.chieppa.squash.screens.fennec.SCARF_END
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.fennec.CAP
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.FEET_SLOT
import dev.chieppa.squash.screens.fennec.HOODED
import dev.chieppa.squash.screens.fennec.SCARF
import dev.chieppa.squash.screens.fennec.SWEATER
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.WINTER
import dev.chieppa.squash.screens.fennec.savedFennecDressed
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

private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoDressFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** The Dress stage in the app whole, over the dressed fennec: W13's strip, inspector and status, W14's band, popover and layers, W15's body under a garment. */
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
        assertEquals(BuildTool.DRAW_GARMENT, state.tool.value, "Dress opens on Draw garment")
        for (tool in listOf(BuildTool.DRAW_GARMENT, BuildTool.EDIT_GARMENT, BuildTool.ERASE_GARMENT, BuildTool.SMOOTH_GARMENT, BuildTool.COVER, BuildTool.LOOSE_END)) {
            onNodeWithTag(ShellTags.tool(tool)).assertExists()
        }
        onNodeWithTag(ShellTags.NEW_GARMENT).assertExists()
        assertEquals(GarmentLook.SHOWN, state.dress.lookInDress.value)
        assertEquals(SCARF, state.dress.garment.value, "the outermost worn garment is picked")
        state.dress.garment.value = SWEATER
        waitForIdle()
        onNodeWithTag(DressTags.covers(TORSO)).assertExists()
        onNodeWithTag(DressTags.join(dev.chieppa.squash.screens.fennec.FRONT_LEG_L)).assertExists()
        onNodeWithTag(ShellTags.STATUS_STAGE).onChildren().onFirst().assertTextContains("Dress · Draw garment", substring = true)
        onNodeWithTag(ShellTags.STATUS_END).onChildren().onFirst().assertTextContains("Sweater · Body · 2 branches · 1 join", substring = true)
        val steps = session.history.steps.value.done.size
        onNodeWithTag(DressTags.INK_HEM).performClick()
        waitForIdle()
        val inked = assertIs<SetEdgeInk>(session.history.steps.value.done.last())
        assertEquals(false, inked.hem)
        state.undo()
        assertEquals(steps, session.history.steps.value.done.size)

        key(Key.N)
        settle(work, state)
        waitForIdle()
        val added = session.document.value.character.wardrobe.garments.last()
        assertEquals(added.id, state.dress.garment.value, "New garment picks what it adds")
        state.undo()

        key(Key.Seven)
        assertEquals(Stage.POSE, state.stage.value)
        key(Key.Six)
        assertEquals(Stage.DRESS, state.stage.value)
    }

    @Test
    fun theBandShowsOutfitsAndSlotsAndTheLayersListNamesWhoWins() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        onNodeWithTag(ShellTags.DRESS_BAND).assertExists()
        onNodeWithTag(DressTags.outfit(HOODED)).performClick()
        waitForIdle()
        assertEquals(HOODED, assertIs<ShowOutfit>(session.history.steps.value.done.last()).outfit)
        state.undo()
        waitForIdle()
        assertEquals(WINTER, session.document.value.character.wardrobe.shown)

        onNodeWithTag(DressTags.slot(FEET_SLOT)).performClick()
        waitForIdle()
        onNodeWithTag(DressTags.POPOVER).assertExists()
        onNodeWithTag(DressTags.PICK_NONE).performClick()
        waitForIdle()
        val pick = assertIs<PickGarment>(session.history.steps.value.done.last())
        assertNull(pick.garment, "None takes the feet's garment off")
        state.undo()
        state.dress.openSlot.value = null
        waitForIdle()

        onNodeWithTag(DressTags.layer(SCARF)).assertExists()
        onNodeWithTag(DressTags.clash(TORSO)).assertExists()
        onNodeWithText("Scarf over Sweater", useUnmergedTree = true).assertExists()
        onNodeWithTag(DressTags.layerShow(SCARF)).performScrollTo().performClick()
        waitForIdle()
        assertTrue(SCARF in state.dress.hidden.value)
        assertTrue(SCARF in session.garmentView.value.hidden, "a garment out of view is left out of the session's dressing")
    }

    @Test
    fun bodyUnderTheCapSetsThroughOnTheEarsWithItsCuff() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        state.dress.garment.value = CAP
        waitForIdle()
        onNodeWithTag(DressTags.change(EAR_L)).assertExists()
        onNodeWithTag(DressTags.hole(EAR_L)).assertExists()
        onNode(hasText("Through") and hasAnyAncestor(hasTestTag(DressTags.change(EAR_L))), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        val change = assertIs<SetBodyPartChange>(session.history.steps.value.done.last())
        assertIs<BodyPartChange.Through>(change.change)
        onNodeWithTag(DressTags.cuff(EAR_L)).assertExists()
        onNodeWithTag(DressTags.cuffSize(EAR_L)).assertExists()
    }

    @Test
    fun theLooseEndToolHangsAnEndFromAGarmentAndItsSectionSetsFollowThrough() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressedOpen()
        val session = assertNotNull(state.session.value)
        key(Key.L)
        assertEquals(BuildTool.LOOSE_END, state.tool.value)
        onNodeWithTag(DressTags.looseEnd(SCARF_END)).assertExists()
        onNodeWithTag(DressTags.follow(SCARF_END)).performScrollTo().performClick()
        waitForIdle()
        val followed = assertIs<SetLooseEnd>(session.history.steps.value.done.last())
        assertEquals(false, followed.end.followThrough)
        assertEquals(SCARF_END, followed.end.id)
        state.undo()

        val covering = assertNotNull(session.assembly.output.value?.parts?.firstNotNullOfOrNull { it.covering?.takeIf { c -> c.garment == SWEATER && c.part == TORSO } })
        state.dress.looseBones.value = 3
        LooseEndTool(state).pressed(DressHit.OnGarment(covering, 0.75f))
        waitForIdle()
        val hung = assertIs<SetLooseEnd>(session.history.steps.value.done.last())
        assertEquals(SWEATER, hung.garment)
        assertEquals(3, hung.end.bones)
        assertEquals(0.75f, hung.end.v)
        assertEquals(SWEATER, state.dress.garment.value, "the press picks the garment")
        onNodeWithTag(DressTags.looseEnd(hung.end.id)).assertExists()
    }
}
