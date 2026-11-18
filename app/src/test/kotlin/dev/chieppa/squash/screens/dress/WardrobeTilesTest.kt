package dev.chieppa.squash.screens.dress

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextRange
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddGarment
import dev.chieppa.squash.document.AddGarmentSlot
import dev.chieppa.squash.document.AddOutfit
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.Together
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.library.LibraryTab
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.caninePlan
import dev.chieppa.squash.screens.shell.shipped
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1600
private const val HEIGHT = 900

/** Long enough on the test clock that two presses are two, not a double-click. */
private const val APART_MS = 1000L

private val HEAD = GarmentSlotId("head")
private val BODY = GarmentSlotId("body")
private val HAT = GarmentId("hat")
private val COAT = GarmentId("coat")
private val LOOSE = GarmentId("loose")
private val WINTER = OutfitId("winter")

private object NoFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Renders a pixel for each thumbnail asked for, keeping what it was asked for. */
private class PixelArtist : WardrobeArtist {
    val asked = mutableListOf<Pair<WardrobeScene, WardrobeLook>>()
    val renders get() = asked.size

    override suspend fun render(scene: WardrobeScene, templates: TemplateLibrary, look: WardrobeLook): ImageBitmap {
        asked += scene to look
        return ImageBitmap(1, 1)
    }
}

/** Library's Garments tiles: naming what New makes, renaming, and thumbnails, on a bare new character. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class WardrobeTilesTest {
    private val work = TestWork()

    private fun ComposeUiTest.opened(artist: WardrobeArtist? = null): AppState {
        val state = AppState(shipped, work, wardrobeArtist = artist)
        state.createCharacter(caninePlan, "Fennec")
        setStretchContent { SquashApp(state, NoFiles) }
        settle(work, state)
        state.library.tab.value = LibraryTab.GARMENTS
        waitForIdle()
        return state
    }

    private fun ComposeUiTest.typed(tag: String): String = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun ComposeUiTest.placeholder(tag: String, name: String) =
        onNode(hasText(name) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertExists()

    private fun ComposeUiTest.key(tag: String, key: Key) {
        onNodeWithTag(tag).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private val AppState.wardrobe get() = assertNotNull(session.value).document.value.character.wardrobe

    @Test
    fun newGarmentsSlotsAndOutfitsOpenEmptyOverTheirAutomaticNames() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = opened()
        onNode(hasClickAction() and hasAnyAncestor(hasTestTag(DressTags.NO_GARMENTS)), useUnmergedTree = true).performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.GARMENT_NAME).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", typed(DressTags.GARMENT_NAME))
        placeholder(DressTags.GARMENT_NAME, "Garment 1")
        key(DressTags.GARMENT_NAME, Key.Enter)
        assertEquals("Garment 1", state.wardrobe.garments.single().name, "Enter on an empty field keeps the automatic name")
        assertNull(state.library.renaming.value)
        assertEquals(listOf("Slot 1"), state.wardrobe.slots.map { it.name }, "the first garment makes its slot")
        assertEquals(listOf("Outfit 1"), state.wardrobe.outfits.map { it.name }, "and an outfit wearing it")
        assertEquals(state.wardrobe.garments.single().id, state.wardrobe.worn.single().id)

        onNodeWithTag(DressTags.NEW_SLOT).performScrollTo().performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.SLOT_NAME).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", typed(DressTags.SLOT_NAME))
        placeholder(DressTags.SLOT_NAME, "Slot 2")
        onNodeWithTag(DressTags.SLOT_NAME).performTextInput("Head")
        key(DressTags.SLOT_NAME, Key.Enter)
        assertEquals("Head", state.wardrobe.slots.last().name)

        onNodeWithTag(DressTags.NEW_OUTFIT, useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.OUTFIT_NAME).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("", typed(DressTags.OUTFIT_NAME))
        onNodeWithTag(DressTags.OUTFIT_NAME).performTextInput("Snow")
        key(DressTags.OUTFIT_NAME, Key.Escape)
        assertEquals("Outfit 2", state.wardrobe.outfits.last().name, "Esc on a new outfit keeps its automatic name")
        assertNull(state.library.renaming.value)
    }

    @Test
    fun focusLeavingANewNameKeepsWhatIsTypedOrElseTheAutomaticName() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = opened()
        onNodeWithTag(DressTags.NEW_OUTFIT, useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.OUTFIT_NAME).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(DressTags.OUTFIT_NAME).performTextInput("Snow")
        onNodeWithTag(DressTags.OUTFITS).requestFocus()
        waitForIdle()
        assertEquals("Snow", state.wardrobe.outfits.single().name)
        assertNull(state.library.renaming.value)

        mainClock.advanceTimeBy(APART_MS)
        onNodeWithTag(DressTags.NEW_OUTFIT, useUnmergedTree = true).performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.OUTFIT_NAME).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(DressTags.OUTFITS).requestFocus()
        waitForIdle()
        assertEquals(listOf("Snow", "Outfit 2"), state.wardrobe.outfits.map { it.name })
    }

    @Test
    fun renamingOpensWithTheWholeNameSelectedSoTypingReplacesIt() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = opened()
        onNodeWithTag(DressTags.NEW_OUTFIT, useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state) { onAllNodesWithTag(DressTags.OUTFIT_NAME).fetchSemanticsNodes().isNotEmpty() }
        key(DressTags.OUTFIT_NAME, Key.Enter)
        onNodeWithTag(DressTags.OUTFITS).requestFocus()
        key(DressTags.OUTFITS, Key.F2)
        assertEquals("Outfit 1", typed(DressTags.OUTFIT_NAME))
        assertEquals(TextRange(0, "Outfit 1".length), onNodeWithTag(DressTags.OUTFIT_NAME).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        onNodeWithTag(DressTags.OUTFIT_NAME).performTextInput("Snow")
        key(DressTags.OUTFIT_NAME, Key.Enter)
        assertEquals("Snow", state.wardrobe.outfits.single().name)
    }

    @Test
    fun tilesShowTheirIconsUntilTheirThumbnailsAreRenderedAndALooseGarmentKeepsItsIcon() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val artist = PixelArtist()
        val state = opened(artist)
        val session: CharacterSession = assertNotNull(state.session.value)
        session.send(
            Together(
                listOf(
                    AddGarmentSlot(GarmentSlot(HEAD, "Head")),
                    AddGarmentSlot(GarmentSlot(BODY, "Body")),
                    AddGarment(Garment(HAT, "Hat", HEAD)),
                    AddGarment(Garment(COAT, "Coat", BODY)),
                    AddGarment(Garment(LOOSE, "Loose")),
                    AddOutfit(Outfit(WINTER, "Winter", mapOf(HEAD to HAT, BODY to COAT))),
                    ShowOutfit(WINTER),
                ),
            ),
        )
        waitForIdle()
        onAllNodesWithTag(DressTags.THUMBNAIL, useUnmergedTree = true).assertCountEquals(0)
        settle(work, state) { artist.renders == 3 }
        onAllNodesWithTag(DressTags.THUMBNAIL, useUnmergedTree = true).assertCountEquals(3)
        onNode(hasTestTag(DressTags.THUMBNAIL) and hasAnyAncestor(hasTestTag(DressTags.outfit(WINTER))), useUnmergedTree = true).assertExists()
        onNode(hasTestTag(DressTags.THUMBNAIL) and hasAnyAncestor(hasTestTag(DressTags.garmentTile(HAT))), useUnmergedTree = true).assertExists()
        onNode(hasTestTag(DressTags.THUMBNAIL) and hasAnyAncestor(hasTestTag(DressTags.garmentTile(LOOSE))), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun eachGarmentTileDrawsThatGarmentAloneOnTheBareBodyAtItsTilesSize() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val artist = PixelArtist()
        val state = opened(artist)
        assertNotNull(state.session.value).send(
            Together(
                listOf(
                    AddGarmentSlot(GarmentSlot(HEAD, "Head")),
                    AddGarmentSlot(GarmentSlot(BODY, "Body")),
                    AddGarment(Garment(HAT, "Hat", HEAD)),
                    AddGarment(Garment(COAT, "Coat", BODY)),
                    AddOutfit(Outfit(WINTER, "Winter", mapOf(HEAD to HAT, BODY to COAT))),
                    ShowOutfit(WINTER),
                ),
            ),
        )
        settle(work, state) { artist.renders == 3 }
        val (outfit, outfitLook) = artist.asked.single { it.first.around == null }
        assertEquals(listOf(HAT, COAT), outfit.document.character.wardrobe.worn.map { it.id }, "an outfit tile draws the whole outfit")
        val garments = artist.asked.filter { it.first.around != null }
        assertEquals(
            mapOf<GarmentId?, List<GarmentId>>(HAT to listOf(HAT), COAT to listOf(COAT)),
            garments.associate { (scene, _) -> scene.around to scene.document.character.wardrobe.worn.map { it.id } },
            "a garment tile draws its garment and nothing else of the outfit shown",
        )
        val garmentLook = garments.map { it.second }.distinct().single()
        val art = onNode(hasTestTag(DressTags.THUMBNAIL) and hasAnyAncestor(hasTestTag(DressTags.garmentTile(HAT))), useUnmergedTree = true).fetchSemanticsNode().size
        assertEquals(art.width to art.height, garmentLook.width to garmentLook.height, "rendered at the size its art shows at")
        assertTrue(outfitLook.height > garmentLook.height, "an outfit tile stands taller, for a whole character")
    }
}
