package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.LEG_L
import dev.chieppa.squash.document.WinterFennec.SWEATER
import dev.chieppa.squash.document.WinterFennec.TORSO
import dev.chieppa.stretch.test.scenario
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A garment's shapes stay through repainting while their area does, round-trip through the file, and undo with the paint. */
class ClothShapeCommandTest {
    private val area = GarmentAreaId("area-1")
    private val trim = GarmentAreaId("area-2")
    private val torsoShape = ClothShape(TORSO, area, ClothOutline.Box(0.4f, 0.6f, 0.1f, 0.12f))
    private val legShape = ClothShape(LEG_L, trim, ClothOutline.Path(listOf(ClothPoint(0.2f, 0f), ClothPoint(0.3f, 0.1f), ClothPoint(0.3f, -0.1f)), curved = true, corners = setOf(0)))

    @Test
    fun repaintingKeepsTheShapesOfTheAreasKeptAndUndoGivesTheRestBack() {
        val start = WinterFennec.dressed()
        val sweater = checkNotNull(start.character.wardrobe.garment(SWEATER))
        val painted = GarmentArea(area, "", mapOf(TORSO to listOf(0, 40)))
        val trimmed = GarmentArea(trim, "", mapOf(LEG_L to listOf(0, 20)))
        val shaped = sweater.copy(areas = listOf(painted, trimmed), shapes = listOf(torsoShape, legShape))
        val repainted = painted.copy(cells = painted.cells + (LEG_L to listOf(0, 24)))
        scenario(start) {
            run(SetGarment(shaped))
            run(SetGarmentPaint(SWEATER, listOf(repainted), emptyList()))
            expect { assertEquals(listOf(torsoShape), it.character.wardrobe.garment(SWEATER)?.shapes) }
            undo()
            expect { assertEquals(listOf(torsoShape, legShape), it.character.wardrobe.garment(SWEATER)?.shapes) }
        }
    }

    @Test
    fun shapesRoundTripThroughJson() {
        val garment = Garment(SWEATER, "Sweater", areas = listOf(GarmentArea(area, "")), shapes = listOf(torsoShape, legShape.copy(unpaint = true)))
        val json = Json { encodeDefaults = false }
        assertEquals(garment, json.decodeFromString(Garment.serializer(), json.encodeToString(Garment.serializer(), garment)))
    }

    @Test
    fun aMirroredOutlineLiesAtOneLessItsV() {
        assertEquals(ClothOutline.Ellipse(0.1f, 0.2f, 0.75f, 0.875f), ClothOutline.Ellipse(0.1f, 0.2f, 0.125f, 0.25f).mirrored())
        assertEquals(listOf(ClothPoint(0.2f, 0.75f)), ClothOutline.Path(listOf(ClothPoint(0.2f, 0.25f), ClothPoint(0.3f, 0.5f), ClothPoint(0.1f, 0.5f))).mirrored().let { (it as ClothOutline.Path).points.take(1) })
    }
}
