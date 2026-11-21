package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.cellsOfRuns
import dev.chieppa.squash.document.mirrored
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import dev.chieppa.squash.recipe.model.RecipeArea
import dev.chieppa.squash.recipe.model.RecipeGarment
import dev.chieppa.squash.recipe.model.RecipeStroke
import dev.chieppa.squash.recipe.model.RecipeStrokeShape
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exact strokes keep their shapes on the garment, lay a cell under a strap narrower than one, and export as they were written. */
@Tag("integration")
class RecipeExactStrokeTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())
    private val base = RecipeJson.read(File(System.getProperty("squash.recipes"), "painted-tee.recipe.json").readText())
    private val straps = RecipeGarment(
        id = "straps",
        name = "Straps",
        slot = "top",
        areas = listOf(
            RecipeArea(
                "body",
                paint = listOf(
                    RecipeStroke("torso", RecipeStrokeShape.BAND, u = listOf(0.5f, 0.8f), exact = true),
                    RecipeStroke("torso", RecipeStrokeShape.BOX, u = listOf(0.78f, 0.99f), v = listOf(0.1f, 0.11f), mirrored = true, exact = true),
                    RecipeStroke("torso", RecipeStrokeShape.LASSO, points = listOf(listOf(0.8f, -0.05f), listOf(0.8f, 0.05f), listOf(0.7f, 0f)), unpaint = true, exact = true),
                ),
            ),
        ),
    )
    private val built = builder.build(base.copy(wardrobe = base.wardrobe!!.copy(garments = base.wardrobe.garments + straps)))

    @Test
    fun exactStrokesAreKeptAsShapesAndAStrapUnderACellWideHoldsCells() {
        assertEquals(emptyList(), built.warnings)
        val garment = checkNotNull(built.document.character.wardrobe.garment(GarmentId("straps")))
        assertEquals(4, garment.shapes.size, "the mirrored strap is two shapes")
        assertTrue(garment.shapes.last().unpaint)
        assertEquals(ClothOutline.Box(0.78f, 0.99f, 0.1f, 0.11f).mirrored(), garment.shapes[2].outline)
        val cells = cellsOfRuns(garment.areas.single().cells.getValue(PartId("torso")))
        val strapRow = (0.9f * 64).toInt()
        assertTrue((0 until 32).count { cells[strapRow * 32 + it] } >= 2, "each strap lays a column")
    }

    @Test
    fun exactStrokesExportAsExactStrokesAndBuildIntoTheSameGarment() {
        val exported = exportRecipe(built.document)
        val strokes = exported.recipe.wardrobe!!.garments.single { it.id == "straps" }.areas.single().paint
        assertTrue(strokes.all { it.exact })
        val again = builder.build(RecipeJson.read(RecipeJson.write(exported.recipe)))
        assertEquals(built.document.character.wardrobe.garment(GarmentId("straps")), again.document.character.wardrobe.garment(GarmentId("straps")))
    }

    @Test
    fun aStrokeThatIsNotExactBesideExactOnesIsWarnedOf() {
        val mixed = straps.copy(areas = listOf(straps.areas.single().copy(paint = straps.areas.single().paint + RecipeStroke("torso", RecipeStrokeShape.BAND, u = listOf(0.4f, 0.5f)))))
        val result = builder.build(base.copy(wardrobe = base.wardrobe!!.copy(garments = base.wardrobe.garments + mixed)))
        assertTrue(result.warnings.any { "not exact" in it }, result.warnings.toString())
    }
}
