package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.document.ClothPoint
import dev.chieppa.squash.document.ClothShape
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.runsOfCells
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A part's length and girth in body units. */
private const val ALONG = 1.5f
private const val AROUND = 1.2f

private val TORSO = PartId("torso")
private val BODY = GarmentAreaId("body")
private val TRIM = GarmentAreaId("trim")

/** Cloth drawn as shapes: its edge is the outline itself, however narrow, its cells hold it all, and it reads only the cells it lays. */
class DrawnClothTest {
    private fun garment(shapes: List<ClothShape>, cells: List<BooleanArray>? = null): Garment {
        val ids = listOf(BODY, TRIM)
        val laid = cells ?: drawnCells(shapes.map { ClothOutlineDistance(it.outline) }, shapes.map { it.unpaint }, shapes.map { ids.indexOf(it.area) }, ids.size)
        val areas = ids.mapIndexed { k, id -> GarmentArea(id, "", if (laid[k].any { it }) mapOf(TORSO to runsOfCells(laid[k])) else emptyMap()) }
        return Garment(GarmentId("top"), "Top", areas = areas, shapes = shapes)
    }

    private fun clothOf(garment: Garment): ClothField {
        val cells = DrawnCloth.cellsOf(garment, TORSO)
        return assertNotNull(ClothField.of(cells, DrawnCloth.of(garment, TORSO)))
    }

    @Test
    fun aStrapAThirdOfAColumnWideStaysClothWithItsEdgesOnTheOutline() {
        val width = 0.33f / CHART_COLUMNS
        val strap = ClothShape(TORSO, BODY, ClothOutline.Box(0.6f, 0.9f, 0.2f, 0.2f + width))
        val cloth = clothOf(garment(listOf(strap)))
        assertTrue(cloth.at(0.75f, 0.2f + width / 2f) > CLOTH_HEM_LEVEL)
        assertEquals(0f, cloth.inside(0.75f, 0.2f, ALONG, AROUND), 1e-4f)
        assertEquals(width / 2f * AROUND, cloth.inside(0.75f, 0.2f + width / 2f, ALONG, AROUND), 1e-4f)
        assertTrue(cloth.at(0.75f, 0.2f - 2f / CHART_COLUMNS) < 0.05f)
    }

    @Test
    fun aStrokeLaysEveryCellWithinHalfACellOfItAndAnUnpaintingOneOnlyThoseWellInside() {
        val narrow = drawnStrokeCells(ClothOutline.Box(0.5f, 0.6f, 0.2f, 0.2f + 0.2f / CHART_COLUMNS), unpaint = false)
        assertTrue((0 until CHART_ROWS).filter { (it + 0.5f) / CHART_ROWS in 0.5f..0.6f }.all { row -> (0 until CHART_COLUMNS).any { narrow[row * CHART_COLUMNS + it] } })
        val cut = drawnStrokeCells(ClothOutline.Box(0.5f, 0.6f, 0.2f, 0.2f + 0.9f / CHART_COLUMNS), unpaint = true)
        assertTrue(cut.none { it })
    }

    @Test
    fun aPathsPointIsKeptSharpWhereCellsWouldRoundIt() {
        val tip = ClothShape(TORSO, BODY, ClothOutline.Path(listOf(ClothPoint(0.9f, -0.03f), ClothPoint(0.9f, 0.03f), ClothPoint(0.6f, 0f))))
        val cloth = clothOf(garment(listOf(tip)))
        assertTrue(cloth.inside(0.605f, 0f, ALONG, AROUND) > 0f)
        assertTrue(cloth.inside(0.595f, 0f, ALONG, AROUND) < 0f)
    }

    @Test
    fun aLaterAreaTakesWhatItCoversAndTheirSeamIsWhereTheirFieldsMeet() {
        val body = ClothShape(TORSO, BODY, ClothOutline.Box(0.4f, 1f))
        val trim = ClothShape(TORSO, TRIM, ClothOutline.Box(0.9f, 1f))
        val cloth = clothOf(garment(listOf(body, trim)))
        assertTrue(cloth.areaAt(0, 0.95f, 0.3f) < 0.05f)
        assertEquals(cloth.areaAt(0, 0.9f, 0.3f), cloth.areaAt(1, 0.9f, 0.3f), 1e-3f)
        assertTrue(cloth.at(0.9f, 0.3f) > 0.99f)
        assertTrue(cloth.at(1f, 0.3f) > 0.99f, "a band to the part's end runs on over it")
    }

    @Test
    fun anUnpaintingShapeCutsEveryArea() {
        val body = ClothShape(TORSO, BODY, ClothOutline.Box(0.4f, 1f))
        val neck = ClothShape(TORSO, BODY, ClothOutline.Ellipse(0.9f, 1.1f, -0.1f, 0.1f), unpaint = true)
        val cloth = clothOf(garment(listOf(body, neck)))
        assertTrue(cloth.inside(1f, 0f, ALONG, AROUND) < 0f)
        assertTrue(cloth.inside(1f, 0.5f, ALONG, AROUND) > 0f)
    }

    @Test
    fun cellsPaintedOtherwiseAreReadAsCellsThereAndTheShapesKeepTheirEdgeElsewhere() {
        val band = ClothShape(TORSO, BODY, ClothOutline.Box(0.4f, 1f))
        val laid = garment(listOf(band))
        val cells = DrawnCloth.cellsOf(laid, TORSO).map { it.copyOf() }
        for (row in 56 until CHART_ROWS) for (column in 0 until 4) cells[0][row * CHART_COLUMNS + column] = false
        val cut = clothOf(garment(listOf(band), cells))
        assertTrue(cut.at(0.95f, 2f / CHART_COLUMNS) < CLOTH_HEM_LEVEL, "the cells cut out stay cut out")
        assertTrue(cut.at(0.95f, 0.5f) > 0.99f)
        assertEquals(0f, cut.inside(0.4f, 0.5f, ALONG, AROUND), 1e-4f, "the band's own hem, far from the cut, is the shape's")
        assertNull(DrawnCloth.of(laid.copy(shapes = listOf(band.copy(area = GarmentAreaId("gone")))), TORSO))
    }

    @Test
    fun aFieldDrawnAndOneOfCellsAreUnequal() {
        val strap = ClothShape(TORSO, BODY, ClothOutline.Box(0.6f, 0.9f, 0.2f, 0.3f))
        val cells = DrawnCloth.cellsOf(garment(listOf(strap)), TORSO)
        assertTrue(clothOf(garment(listOf(strap))) != ClothField.of(cells))
    }
}
