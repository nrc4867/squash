package dev.chieppa.squash.screens.dress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentHole
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.Screen
import dev.chieppa.squash.screens.SquashApp
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.BACK_LEG_L
import dev.chieppa.squash.screens.fennec.BACK_PAW_L
import dev.chieppa.squash.screens.fennec.EAR_L
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.FRONT_PAW_L
import dev.chieppa.squash.screens.fennec.HEAD
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.settle
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.squash.screens.surface.PaintShape
import dev.chieppa.squash.screens.surface.SurfaceHit
import dev.chieppa.squash.screens.surface.strokeOf
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.LEG_L
import dev.chieppa.squash.screens.woman.NECK
import dev.chieppa.squash.screens.woman.savedWoman
import dev.chieppa.squash.screens.woman.TORSO as WOMAN_TORSO
import dev.chieppa.squash.screens.fennec.TORSO as FENNEC_TORSO
import dev.chieppa.stretch.document.DocumentFormat
import dev.chieppa.stretch.document.FileChooser
import dev.chieppa.stretch.test.setStretchContent
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val WIDTH = 1440
private const val HEIGHT = 900

/** Where the tee's hem lies on the torso, how far its sleeves run down the arms, and where the torso's paint stops below the neck, in u. */
private const val TEE_HEM_U = 0.2f
private const val TEE_TOP_U = 0.97f
private const val TEE_SLEEVE_U = 0.3f

/** How big a brush unpaints the neck with, in body units: small enough to leave the shoulders. */
private const val NECK_BRUSH = 0.03f

/** Where the boots' shafts start on the fennec's back legs. */
private const val SHAFT_U = 0.6f

/** How far a box of rows may sit from where it was asked for, in u: a cell. */
private const val CELL_U = 1f / CHART_ROWS + 0.001f

private object NoPaintFiles : FileChooser {
    override suspend fun chooseOpen(format: DocumentFormat<*>): String? = null
    override suspend fun chooseSave(format: DocumentFormat<*>, suggestedName: String): String? = null
}

/** Dress's Paint, areas, Hole, Straighten and Tuck on the bare woman and fennec, through the app's state and its tools. */
@Tag("integration")
@OptIn(ExperimentalTestApi::class)
class GarmentPaintFlowTest {
    private val work = TestWork()

    @AfterTest
    fun close() = work.close()

    /** The app over [document] with a new garment named [name] worn and picked, its tools working on it. */
    private fun opened(document: SquashDocument, name: String): AppState {
        val state = AppState(TemplateFolder.shipped(), work)
        val session = state.open(document)
        work.settle(session)
        val character = session.document.value.character
        val garment = newGarmentIn(character.wardrobe) { name }
        session.send(character.newGarmentCommand(garment, null, NewGarmentNames(name, name)))
        work.settle(session)
        state.dress.garment.value = garment.id
        return state
    }

    private val AppState.open: CharacterSession get() = assertNotNull(session.value)
    private val AppState.character: Character get() = open.document.value.character
    private val AppState.body: AssembledCharacter get() = assertNotNull(open.assembly.output.value)
    private val AppState.garment: Garment get() = assertNotNull(dress.garment.value?.let(character.wardrobe::garment))
    private val AppState.painted: PaintedGarment get() = paintedOf(character, body, garment)

    private fun AppState.lay(stroke: SurfaceMask, held: Boolean = false) {
        GarmentPaintTool(this, work.scope, SMOKE_LOOK).lay(stroke, held)
        work.settle(open)
    }

    private fun AppState.send(command: dev.chieppa.squash.document.DocumentCommand?) {
        open.send(assertNotNull(command))
        work.settle(open)
    }

    private fun AppState.brush(part: PartId, fromU: Float, toU: Float): SurfaceMask = roundStroke(character, body, part, fromU, toU)

    private fun AppState.band(part: PartId, fromU: Float, toU: Float) = bandStroke(character, body, part, fromU, toU)

    private fun AppState.patch(part: PartId): String = assertNotNull(bodyChart(character, body).part(part)).patch

    @Test
    fun aTeeIsTheTorsoBrushedInOnePassAndTheUpperArmsInASecondTheNeckLeftOpen() {
        val state = opened(savedWoman(), "Tee")
        state.lay(state.brush(WOMAN_TORSO, TEE_HEM_U, TEE_TOP_U))
        state.lay(brushStroke(state.character, state.body, brushPlaces(state.character, state.body, NECK, 0f, 1f), NECK_BRUSH), held = true)
        assertEquals(0, state.painted.cloth.count(NECK), "what the torso's brush caught of the neck is unpainted with Alt")
        val torsoOnly = state.garment
        val torso = assertNotNull(torsoOnly.branch(WOMAN_TORSO), "the torso brushed is cloth")
        assertTrue(torso.allRound, "front, sides and back")
        assertEquals(1, torsoOnly.areas.size, "the first stroke makes the garment's first area")
        state.lay(state.brush(ARM_L, 0.02f, TEE_SLEEVE_U))
        val tee = state.garment
        val sleeve = assertNotNull(tee.branch(ARM_L), "the upper arms are cloth, the pair from one side")
        assertTrue(sleeve.from <= 0f && sleeve.to in TEE_SLEEVE_U - 0.1f..TEE_SLEEVE_U + 0.15f, "the sleeve runs from the shoulder to its hem, not $sleeve")
        assertNull(tee.branch(NECK), "the neck is never painted")
        assertEquals(BodyPartChange.Through(cuff = false), tee.changes[NECK], "and comes out of the cloth bare")
        assertNull(tee.branch(LEG_L), "the legs stay bare")
        val edges = state.painted.edges.map { it.edge.part }.toSet()
        assertTrue(ARM_L in edges && WOMAN_TORSO in edges, "edges at the sleeves and round the torso: $edges")
    }

    @Test
    fun holdingTheModifierUnpaintsAndUnderUnpaintItPaints() {
        val state = opened(savedFennec(), "Boots")
        val tool = GarmentPaintTool(state, work.scope, SMOKE_LOOK)
        assertFalse(tool.styleFor(held = false).erase)
        assertTrue(tool.styleFor(held = true).erase, "Alt or the secondary button unpaints")
        state.lay(state.band(BACK_LEG_L, SHAFT_U, 1f) + state.band(BACK_PAW_L, 0f, 1f))
        assertNotNull(state.garment.branch(BACK_PAW_L))
        state.lay(state.band(BACK_PAW_L, 0f, 1f), held = true)
        assertNull(state.garment.branch(BACK_PAW_L), "the stroke with the modifier took the paw's cloth away")
        assertNotNull(state.garment.branch(BACK_LEG_L), "and left the shaft")
        state.dress.unpaint.value = true
        assertFalse(tool.styleFor(held = true).erase, "under Unpaint the modifier paints")
        state.lay(state.band(BACK_PAW_L, 0f, 1f), held = true)
        assertNotNull(state.garment.branch(BACK_PAW_L))
        state.lay(state.band(BACK_PAW_L, 0f, 1f))
        assertNull(state.garment.branch(BACK_PAW_L), "Unpaint alone takes it away")
    }

    @Test
    fun eachShapeModeLaysCellsWhereItIsDrawn() {
        val state = opened(savedFennec(), "Cap")
        val character = state.character
        val body = state.body
        val places = chartPlaces(character, body)
        val sight = SideSight()
        val headPatch = state.patch(HEAD)
        val headTop = assertNotNull(placeOnBody(body, Anchor(HEAD, headPatch, 0.5f, 0f)))
        val headUnder = assertNotNull(placeOnBody(body, Anchor(HEAD, headPatch, 0.5f, 0.5f)))
        val a = sight.toPixel(headTop)
        val b = sight.toPixel(headUnder)
        val middle = (a + b) / 2f
        val reach = (a - b).getDistance() / 2f
        val from = middle - Offset(reach, reach)
        val to = middle + Offset(reach, reach)
        val hitAt = { pixel: Offset -> nearestShown(character, body, sight, pixel) }
        val across = Offset(reach / 2f, 0f)
        for (shape in PaintShape.entries) {
            val start = if (shape.outlined) from else middle - across
            val path = when (shape) {
                PaintShape.LASSO -> listOf(Offset(to.x, from.y), to, Offset(from.x, to.y))
                PaintShape.BRUSH, PaintShape.LINE -> listOf(middle + across)
                else -> listOf(to)
            }
            val stroke = strokeOf(shape, places, sight, start, path, reach / 4f / sight.pixelsPerUnitAt(headTop), reach / 4f, hitAt)
            assertTrue(stroke.count(HEAD) > 0, "$shape lays cells on the head")
            assertEquals(0, stroke.count(BACK_PAW_L), "$shape lays none on the back paws, far from it")
        }
        val box = strokeOf(PaintShape.BOX, places, sight, from, listOf(to), 0f, 0f, hitAt)
        val ellipse = strokeOf(PaintShape.ELLIPSE, places, sight, from, listOf(to), 0f, 0f, hitAt)
        assertTrue(ellipse.count(HEAD) < box.count(HEAD), "an ellipse inside a box holds less of the head than the box")
        state.lay(box)
        assertNotNull(state.garment.branch(HEAD), "a box over the head in Side paints through it, both sides")
        assertTrue(assertNotNull(state.garment.branch(HEAD)).allRound)
    }

    @Test
    fun growAndShrinkMoveTheWholeEdgeAStep() {
        val state = opened(savedFennec(), "Socks")
        state.lay(state.band(BACK_LEG_L, 0.4f, 0.7f))
        val cells = state.painted.cloth.count(BACK_LEG_L)
        state.send(state.character.growCommand(state.body, state.garment, null, shrink = false))
        val grown = state.painted.cloth.count(BACK_LEG_L)
        assertEquals(cells + 2 * CHART_COLUMNS, grown, "a row more at each end")
        state.send(state.character.growCommand(state.body, state.garment, null, shrink = true))
        assertEquals(cells, state.painted.cloth.count(BACK_LEG_L))
        state.send(state.character.growCommand(state.body, state.garment, null, shrink = true))
        assertEquals(cells - 2 * CHART_COLUMNS, state.painted.cloth.count(BACK_LEG_L))
    }

    @Test
    fun twoAreasTouchAlongASeamWhichTheHemInkInks() = runDesktopComposeUiTest(WIDTH, HEIGHT) {
        val state = opened(savedFennec(), "Sandals")
        state.screen.value = Screen.WORKSPACE
        setStretchContent { SquashApp(state, NoPaintFiles) }
        settle(work, state)
        state.showStage(dev.chieppa.squash.screens.shell.Stage.DRESS)
        settle(work, state)
        state.lay(state.band(FRONT_LEG_L, 0.75f, 1f))
        settle(work, state)
        onNodeWithTag(DressTags.NEW_AREA).performScrollTo().performClick()
        settle(work, state)
        val second = assertNotNull(state.dress.area.value)
        assertEquals(2, state.garment.areas.size)
        onNodeWithTag(DressTags.area(second)).assertExists()
        state.lay(state.band(FRONT_PAW_L, 0f, 0.4f))
        settle(work, state)
        val sandals = state.garment
        assertEquals(listOf(true, true), sandals.areas.map { it.cells.isNotEmpty() }, "each area holds its own strap")
        val seams = state.painted.edges.filter { it.edge.seam }
        assertTrue(seams.any { it.edge.part == FRONT_PAW_L }, "where the straps meet, at the paw's root, is a seam")
        assertTrue(FRONT_PAW_L in sandals.joins, "inked with the hems")
        assertNotNull(sandals.branch(FRONT_LEG_L))
        assertNotNull(sandals.branch(FRONT_PAW_L))
        onNodeWithTag(DressTags.removeArea(second)).performScrollTo().performClick()
        settle(work, state)
        assertNull(state.garment.branch(FRONT_PAW_L), "the toe strap goes with its area")
        assertFalse(FRONT_PAW_L in state.garment.joins)
    }

    @Test
    fun holeLetsAPartOutAndASecondPressCuffsIt() {
        val state = opened(savedWoman(), "Tee")
        state.lay(state.band(WOMAN_TORSO, TEE_HEM_U, 1f) + state.band(NECK, 0f, 1f))
        assertNotNull(state.garment.branch(NECK), "the neck painted over")
        val hole = GarmentHoleTool(state, SMOKE_LOOK)
        val onNeck = SurfaceHit(NECK, state.patch(NECK), 0.5f, 0f, Vec3.ZERO)
        hole.pressedAt(onNeck)
        work.settle(state.open)
        assertEquals(listOf(GarmentHole(NECK)), state.garment.holes)
        assertNull(state.garment.branch(NECK), "the neck comes out")
        assertEquals(BodyPartChange.Through(cuff = false), state.garment.changes[NECK], "with no cuff")
        hole.pressedAt(onNeck)
        work.settle(state.open)
        assertEquals(listOf(GarmentHole(NECK, cuff = true)), state.garment.holes)
        val cuff = assertNotNull(state.garment.branch(NECK), "a second press cuffs it: a band of cloth round the neck's root")
        assertTrue(cuff.from <= 0f && cuff.to <= CUFF_BAND_U + CELL_U * 2, "the band runs from the root, to ${cuff.to}")
        hole.pressedAt(onNeck)
        work.settle(state.open)
        assertEquals(listOf(GarmentHole(NECK, cuff = true)), state.garment.holes, "a third press adds nothing")
        state.send(state.character.holeCuffCommand(state.body, state.garment, NECK, false))
        assertEquals(BodyPartChange.Through(cuff = false), state.garment.changes[NECK], "the cuff's switch turns it off")
        state.send(state.character.closeHoleCommand(state.body, state.garment, NECK))
        assertTrue(state.garment.holes.isEmpty())
        assertNotNull(state.garment.branch(NECK), "closing the hole covers the neck again")
        state.dress.cuffOnSecondPress.value = false
        hole.pressedAt(onNeck)
        work.settle(state.open)
        hole.pressedAt(onNeck)
        work.settle(state.open)
        assertEquals(listOf(GarmentHole(NECK)), state.garment.holes, "with Cuff on second press off, no cuff")
    }

    @Test
    fun straightenLevelsTheEdgeRoundItsPart() {
        val state = opened(savedFennec(), "Boots")
        val chart = bodyChart(state.character, state.body)
        val slanted = SurfaceMask.build(chart) { part, cell -> part == FRONT_LEG_L && cell / CHART_COLUMNS >= 30 + (cell % CHART_COLUMNS) / 4 }
        state.lay(slanted)
        val index = state.painted.edges.indexOfFirst { it.edge.part == FRONT_LEG_L }
        assertTrue(index >= 0)
        state.dress.edge.value = index
        state.send(state.character.straightenCommand(state.body, state.garment, index))
        val mask = state.painted.areas.single().mask
        val tops = (0 until CHART_COLUMNS).map { column -> (0 until CHART_ROWS).first { row -> mask.held(FRONT_LEG_L, row * CHART_COLUMNS + column) } }
        assertEquals(1, tops.toSet().size, "every column starts on one row: $tops")
        assertTrue(tops.first() in 30..38)
        val edge = state.painted.edges.first { it.edge.part == FRONT_LEG_L }.edge
        val us = edge.curve.points.map { it.u }
        assertTrue(us.max() - us.min() < CELL_U, "the edge is a level ring")
        assertNull(state.character.straightenCommand(state.body, state.garment, state.painted.edges.indexOf(state.painted.edges.first { it.edge.part == FRONT_LEG_L })))
    }

    @Test
    fun paintCoversTheEarsAndTuckDecidesWhetherTheClothIsShapedOverThem() {
        val state = opened(savedFennec(), "Hood")
        state.lay(state.band(HEAD, 0f, 1f) + state.band(EAR_L, 0f, 1f))
        val hood = state.garment
        assertNotNull(hood.branch(HEAD))
        val ear = shownTucks(state.character, state.body, hood, null).single { it.part == EAR_L }
        assertEquals(TuckKind.EARS, ear.kind)
        assertFalse(ear.tucked, "ears start Shaped")
        assertNotNull(hood.branch(EAR_L), "the cloth follows each ear")
        state.send(tuckCommand(state.character, state.body, hood, ear, tucked = true))
        val smooth = state.garment
        assertEquals(BodyPartChange.Hide, smooth.changes[EAR_L], "tucked, the ears go under smooth cloth")
        assertNull(smooth.branch(EAR_L))
        state.selectedPart.value = FENNEC_TORSO
        assertTrue(shownTucks(state.character, state.body, smooth, FENNEC_TORSO).any { it.part == EAR_L }, "a row for a part beyond the selection")
        state.selectedPart.value = BACK_LEG_L
        assertTrue(shownTucks(state.character, state.body, smooth, BACK_LEG_L).none { it.part == EAR_L }, "none for a part outside it")
    }

    @Test
    fun aGarmentSavedAsBoxesOpensWithThemPaintedAndIsPaintedFromThem() {
        val bare = opened(savedFennec(), "Probe")
        val legacy = legacyBoots(savedFennec(), bare.patch(FRONT_LEG_L), bare.patch(FRONT_PAW_L), SHAFT_U)
        val state = AppState(TemplateFolder.shipped(), work)
        val session = state.open(legacy)
        work.settle(session)
        val boots = assertNotNull(state.character.wardrobe.garments.singleOrNull())
        state.dress.garment.value = boots.id
        assertEquals(1, boots.areas.size, "its boxes read as one painted area")
        val read = state.painted
        assertTrue(read.cloth.count(FRONT_PAW_L) > 0 && read.cloth.count(FRONT_LEG_L) > 0)
        assertEquals(0, read.cloth.count(FENNEC_TORSO), "a part it has no branch over stays bare")
        state.lay(state.band(BACK_PAW_L, 0f, 1f))
        val painted = state.garment
        assertEquals(1, painted.areas.size)
        val leg = assertNotNull(painted.branch(FRONT_LEG_L))
        val was = assertNotNull(boots.branch(FRONT_LEG_L))
        assertTrue(abs(leg.from - was.from) <= CELL_U * 2 && leg.to == was.to, "the shaft is where it was: $was, now $leg")
        assertEquals(boots.branch(FRONT_PAW_L)?.let { it.from to it.to }, painted.branch(FRONT_PAW_L)?.let { it.from to it.to })
        assertNotNull(painted.branch(BACK_PAW_L))
    }

}

