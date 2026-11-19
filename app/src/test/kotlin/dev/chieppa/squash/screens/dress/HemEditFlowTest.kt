package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.surface.areaLevel
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.fennec.FRONT_LEG_L
import dev.chieppa.squash.screens.fennec.savedFennec
import dev.chieppa.squash.screens.smoke.SMOKE_LOOK
import dev.chieppa.squash.screens.woman.ARM_L
import dev.chieppa.squash.screens.woman.NECK
import dev.chieppa.squash.screens.woman.TORSO
import dev.chieppa.squash.screens.woman.savedWoman
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/** How near the cloth's cut level a hem's point lies, in the field's own units. */
private const val ON_LEVEL = 0.03f

/** The most handles a slanted hem round a leg takes. */
private const val FEW_HANDLES = 8

/** How many rows a handle is dragged up the leg. */
private const val DRAG_ROWS = 6

/** Dress shows a garment's hems as the curves its cloth is cut on, with few handles that repaint cells, and the paint an opening cuts. */
@Tag("integration")
class HemEditFlowTest {
    private val work = TestWork()

    @AfterTest
    fun close() = work.close()

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

    private fun AppState.lay(stroke: SurfaceMask) {
        GarmentPaintTool(this, work.scope, SMOKE_LOOK).lay(stroke)
        work.settle(open)
    }

    private fun AppState.send(command: DocumentCommand) {
        open.send(command)
        work.settle(open)
    }

    /** A sock on the fennec's front leg whose top runs a row lower every four columns round. */
    private fun AppState.slantedSock() {
        val chart = bodyChart(character, body)
        lay(SurfaceMask.build(chart) { part, cell -> part == FRONT_LEG_L && cell / CHART_COLUMNS >= 30 + (cell % CHART_COLUMNS) / 4 })
    }

    private fun AppState.legHem(): Int = painted.edges.indexOfFirst { it.edge.part == FRONT_LEG_L }.also { assertTrue(it >= 0) }

    @Test
    fun aHemShowsAsTheCurveTheClothIsCutOnNotTheCellsSteps() {
        val state = opened(savedFennec(), "Sock")
        state.slantedSock()
        val painted = state.painted
        val hem = painted.edges[state.legHem()]
        val field = assertNotNull(painted.fields[FRONT_LEG_L])
        val off = hem.edge.curve.points.maxOf { abs(field.areaLevel(hem.area, it.u, it.v)) }
        assertTrue(off < ON_LEVEL, "every point of the hem lies on the cloth's cut level, at most $off off it")
        val rows = hem.edge.curve.points.map { it.u * CHART_ROWS }
        assertTrue(rows.count { abs(it - Math.round(it)) > 0.1f } * 4 > rows.size, "where the cells step the hem runs between their sides")
    }

    @Test
    fun aHemTakesFewHandlesAndDraggingOneRepaintsTheCellsBesideItOnly() {
        val state = opened(savedFennec(), "Sock")
        state.slantedSock()
        val index = state.legHem()
        val held = state.painted
        val curve = held.edges[index].edge.curve
        val hem = assertNotNull(held.hems[index])
        assertTrue(hem.handles.size in LEAST_LOOP_HANDLES..FEW_HANDLES, "${hem.handles.size} handles for a hem of ${curve.points.size} points")
        val unmoved = redrawnEdge(held, index, hem.curve(), hem.curve())
        assertEquals(held.areas.single().mask.runs(), unmoved.areas.single().mask.runs(), "a handle let go where it was repaints nothing")
        val handle = hem.handles[1]
        val up = handle.copy(u = handle.u - DRAG_ROWS.toFloat() / CHART_ROWS)
        state.dress.edge.value = index
        state.send(state.character.edgeMovedCommand(state.body, state.garment, held, index, hem.curve(), hem.moved(1, up).curve()))
        val before = held.areas.single().mask
        val after = state.painted.areas.single().mask
        val column = (handle.v * CHART_COLUMNS).toInt()
        val next = hem.handles[2].v * CHART_COLUMNS
        val after2 = hem.handles[3 % hem.handles.size].v * CHART_COLUMNS
        val far = ((next + ((after2 - next + CHART_COLUMNS) % CHART_COLUMNS) / 2f).toInt()) % CHART_COLUMNS
        fun top(mask: SurfaceMask, c: Int) = (0 until CHART_ROWS).first { mask.held(FRONT_LEG_L, it * CHART_COLUMNS + c) }
        assertTrue(top(after, column) <= top(before, column) - DRAG_ROWS / 2, "the cloth runs further up the leg under the handle: ${top(before, column)} to ${top(after, column)}")
        assertEquals(top(before, far), top(after, far), "nor past the next handle, where the hem's next cubic runs as it was")
        assertTrue(assertNotNull(state.painted.hems[state.legHem()]).handles.size <= FEW_HANDLES, "the hem drawn again still takes few handles")
    }

    @Test
    fun paintAnOpeningWillCutIsHighlightedTillThePartThroughItIsCovered() {
        val state = opened(savedWoman(), "Tee")
        state.lay(bandStroke(state.character, state.body, TORSO, 0.2f, 1f))
        val cut = cutCells(state.character, state.body, state.garment, state.painted)
        assertTrue(cut.count(TORSO) > 0, "the torso's cells round the arms' and the neck's roots are cut")
        assertEquals(cut.count, cut.within(state.painted.cloth).count, "only painted cells")
        assertEquals(0, cut.count(ARM_L), "the bare arms hold none")
        state.lay(bandStroke(state.character, state.body, ARM_L, 0f, 0.3f) + bandStroke(state.character, state.body, NECK, 0f, 1f))
        assertEquals(0, cutCells(state.character, state.body, state.garment, state.painted).count, "with sleeves and a collar painted nothing comes out")
    }
}
