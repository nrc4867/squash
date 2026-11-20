package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.cellsOfRuns
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.recipe.export.exportRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Openings cut a neckline round where a part comes out, a band follows them, and a folded area is written through and exported. */
@Tag("integration")
class RecipeOpeningTest {
    private val builder = RecipeBuilder(TemplateFolder.shipped())
    private val source = Json.parseToJsonElement(File(System.getProperty("squash.recipes"), "painted-tee.recipe.json").readText()).jsonObject
    private val torso = PartId("torso")

    /** The painted tee with its tee's areas replaced by [areas], JSON. */
    private fun tee(areas: String): String {
        val wardrobe = source.getValue("wardrobe").jsonObject
        val garments = wardrobe.getValue("garments").jsonArray.map { garment ->
            val fields = garment.jsonObject
            if (fields["id"].toString() != "\"tee\"") garment
            else JsonObject(fields + ("areas" to Json.parseToJsonElement(areas)))
        }
        return JsonObject(source + ("wardrobe" to JsonObject(wardrobe + ("garments" to JsonArray(garments))))).toString()
    }

    private fun built(areas: String): Garment = builder.build(RecipeJson.read(tee(areas))).document.character.wardrobe.garment(GarmentId("tee"))!!

    private fun body(neckline: String) = """[{"id": "body", "paint": [{"on": "torso", "shape": "band", "u": [0.45, 1]}, {"on": "arm-l", "shape": "band", "u": [0, 0.3]}$neckline]}]"""

    /** The torso cells [garment]'s area [area] paints, by index on the chart. */
    private fun torsoCells(garment: Garment, area: Int = 0): Set<Int> {
        val held = garment.areas[area].cells[torso]?.let(::cellsOfRuns) ?: return emptySet()
        return held.indices.filterTo(HashSet()) { held[it] }
    }

    @Test
    fun aRoundOpeningCutsRoundTheNeckAndAVReachesFurtherDownTheFront() {
        val whole = torsoCells(built(body("")))
        val round = torsoCells(built(body(""", {"on": "neck", "shape": "opening", "unpaint": true, "reach": 0.01, "depth": 0.02}""")))
        val vee = torsoCells(built(body(""", {"on": "neck", "shape": "opening", "unpaint": true, "cut": "v", "reach": 0.01, "depth": 0.12}""")))
        val roundCut = whole - round
        val veeCut = whole - vee
        assertTrue(roundCut.isNotEmpty(), "a crew neckline takes the torso's cells round the neck")
        assertTrue(veeCut.size > roundCut.size, "a deep V takes more")
        val lowestRound = roundCut.minOf { it / CHART_COLUMNS }
        val lowestVee = veeCut.minOf { it / CHART_COLUMNS }
        assertTrue(lowestVee < lowestRound, "the V's point lies further down the torso: row $lowestVee against $lowestRound")
    }

    @Test
    fun aBandRoundAnOpeningLiesOutsideItAndTheOpeningStaysBare() {
        val band = """, {"id": "rib", "gather": 0.004, "paint": [{"on": "neck", "shape": "opening", "reach": 0.03, "depth": 0.04}, {"on": "neck", "shape": "opening", "unpaint": true, "reach": 0.01, "depth": 0.02}]}"""
        val garment = built(body("").dropLast(1) + band + "]")
        val rib = torsoCells(garment, 1)
        val bare = torsoCells(built(body("")), 0) - torsoCells(garment, 0) - rib
        assertTrue(rib.isNotEmpty(), "the band paints the torso round the opening")
        assertTrue(bare.isNotEmpty(), "the opening inside the band is bare")
        assertTrue(rib.none { it in bare })
    }

    @Test
    fun aFoldIsWrittenThroughAndExported() {
        val folded = """[{"id": "body", "paint": [{"on": "torso", "shape": "band", "u": [0.45, 1]}]}, {"id": "cuff", "fold": 0.007, "paint": [{"on": "torso", "shape": "band", "u": [0.45, 0.52]}]}]"""
        val built = builder.build(RecipeJson.read(tee(folded)))
        assertEquals(0.007f, built.document.character.wardrobe.garment(GarmentId("tee"))!!.areas[1].fold)
        val exported = exportRecipe(built.document).recipe.wardrobe!!.garments.first { it.id == "tee" }
        assertEquals(listOf(null, 0.007f), exported.areas.map { it.fold })
    }

    @Test
    fun aWrongOpeningOrFoldStopsAtItsPath() {
        val deep = body(""", {"on": "neck", "shape": "opening", "unpaint": true, "depth": -0.1}""")
        assertEquals("$.wardrobe.garments[0].areas[0].paint[2].depth", assertFailsWith<RecipeException> { built(deep) }.path)
        val thick = """[{"id": "body", "fold": 0.5, "paint": [{"on": "torso", "shape": "band", "u": [0.45, 1]}]}]"""
        assertEquals("$.wardrobe.garments[0].areas[0].fold", assertFailsWith<RecipeException> { built(thick) }.path)
    }
}
