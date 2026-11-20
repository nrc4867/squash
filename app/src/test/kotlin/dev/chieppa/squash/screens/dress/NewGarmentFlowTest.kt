package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.shownTucks
import dev.chieppa.squash.character.dress.tuckCommand
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SNUG_FIT
import dev.chieppa.squash.document.SetPartFit
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.runBlocking
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_garment_name
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900

private object NoGarmentFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** New garment, and Fit a part on a painted, shaped tail, on the bare fennec through the app whole. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class NewGarmentFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.bareOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoGarmentFiles) }
        settle(work, state)
        key(Key.Six)
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** New garment from its key, its names read before the key so the step it sends waits on nothing, settled once it is picked. */
    private fun ComposeUiTest.newGarmentByKey(state: AppState): Garment {
        runBlocking {
            newGarmentNames(assertNotNull(state.session.value).document.value.character.wardrobe)
            getString(Res.string.dress_garment_name, 1)
        }
        key(Key.N)
        settle(work, state) { state.dress.garment.value != null }
        return garment(state)
    }

    private fun ComposeUiTest.newGarment(state: AppState): Garment {
        runBlocking { state.newGarment() }
        settle(work, state)
        return garment(state)
    }

    private fun garment(state: AppState): Garment {
        val wardrobe = assertNotNull(state.session.value).document.value.character.wardrobe
        return assertNotNull(state.dress.garment.value?.let(wardrobe::garment))
    }




    @Test
    fun newGarmentIsWornAtOnceInItsOwnColorWithItsSlotAndOutfitInOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = bareOpen()
        val session = assertNotNull(state.session.value)
        val steps = session.history.steps.value.done.size
        val garment = newGarmentByKey(state)
        val character = session.document.value.character
        assertEquals(steps + 1, session.history.steps.value.done.size, "one step")
        assertEquals(listOf(garment.id), character.wardrobe.worn.map { it.id }, "worn at once")
        assertEquals(1, character.wardrobe.slots.size)
        assertEquals(1, character.wardrobe.outfits.size)
        assertEquals(character.wardrobe.outfits.single().id, character.wardrobe.shown)
        val color = assertNotNull(garment.color)
        assertEquals(garmentColor(garment.id), character.palette.color(color), "a color of its own")
        assertEquals(SNUG_FIT, garment.fit)
        assertEquals(BuildTool.PAINT, state.tool.value, "Paint is armed")
        state.undo()
        assertTrue(session.document.value.character.wardrobe.garments.isEmpty())
        assertTrue(session.document.value.character.wardrobe.slots.isEmpty())
    }

    @Test
    fun fitAPartAddsAnOverrideRowItsCrossTakesAway() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = bareOpen()
        newGarment(state)
        val session = assertNotNull(state.session.value)
        val body = assertNotNull(session.assembly.output.value)
        GarmentPaintTool(state, work.scope, SMOKE_LOOK).lay(bandStroke(session.document.value.character, body, TAIL, 0f, 1f))
        settle(work, state)
        val tucked = shownTucks(session.document.value.character, body, garment(state), null).single { it.part == TAIL }
        session.send(tuckCommand(session.document.value.character, body, garment(state), tucked, tucked = false))
        settle(work, state)
        onNodeWithTag(DressTags.FIT_PART).performScrollTo().performClick()
        waitForIdle()
        val tail = assertNotNull(session.document.value.character.part(TAIL))
        onAllNodesWithText(partName(tail, state.templates)).onLast().performClick()
        settle(work, state)
        val fitted = assertIs<SetPartFit>(session.history.steps.value.done.last())
        assertEquals(TAIL, fitted.part)
        onNodeWithTag(DressTags.partFit(TAIL)).assertExists()
        onNodeWithTag(DressTags.removeFit(TAIL)).performScrollTo().performClick()
        settle(work, state)
        assertEquals(emptyMap(), garment(state).partFits)
    }
}
