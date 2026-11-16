package dev.chieppa.squash.screens.dress

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.catalog.partName
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SNUG_FIT
import dev.chieppa.squash.document.SetPartFit
import dev.chieppa.squash.document.TuckChoice
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.TAIL
import dev.chieppa.squash.screens.fennec.TORSO
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.shell.BuildTool
import dev.chieppa.squash.screens.shell.ShellTags
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIDTH = 1440
private const val HEIGHT = 900

/** Where round the leg the stroke across it runs, as a view sees the front half of it. */
private const val ACROSS_FROM = 0.3f
private const val ACROSS_TO = 0.7f
private const val ACROSS_POINTS = 9

/** Where along the tail its sock's edge is drawn: at its root, so the whole tail lies inside. */
private const val TAIL_ROOT_U = 0.01f

/** A patch of the paw's toes, where a press on a toe lands. */
private const val TOE_PATCH = "toes:0"

/** How far down the front leg the boot's edge is drawn. */
private const val SHAFT_U = 0.7f

private object NoEdgeFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Dress's garment drawing on the bare fennec, through the app whole: New garment, Edge and its fill, Hole, Tuck and Fit. */
@OptIn(ExperimentalTestApi::class)
class GarmentEdgeFlowTest {
    private val work = TestWork()

    private fun ComposeUiTest.bareOpen(): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        state.open(savedFennec())
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoEdgeFiles) }
        settle(work, state)
        key(Key.Six)
        return state
    }

    private fun ComposeUiTest.key(key: Key) {
        onNodeWithTag(ShellTags.WORKSPACE).performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** New garment from its key, its names read before the key so the step it sends waits on nothing. */
    private fun ComposeUiTest.newGarmentByKey(state: AppState): Garment {
        runBlocking { newGarmentNames(assertNotNull(state.session.value).document.value.character.wardrobe) }
        key(Key.N)
        settle(work, state)
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

    private fun patchOf(state: AppState, part: PartId): String {
        val session = assertNotNull(state.session.value)
        val body = assertNotNull(session.assembly.output.value)
        return assertNotNull(bodyChart(session.document.value.character, body).part(part)).patch
    }

    /** A stroke across the front of the front leg, as a drag across it in a view lays one. */
    private fun ComposeUiTest.edgeRoundTheLeg(state: AppState, tool: EdgeTool) {
        val patch = patchOf(state, FRONT_LEG_L)
        val points = List(ACROSS_POINTS) { i -> Anchor(FRONT_LEG_L, patch, SHAFT_U, ACROSS_FROM + (ACROSS_TO - ACROSS_FROM) * i / (ACROSS_POINTS - 1)) }
        val session = assertNotNull(state.session.value)
        tool.stroked(points, meets = false, garment(state), session.document.value.character, assertNotNull(session.assembly.output.value))
        settle(work, state)
    }

    private fun ComposeUiTest.pressOn(state: AppState, tool: GarmentEdgeTool, part: PartId, u: Float, v: Float = 0.5f) {
        val session = assertNotNull(state.session.value)
        val hit = SurfaceHit(part, patchOf(state, part), u, v, Vec3.ZERO)
        tool.pressedAt(hit, garment(state), session.document.value.character, assertNotNull(session.assembly.output.value))
        settle(work, state)
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
        assertEquals(BuildTool.EDGE, state.tool.value, "Edge is armed")
        state.undo()
        assertTrue(session.document.value.character.wardrobe.garments.isEmpty())
        assertTrue(session.document.value.character.wardrobe.slots.isEmpty())
    }

    @Test
    fun anEdgeAcrossALegFillsThePawSideAPressFlipsItAndHoleOpensItsPaw() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = bareOpen()
        newGarment(state)
        val tool = EdgeTool(state, SMOKE_LOOK)
        edgeRoundTheLeg(state, tool)
        val boot = garment(state)
        val edge = boot.edges.single()
        assertTrue(edge.curve.closed, "a stroke across a limb closes round its far side")
        val shaft = assertNotNull(boot.branch(FRONT_LEG_L), "the leg below the edge is covered")
        assertEquals(1f, shaft.to)
        assertTrue(shaft.from in SHAFT_U - 0.05f..SHAFT_U + 0.05f, "the shaft starts at the edge, not ${shaft.from}")
        val paw = assertNotNull(boot.branch(FRONT_PAW_L), "the smaller side, the paw's, fills")
        assertTrue(paw.from <= 0f && paw.to >= 1f && paw.allRound)
        assertNull(boot.branch(TORSO))
        assertEquals(0f, assertIs<BodyPartChange.Redraw>(boot.changes[FRONT_PAW_L]).data.params["toes"], "toes are tucked")

        val session = assertNotNull(state.session.value)
        val document = session.document.value
        val press = Anchor(TORSO, patchOf(state, TORSO), 0.5f, 0.5f)
        val flip = document.character.fillCommand(assertNotNull(session.assembly.output.value), boot, press)
        val flipped = assertNotNull(flip.apply(document).document.character.wardrobe.garment(boot.id))
        assertNotNull(flipped.branch(TORSO), "a press on the other side fills it")
        assertNull(flipped.branch(FRONT_PAW_L), "and empties the paw's side")
        val leg = assertNotNull(flipped.branch(FRONT_LEG_L))
        assertEquals(0f, leg.from)
        assertTrue(leg.to in SHAFT_U - 0.05f..SHAFT_U + 0.05f, "the leg is covered down to the edge, not ${leg.to}")
        assertNull(flipped.changes[FRONT_PAW_L])
        val back = document.character.fillCommand(assertNotNull(session.assembly.output.value), flipped, Anchor(FRONT_PAW_L, patchOf(state, FRONT_PAW_L), 0.5f, 0.5f))
        assertNotNull(back.apply(flip.apply(document).document).document.character.wardrobe.garment(boot.id)?.branch(FRONT_PAW_L), "a press on the paw flips it back")

        val hole = HoleTool(state, SMOKE_LOOK)
        val onAToe = SurfaceHit(FRONT_PAW_L, TOE_PATCH, 0.5f, 0.5f, Vec3.ZERO)
        hole.pressedAt(onAToe, boot, session.document.value.character, assertNotNull(session.assembly.output.value))
        settle(work, state)
        val holed = garment(state)
        assertTrue(holed.edges.last().hole && holed.edges.last().curve.closed)
        assertNull(holed.branch(FRONT_PAW_L), "a press on a toe takes the paw out through a hole round its root")
        assertEquals(BodyPartChange.Through(cuff = false), holed.changes[FRONT_PAW_L], "with no cuff")
        assertEquals(boot.branch(FRONT_LEG_L), holed.branch(FRONT_LEG_L), "the rest of the garment stays as it was")
        assertEquals(setOf(FRONT_LEG_L), holed.branches.map { it.part }.toSet(), "and nothing else is covered")
        pressOn(state, hole, FRONT_PAW_L, 0.5f)
        val banded = garment(state)
        assertEquals(3, banded.edges.size, "a second press adds a band's edge")
        val band = assertNotNull(banded.branch(FRONT_PAW_L), "the band is cloth round the paw's root")
        assertTrue(band.from <= 0f && band.to < 0.25f, "the band runs from the root, to ${band.to}")
        assertNotNull(banded.branch(FRONT_LEG_L))

        onNodeWithTag(DressTags.removeEdge(2)).performScrollTo().performClick()
        settle(work, state)
        assertNull(garment(state).branch(FRONT_PAW_L), "removing the band's edge leaves the hole")
        onNodeWithTag(DressTags.removeEdge(1)).performScrollTo().performClick()
        settle(work, state)
        val rebuilt = garment(state)
        assertNotNull(rebuilt.branch(FRONT_PAW_L), "removing the hole's edge covers the paw again from the edge that remains")
        assertEquals(boot.branch(FRONT_LEG_L), rebuilt.branch(FRONT_LEG_L))
        onNodeWithTag(DressTags.removeEdge(0)).performScrollTo().performClick()
        settle(work, state)
        val bare = garment(state)
        assertTrue(bare.edges.isEmpty())
        assertTrue(bare.branches.isEmpty(), "a garment with no edges covers nothing")
        assertTrue(bare.changes.isEmpty(), "and changes no part")
    }

    @Test
    fun aTailInsideTheClothTucksUntilShapedAndFitAPartLoosensIt() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = bareOpen()
        newGarment(state)
        val session = assertNotNull(state.session.value)
        val patch = patchOf(state, TAIL)
        val across = List(ACROSS_POINTS) { i -> Anchor(TAIL, patch, TAIL_ROOT_U, ACROSS_FROM + (ACROSS_TO - ACROSS_FROM) * i / (ACROSS_POINTS - 1)) }
        EdgeTool(state, SMOKE_LOOK).stroked(across, meets = false, garment(state), session.document.value.character, assertNotNull(session.assembly.output.value))
        settle(work, state)
        val sock = garment(state)
        assertEquals(BodyPartChange.Hide, sock.changes[TAIL], "a tail inside the cloth is tucked under it")
        assertNull(sock.branch(TAIL))
        onNodeWithTag(DressTags.tuck(TAIL, TuckKind.TAIL)).assertExists()
        state.selectedPart.value = HEAD
        waitForIdle()
        onNodeWithTag(DressTags.tuck(TAIL, TuckKind.TAIL)).assertDoesNotExist()
        state.selectedPart.value = TORSO
        waitForIdle()
        onNodeWithTag(DressTags.tuck(TAIL, TuckKind.TAIL)).assertExists()
        state.selectedPart.value = null
        waitForIdle()

        onNode(hasText("Shaped") and hasAnyAncestor(hasTestTag(DressTags.tuckChoice(TAIL, TuckKind.TAIL))), useUnmergedTree = true).performScrollTo().performClick()
        settle(work, state)
        val shaped = garment(state)
        assertEquals(listOf(TuckChoice(TAIL, TuckKind.TAIL, false)), shaped.tucks)
        assertNull(shaped.changes[TAIL], "a shaped tail is built")
        assertNotNull(shaped.branch(TAIL), "and the cloth follows it")

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
