package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.bodyChart
import dev.chieppa.squash.character.dress.placeOnBody
import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.character.shape.cellMiddle
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.geom.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** How far from a pixel, in pixels, a cell's middle shows for [nearestShown] to take it. */
private const val NEAR_PIXELS = 16f

/** How many cells [nearestShown] steps over between those it looks at. */
private const val CELL_STEP = 3

/** How many points the saved boots' edge takes round the leg. */
private const val RING_POINTS = 24

/**
 * [bare], the fennec, wearing boots saved in format 16 as an edge round its front leg at [shaftU], on the leg's first run [legPatch],
 * and a fill on the paw's [pawPatch], with the branches they wrote: the leg from the edge down and the paw whole.
 */
fun legacyBoots(bare: SquashDocument, legPatch: String, pawPatch: String, shaftU: Float): SquashDocument {
    val saved = Json.encodeToJsonElement(SquashDocument.serializer(), bare).jsonObject
    val ring = (0 until RING_POINTS).joinToString(",") { """{"part":"front-leg-l","patch":"$legPatch","u":$shaftU,"v":${it.toFloat() / RING_POINTS}}""" }
    val garment = Json.parseToJsonElement(
        """{"id":"boots","name":"Boots","slot":"feet","fit":0.015,
           "branches":[{"part":"front-leg-l","from":${shaftU - 0.5f / CHART_ROWS},"to":1.0},{"part":"front-paw-l"}],
           "edges":[{"curve":{"points":[$ring],"closed":true}}],
           "fill":[{"part":"front-paw-l","patch":"$pawPatch","u":0.5,"v":0.25}]}""",
    ).jsonObject
    val wardrobe = JsonObject(
        mapOf(
            "garments" to JsonArray(listOf(garment)),
            "slots" to JsonArray(listOf(Json.parseToJsonElement("""{"id":"feet","name":"Feet"}"""))),
            "outfits" to JsonArray(listOf(Json.parseToJsonElement("""{"id":"winter","name":"Winter","picks":{"feet":"boots"}}"""))),
            "shown" to JsonPrimitive("winter"),
        ),
    )
    val character = JsonObject(saved.getValue("character").jsonObject + ("wardrobe" to wardrobe))
    return readDocument(JsonObject(saved + ("character" to character) + ("format" to JsonPrimitive(16))).toString())
}

/** The place on [body] showing nearest [pixel] through [sight], of the cells' middles within a few pixels, the one nearest the viewer. */
fun nearestShown(character: Character, body: AssembledCharacter, sight: SideSight, pixel: Offset): Vec3? {
    val chart = bodyChart(character, body)
    var best: Vec3? = null
    var nearest = Float.POSITIVE_INFINITY
    for (part in chart.parts) for (cell in 0 until CHART_CELLS step CELL_STEP) {
        val at = placeOnBody(body, cellMiddle(part.id, part.patch, cell)) ?: continue
        if ((sight.toPixel(at) - pixel).getDistance() > NEAR_PIXELS) continue
        val depth = sight.depthOf(at)
        if (depth < nearest) {
            nearest = depth
            best = at
        }
    }
    return best
}
