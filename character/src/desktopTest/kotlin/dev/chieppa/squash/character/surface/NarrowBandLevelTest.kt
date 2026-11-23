package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.shipped
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.document.ClothShape
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.runsOfCells
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val FIT = 0.003f

/** The torso's cloth, as rows of its chart. */
private const val TORSO_FROM = 0.3f
private const val TORSO_TO = 0.7f

/** The chart rows a narrow band paints round a leg, clear of its root. */
private const val BAND_FROM = 20
private const val BAND_UNTIL = 21

/**
 * A narrow band painted round a leg meshes as fine as the garment's narrowest cloth allows: to an eighth of the body's cell where the
 * garment's other cloth is drawn, and to a quarter where all of it is painted cells.
 */
@Tag("integration")
class NarrowBandLevelTest {
    private val plan = shipped.plans.single { it.id.value == "canine-quadruped" }
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val torso = PartId("torso")
    private val legL = PartId("leg-l")
    private val legR = PartId("leg-r")
    private val area = GarmentAreaId("body")

    private val body: AssembledCharacter by lazy {
        val legs = PairId("legs")
        val leg = library.latest(TemplateId("leg"))!!.ref
        val placed = listOf(
            AssemblyPart(torso, library.latest(TemplateId("torso"))!!.ref, null, null, null),
            AssemblyPart(legL, leg, SocketRef(torso, "front-leg"), legs, Side.LEFT),
            AssemblyPart(legR, leg, SocketRef(torso, "front-leg"), legs, Side.RIGHT),
        )
        assembleNow(placed, { PartData() }, plan, library)
    }

    private fun rows(from: Int, until: Int) = BooleanArray(CHART_ROWS * CHART_COLUMNS) { it / CHART_COLUMNS in from until until }

    /** A garment of torso cloth, [drawn] as a shape or painted as cells, and a band round the left leg from row [BAND_FROM] until [BAND_UNTIL]. */
    private fun worn(drawn: Boolean): WornCover {
        val torsoCells = rows((TORSO_FROM * CHART_ROWS).toInt(), (TORSO_TO * CHART_ROWS).toInt())
        val cells = mapOf(torso to runsOfCells(torsoCells), legL to runsOfCells(rows(BAND_FROM, BAND_UNTIL)))
        val shapes = if (drawn) listOf(ClothShape(torso, area, ClothOutline.Box(TORSO_FROM, TORSO_TO))) else emptyList()
        val garment = Garment(
            GarmentId("top"),
            "Top",
            fit = FIT,
            branches = listOf(GarmentBranch(torso, TORSO_FROM, TORSO_TO, FIT), GarmentBranch(legL, BAND_FROM.toFloat() / CHART_ROWS, BAND_UNTIL.toFloat() / CHART_ROWS, FIT)),
            areas = listOf(GarmentArea(area, "", cells)),
            shapes = shapes,
        )
        return WornCover.of(GarmentCover(garment, garment.branches.associateBy { it.part }), body, emptyList(), ShellCache())
    }

    /** The cell the left leg's band meshes at, over [field], the body's, with [worn] grown on it. */
    private fun bandCell(field: CharacterField, worn: WornCover, solids: RunSolids): Float {
        val grown = assertNotNull(garmentField(field, worn, body, solids, RunClips()))
        return grown.cellOf(grown.runs.indexOfFirst { it.partId == legL && it.first })
    }

    @Test
    fun aNarrowPaintedBandBesideDrawnClothMeshesToAnEighthOfTheBodysCell() {
        val solids = RunSolids()
        val field = assertNotNull(fieldOf(body, solids))
        val w = worn(drawn = true)
        assertEquals(field.cell / 8f, bandCell(field, w, solids), field.cell * 1e-4f, "beside drawn cloth")
        assertEquals(field.cell / 4f, bandCell(field, worn(drawn = false), solids), field.cell * 1e-4f, "in a garment of painted cells alone")
    }
}
