package dev.chieppa.squash.screens.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.AddWornGarment
import dev.chieppa.squash.document.DuplicateOutfit
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.RemoveOutfit
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.addPart
import dev.chieppa.squash.screens.dress.DressTags
import dev.chieppa.squash.screens.fennec.BOOTS
import dev.chieppa.squash.screens.fennec.FEET_SLOT
import dev.chieppa.squash.screens.fennec.WINTER
import dev.chieppa.squash.screens.fennec.savedFennecDressed
import dev.chieppa.squash.screens.library.LibraryTab
import dev.chieppa.squash.screens.library.Renaming
import dev.chieppa.squash.screens.openSocketsNow
import dev.chieppa.squash.screens.settle
import dev.chieppa.stretch.document.FileChooser
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.stretch.frame.DragAndDropHost
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1600
private const val HEIGHT = 900

/** Long enough on the test clock that two presses are two, not a double-click. */
private const val APART_MS = 1000L

private object NoLibraryFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

private const val LIFT_STEPS = 10
private const val LIFT_STEP = 20f

/** How far, in dp, a turned preview's bounds may reach past the tile's. */
private const val LIFT_SLACK = 8f

/** Library's flows F1 to F6 and its keys, in the app whole. See the rethink's spec, boards L1 to L7. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class LibraryFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.created(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        setStretchContent { DragAndDropHost { SquashApp(state, NoLibraryFiles) } }
        onNodeWithTag(ShellTags.NEW_NAME).performTextInput("Fennec")
        onNodeWithTag(ShellTags.NEW_CREATE).performClick()
        settle(work, state)
        return state
    }

    private fun ComposeUiTest.dressed(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennecDressed())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoLibraryFiles) }
        settle(work, state)
        state.library.tab.value = LibraryTab.GARMENTS
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.workspaceKey(key: Key, ctrl: Boolean = false) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { if (ctrl) withKeyDown(Key.CtrlLeft) { pressKey(key) } else pressKey(key) }
        waitForIdle()
    }

    /** Adds [type] on the first open socket named [socket], as Library does. */
    private fun AppState.add(socket: String, type: String) {
        offerSocket(openSocketsNow().first { it.name == socket })
        addPart(TemplateId(type))
    }

    @Test
    fun f1ATailGoesOnItsSocketInOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        val session = assertNotNull(state.session.value)
        onNodeWithTag(ShellTags.partRow("tail")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY).assertExists()
        assertEquals(TemplateId("tail"), state.library.cursor.value, "the first type the socket takes is under the cursor")
        onNodeWithTag(ShellTags.LIBRARY_PICK).assertTextContains("Add tail")
        val steps = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.LIBRARY_PICK).performClick()
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size)
        val tail = session.document.value.character.parts.single { it.socket?.socket == "tail" }
        assertEquals(tail.id, state.selectedPart.value)
        assertNull(state.addingSocket.value, "the target clears")
        assertEquals(TemplateId("tail"), state.library.recent.value.first(), "and the tail leads Recent")
        onNodeWithTag(ShellTags.LIBRARY_RECENT).assertExists()
    }

    @Test
    fun f2ReplacingAPawKeepsItsPairInOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        val session = assertNotNull(state.session.value)
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_PICK).assertTextContains("Replace", substring = true)
        onNodeWithTag(ShellTags.libraryTile("foot"), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        assertEquals(TemplateId("foot"), state.library.cursor.value)
        val steps = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.LIBRARY_PICK).performClick()
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size)
        assertIs<Together>(session.history.steps.value.done.last())
        assertEquals(setOf("foot"), session.document.value.character.parts.filter { it.socket?.socket == "paw" }.map { it.template.id.value }.toSet())
        state.undo()
        assertEquals(setOf("paw"), session.document.value.character.parts.filter { it.socket?.socket == "paw" }.map { it.template.id.value }.toSet())
    }

    @Test
    fun aSelectedLegTakesABirdLegFromATilesMenuAndAnArmFromThePressAndFooterKeepingItsPaws() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        val session = assertNotNull(state.session.value)
        state.add("back-leg", "leg")
        state.add("paw", "paw")
        val leg = session.document.value.character.parts.first { it.socket?.socket == "back-leg" }
        state.selectPart(leg.id)
        waitForIdle()
        fun templatesOn(socket: String) = session.document.value.character.parts.filter { it.socket?.socket == socket }.map { it.template.id.value }

        val steps = session.history.steps.value.done.size
        onNodeWithTag(ShellTags.libraryTile("leg-bird"), useUnmergedTree = true).performScrollTo().performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_MENU_REPLACE).performClick()
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size, "one step")
        assertEquals(listOf("leg-bird", "leg-bird"), templatesOn("back-leg"))
        assertEquals(listOf("paw", "paw"), templatesOn("foot"), "the paws stay on, at the bird legs' ends")
        state.undo()
        assertEquals(listOf("leg", "leg"), templatesOn("back-leg"))
        assertEquals(listOf("paw", "paw"), templatesOn("paw"))

        onNodeWithTag(ShellTags.libraryTile("arm"), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_PICK).performClick()
        waitForIdle()
        assertEquals(listOf("arm", "arm"), templatesOn("back-leg"))
        assertEquals(listOf("paw", "paw"), templatesOn("hand"))

        onNodeWithTag(ShellTags.libraryTile("tail"), useUnmergedTree = true).performScrollTo().performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_MENU_REPLACE).assertDoesNotExist()
    }

    @Test
    fun f3RemovingALegFromItsMenuTakesThePairAndItsPawsInOneStep() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        val session = assertNotNull(state.session.value)
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        waitForIdle()
        val before = session.document.value
        onNodeWithTag(ShellTags.partRow("front-leg")).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(ShellTags.PART_MENU_REPLACE).assertExists()
        onNodeWithTag(ShellTags.PART_MENU_REMOVE).performClick()
        waitForIdle()
        assertEquals(1, session.document.value.character.parts.size)
        assertTrue(state.openSocketsNow().any { it.name == "front-leg" }, "the socket comes back as a + row")
        state.undo()
        assertEquals(before, session.document.value, "one step")
    }

    @Test
    fun aPawsTipsFoldIntoOneRowThatTargetsThemAll() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        state.add("front-leg", "leg")
        state.add("paw", "paw")
        waitForIdle()
        val paw = assertNotNull(state.selectedPart.value)
        if (state.openSocketsNow().none { it.parent == paw && it.name.contains("tip") }) return@runDesktopComposeUiTest
        onNodeWithTag(ShellTags.tipsRow(paw.value)).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(ShellTags.PART_MENU_ADD).performClick()
        waitForIdle()
        assertEquals(paw, state.addingSocket.value?.parent)
    }

    @Test
    fun f4NewInASlotWearsItPicksItAndArmsEdge() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        val session = assertNotNull(state.session.value)
        onNodeWithTag(DressTags.newIn(FEET_SLOT), useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.GARMENT_NAME).fetchSemanticsNodes().isNotEmpty() }
        val added = assertIs<Together>(session.history.steps.value.done.last()).commands.filterIsInstance<AddWornGarment>().single()
        val wardrobe = session.document.value.character.wardrobe
        assertEquals(FEET_SLOT, added.garment.slot)
        assertEquals(added.garment.id, wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT), "worn in the boots' place")
        assertEquals(added.garment.id, state.dress.garment.value)
        assertEquals(BuildTool.PAINT, state.tool.value)
        assertEquals(Renaming.OfGarment(added.garment.id, new = true), state.library.renaming.value)
        onNodeWithTag(DressTags.GARMENT_NAME).performTextReplacement("Socks")
        onNodeWithTag(DressTags.GARMENT_NAME).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Socks", session.document.value.character.wardrobe.garment(added.garment.id)?.name)
    }

    @Test
    fun f5AnOutfitIsMadeDuplicatedRenamedAndRemoved() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        val session = assertNotNull(state.session.value)
        val outfits = session.document.value.character.wardrobe.outfits.size
        onNodeWithTag(DressTags.NEW_OUTFIT, useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state) { session.document.value.character.wardrobe.outfits.size > outfits }
        val made = session.document.value.character.wardrobe
        assertEquals(outfits + 1, made.outfits.size)
        assertEquals(made.outfits.last().id, made.shown, "New shows what it makes")
        assertEquals(emptyMap(), made.outfits.last().picks)

        onNodeWithTag(DressTags.outfit(WINTER), useUnmergedTree = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(DressTags.MENU_DUPLICATE).performClick()
        settle(work, state) { session.history.steps.value.done.lastOrNull() is DuplicateOutfit }
        val copy = session.document.value.character.wardrobe
        assertEquals(copy.outfit(WINTER)?.picks, copy.shown?.let(copy::outfit)?.picks, "Duplicate copies the picks and shows the copy")
        val shown = assertNotNull(copy.shown)

        onNodeWithTag(DressTags.outfit(shown), useUnmergedTree = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(DressTags.MENU_RENAME).performClick()
        waitForIdle()
        onNodeWithTag(DressTags.OUTFIT_NAME).performTextReplacement("Snow")
        onNodeWithTag(DressTags.OUTFIT_NAME).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Snow", session.document.value.character.wardrobe.outfit(shown)?.name)

        onNodeWithTag(DressTags.outfit(shown), useUnmergedTree = true).performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(DressTags.MENU_REMOVE).performClick()
        waitForIdle()
        assertIs<RemoveOutfit>(session.history.steps.value.done.last())
        assertNull(session.document.value.character.wardrobe.shown, "removing the shown outfit shows the bare body")
    }

    @Test
    fun f6APressWearsAndAPressOnTheWornOneLeavesTheSlotEmpty() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        val session = assertNotNull(state.session.value)
        onNodeWithTag(DressTags.garmentTile(BOOTS), useUnmergedTree = true).performScrollTo().performClick()
        waitForIdle()
        assertNull(assertIs<PickGarment>(session.history.steps.value.done.last()).garment)
        assertNull(session.document.value.character.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT))
        mainClock.advanceTimeBy(APART_MS)
        onNodeWithTag(DressTags.garmentTile(BOOTS), useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(BOOTS, session.document.value.character.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT))
    }

    @Test
    fun aDoubleClickOnAGarmentIsOnePress() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        val session = assertNotNull(state.session.value)
        val steps = session.history.steps.value.done.size
        onNodeWithTag(DressTags.garmentTile(BOOTS), useUnmergedTree = true).performScrollTo().performMouseInput { doubleClick() }
        waitForIdle()
        assertEquals(steps + 1, session.history.steps.value.done.size)
        assertNull(session.document.value.character.wardrobe.outfit(WINTER)?.picks?.get(FEET_SLOT), "taken off once, not put back on")
    }

    @Test
    fun shiftF10OpensTheMenuOfTheGarmentUnderTheCursorWithoutTheSlotsItems() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = dressed()
        val session = assertNotNull(state.session.value)
        val feet = assertNotNull(session.document.value.character.wardrobe.slot(FEET_SLOT)).name
        onNodeWithText(feet.uppercase(), useUnmergedTree = true).performScrollTo().performMouseInput { rightClick() }
        waitForIdle()
        onNodeWithTag(DressTags.MENU_WEAR).assertDoesNotExist()
        onNodeWithTag(DressTags.MENU_RENAME_SLOT).performClick()
        waitForIdle()
        onNodeWithTag(DressTags.SLOT_NAME).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        state.library.garmentCursor.value = BOOTS
        onNode(hasContentDescription(feet), useUnmergedTree = true).requestFocus()
        onNode(hasContentDescription(feet), useUnmergedTree = true).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.F10) } }
        waitForIdle()
        onNodeWithTag(DressTags.MENU_WEAR).assertExists()
        onNodeWithTag(DressTags.MENU_RENAME_SLOT).assertDoesNotExist()
    }

    @Test
    fun ctrlLShowsAndHidesLibraryAndCtrlFSearchesIt() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        onNodeWithTag(ShellTags.LIBRARY).assertExists()
        workspaceKey(Key.L, ctrl = true)
        onNodeWithTag(ShellTags.LIBRARY).assertDoesNotExist()
        workspaceKey(Key.F, ctrl = true)
        onNodeWithTag(ShellTags.LIBRARY).assertExists()
        onNodeWithTag(ShellTags.LIBRARY_SEARCH).performTextInput("claw")
        waitForIdle()
        assertEquals(TemplateId("claw"), state.library.cursor.value, "the cursor goes to the best match")
        onNodeWithTag(ShellTags.libraryGroup("HORNS_CLAWS")).assertExists()
        onNodeWithTag(ShellTags.libraryGroup("BODY")).assertDoesNotExist()
        onNodeWithTag(ShellTags.LIBRARY_SEARCH).performTextReplacement("zzqx")
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_NO_MATCHES).assertExists()
        onNodeWithTag(ShellTags.LIBRARY_SEARCH).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("", state.library.query.value, "Esc clears the search first")
        onNodeWithTag(ShellTags.libraryGroup("BODY")).assertExists()
    }

    @Test
    fun arrowsMoveTheCursorAndEnterAddsFromTheGrid() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = created()
        val session = assertNotNull(state.session.value)
        onNodeWithTag(ShellTags.partRow("tail")).performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_SEARCH).performClick()
        onNodeWithTag(ShellTags.LIBRARY_SEARCH).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        onNodeWithTag(ShellTags.libraryGroup("TAIL")).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(session.document.value.character.parts.any { it.socket?.socket == "tail" })
    }

    @Test
    fun aDraggedTileLiftsAtTheTilesOwnSize() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        created()
        val tile = onNodeWithTag(ShellTags.libraryTile("leg"), useUnmergedTree = true)
        tile.performScrollTo()
        waitForIdle()
        val bounds = tile.getUnclippedBoundsInRoot()
        val start = with(density) { Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx()) }
        onAllNodes(isRoot()).onFirst().performMouseInput {
            moveTo(start)
            press()
            repeat(LIFT_STEPS) { moveBy(Offset(LIFT_STEP, 0f)) }
        }
        waitForIdle()
        val lifted = onNodeWithTag(ShellTags.LIBRARY_LIFTED, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((bounds.right - bounds.left).value, (lifted.right - lifted.left).value, LIFT_SLACK, "the preview is as wide as the tile")
        assertEquals((bounds.bottom - bounds.top).value, (lifted.bottom - lifted.top).value, LIFT_SLACK, "and as tall")
        onAllNodes(isRoot()).onFirst().performMouseInput {
            moveTo(start)
            release()
        }
        waitForIdle()
        onNodeWithTag(ShellTags.LIBRARY_LIFTED, useUnmergedTree = true).assertDoesNotExist()
    }
}
