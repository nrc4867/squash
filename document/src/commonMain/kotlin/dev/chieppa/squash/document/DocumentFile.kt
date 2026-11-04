package dev.chieppa.squash.document

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FORMAT_KEY = "format"
private const val CHARACTER_KEY = "character"
private const val PARTS_KEY = "parts"
private const val PAIR_KEY = "pair"
private const val OWN_KEY = "own"
private const val OVERRIDES_KEY = "overrides"
private const val SURFACE_KEY = "surface"
private const val GRAIN_KEY = "grain"
private const val PAIRS_KEY = "pairs"
private const val MARKS_KEY = "marks"
private const val POINTS_KEY = "points"
private const val ID_KEY = "id"
private const val KIND_KEY = "kind"
private const val FUR_KIND = "fur"
private const val FUR_ID_PREFIX = "fur-"

/** Each step takes a saved document in the format it is keyed by to the next. */
private val MIGRATIONS: Map<Int, (JsonObject) -> JsonObject> = mapOf(1 to ::grainIntoSurfaceWork, 2 to ::softnessOnBrushStrokes, 3 to ::shadingOnCharacter, 4 to ::lineWeightsOnSurfaceWork, 5 to ::furMarksGainAKind)

/**
 * The document in saved [text], brought forward through the migration chain from the format it was saved in. A document in a
 * format this app has no way forward from, a newer one above all, is refused.
 */
fun readDocument(text: String): SquashDocument {
    var saved = Json.parseToJsonElement(text).jsonObject
    var format = requireNotNull(saved[FORMAT_KEY]?.jsonPrimitive?.intOrNull) { "This file does not say its document format" }
    require(format <= DOCUMENT_FORMAT) { "This file is in document format $format, from a newer app; this one reads up to $DOCUMENT_FORMAT" }
    while (format < DOCUMENT_FORMAT) {
        val step = requireNotNull(MIGRATIONS[format]) { "This app cannot read document format $format" }
        format += 1
        saved = JsonObject(step(saved) + (FORMAT_KEY to JsonPrimitive(format)))
    }
    return Json.decodeFromJsonElement(SquashDocument.serializer(), saved)
}

/** Format 4 to 5: surface work gains line weights and a character line rules. A save without them has none and the default rules. */
private fun lineWeightsOnSurfaceWork(document: JsonObject): JsonObject = document

/** Format 3 to 4: a character gains a shading choice and a palette slot a shadow tone. Absent, they read as flat and as the warm default. */
private fun shadingOnCharacter(document: JsonObject): JsonObject = document

/** Format 5 to 6: a mark whose id said it was a fur stroke becomes one, a root alone; every other mark is a line, which an absent kind reads as. */
private fun furMarksGainAKind(document: JsonObject): JsonObject {
    val character = document.getValue(CHARACTER_KEY).jsonObject
    val parts = character[PARTS_KEY]?.jsonArray?.let { held ->
        JsonArray(held.map { part -> JsonObject(part.jsonObject.mapValues { (key, value) -> if (key == OWN_KEY || key == OVERRIDES_KEY) dataWithFurKinds(value) else value }) })
    }
    val pairs = character[PAIRS_KEY]?.jsonObject?.let { held -> JsonObject(held.mapValues { (_, data) -> dataWithFurKinds(data) }) }
    val moved = character + listOfNotNull(parts?.let { PARTS_KEY to it }, pairs?.let { PAIRS_KEY to it })
    return JsonObject(document + (CHARACTER_KEY to JsonObject(moved)))
}

private fun dataWithFurKinds(data: JsonElement): JsonElement {
    val held = data as? JsonObject ?: return data
    val surface = held[SURFACE_KEY] as? JsonObject ?: return data
    val marks = surface[MARKS_KEY] as? JsonArray ?: return data
    return JsonObject(held + (SURFACE_KEY to JsonObject(surface + (MARKS_KEY to JsonArray(marks.map(::markWithFurKind))))))
}

private fun markWithFurKind(mark: JsonElement): JsonElement {
    val held = mark.jsonObject
    if (held[ID_KEY]?.jsonPrimitive?.content?.startsWith(FUR_ID_PREFIX) != true) return mark
    val root = held.getValue(POINTS_KEY).jsonArray.take(MIN_FUR_POINTS)
    return JsonObject(held + (POINTS_KEY to JsonArray(root)) + (KIND_KEY to JsonPrimitive(FUR_KIND)))
}

/** Format 2 to 3: a brush stroke gains a softness. A stroke saved without one is hard, which is what an absent softness reads as. */
private fun softnessOnBrushStrokes(document: JsonObject): JsonObject = document

/** Format 1 to 2: a part's grain moves from the part to its surface work, its own or, on a paired part, its side's override. */
private fun grainIntoSurfaceWork(document: JsonObject): JsonObject {
    val character = document.getValue(CHARACTER_KEY).jsonObject
    val parts = character[PARTS_KEY]?.jsonArray ?: return document
    val moved = JsonArray(parts.map { movedGrain(it.jsonObject) })
    return JsonObject(document + (CHARACTER_KEY to JsonObject(character + (PARTS_KEY to moved))))
}

private fun movedGrain(part: JsonObject): JsonObject {
    val grain = part[GRAIN_KEY] ?: return part
    val paired = part[PAIR_KEY].let { it != null && it !is JsonNull }
    val into = if (paired) OVERRIDES_KEY else OWN_KEY
    val data = part[into]?.jsonObject ?: JsonObject(emptyMap())
    val surface = JsonObject(mapOf(GRAIN_KEY to grain))
    return JsonObject(part - GRAIN_KEY + (into to JsonObject(data + (SURFACE_KEY to surface))))
}
