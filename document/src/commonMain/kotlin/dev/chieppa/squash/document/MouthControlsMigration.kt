package dev.chieppa.squash.document

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

private const val GROUPS_KEY = "groups"
private const val CONTROLS_KEY = "controls"
private const val OPEN_KEY = "open"
private const val WIDTH_KEY = "width"
private const val MOUTH_SHAPE_KEY = "mouthShape"
private const val WIDE_KEY = "wide"
private const val ROUND_KEY = "round"
private const val SMILE_KEY = "smile"

/** How far a saved width of 1.25 draws the corners out, as `wide` 1. */
private const val WIDTH_PER_WIDE = 0.25f

/** The smile a saved grimace bares, and how wide it draws. */
private const val GRIMACE_SMILE = -0.5f
private const val GRIMACE_WIDE = 0.5f

/**
 * Format 20 to 21: a mouth's controls lose `width` and `mouthShape` for the shaping controls. A width becomes `wide`; a smile or frown
 * `smile`, an O `round` and a grimace a wide frown. A group saved without an `open` had it at 1, now written, since a group left out rests closed.
 */
internal fun mouthControlsShape(document: JsonObject): JsonObject = withEverySurface(document) { surface ->
    val groups = surface[GROUPS_KEY] as? JsonArray ?: return@withEverySurface surface
    JsonObject(surface + (GROUPS_KEY to JsonArray(groups.map(::groupShaped))))
}

private fun groupShaped(saved: JsonElement): JsonElement {
    val group = saved as? JsonObject ?: return saved
    val controls = group[CONTROLS_KEY] as? JsonObject ?: JsonObject(emptyMap())
    val width = controls[WIDTH_KEY]?.jsonPrimitive?.floatOrNull
    val shape = controls[MOUTH_SHAPE_KEY]?.jsonPrimitive?.takeIf { it.isString }?.content
    val shaped = buildMap<String, JsonElement> {
        putAll(controls - WIDTH_KEY - MOUTH_SHAPE_KEY)
        if (OPEN_KEY !in controls) put(OPEN_KEY, JsonPrimitive(1f))
        val wide = (width?.let { (it - 1f) / WIDTH_PER_WIDE } ?: 0f) + if (shape == "GRIMACE") GRIMACE_WIDE else 0f
        if (wide != 0f) put(WIDE_KEY, JsonPrimitive(wide.coerceIn(-1f, 1f)))
        when (shape) {
            "SMILE" -> put(SMILE_KEY, JsonPrimitive(1f))
            "FROWN" -> put(SMILE_KEY, JsonPrimitive(-1f))
            "GRIMACE" -> put(SMILE_KEY, JsonPrimitive(GRIMACE_SMILE))
            "O" -> put(ROUND_KEY, JsonPrimitive(1f))
        }
    }
    return JsonObject(group + (CONTROLS_KEY to JsonObject(shaped)))
}
