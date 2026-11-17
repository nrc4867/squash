package dev.chieppa.squash.document

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val CHARACTER_KEY = "character"
private const val PARTS_KEY = "parts"
private const val PAIR_KEY = "pair"
private const val OWN_KEY = "own"
private const val SURFACE_KEY = "surface"
private const val MARKS_KEY = "marks"
private const val GROUPS_KEY = "groups"
private const val ROLES_KEY = "roles"
private const val ROLE_KEY = "role"
private const val FILL_KEY = "fill"
private const val POINTS_KEY = "points"
private const val ID_KEY = "id"
private const val PART_KEY = "part"
private const val PATCH_KEY = "patch"
private const val U_KEY = "u"
private const val V_KEY = "v"
private const val ANCHOR_KEY = "anchor"
private const val LOOK_KEY = "look"
private const val PAINTED_EYES_KEY = "paintedEyes"
private const val EXPRESSIONS_KEY = "expressions"
private const val GAZE_CHEATS_KEY = "gazeCheats"

private const val LID_ROLE = "lid"
private const val LASHES_ROLE = "lashes"
private const val BROW_ROLE = "brow"

/** The look key each eye role's fill becomes. */
private val FILLS = mapOf("white" to "white", "iris" to "iris", "pupil" to "pupil", "highlight" to "highlight")

/** The look key that says whether a painted eye draws what each drawn role held. */
private val DRAWN_LINES = mapOf(LID_ROLE to "lid", LASHES_ROLE to "lashes", BROW_ROLE to "brow")

/** Every role a drawn eye's group held. */
private val EYE_ROLES = setOf("white", "iris", "pupil", "highlight", LID_ROLE, LASHES_ROLE, BROW_ROLE)

/** The id painted eyes made from drawn ones take, or this followed by the head's id when a painted eye holds it already. */
private const val PAINTED_EYES_ID = "eyes"

/** Halfway round a part, past which a v lies on its right. */
private const val HALF_ROUND = 0.5f

/**
 * [document], a saved document, with every drawn eye gone: the character's drawn eyes painted (see [drawnEyesPainted]) and every eye
 * group left over dropped (see [leftoverEyeGroupsDropped]). A drawn eye group is one holding a role of an eye.
 */
internal fun drawnEyesRemoved(document: JsonObject): JsonObject {
    val character = document.getValue(CHARACTER_KEY).jsonObject
    return leftoverEyeGroupsDropped(JsonObject(document + (CHARACTER_KEY to drawnEyesPainted(character))))
}

/**
 * [character], a saved character's JSON, with its drawn eyes painted: every unpaired part loses its eye groups, by their lid, and the
 * marks they hold, and the expressions lose those groups' marks. A part that had marks in one and no painted eye gains a mirrored
 * painted pair, its middle and its opening's half width and height those of the left eye's lid, or of its marks when it has no lid,
 * with the fills of its white, iris, pupil and highlight and a lid, lashes and brow where it drew them. A mouth stays as drawn, and the character's
 * gaze cheats, which only drawn pupils read, go.
 */
internal fun drawnEyesPainted(character: JsonObject): JsonObject {
    val parts = character[PARTS_KEY] as? JsonArray ?: return character
    val painted = (character[PAINTED_EYES_KEY] as? JsonArray).orEmpty().map { it.jsonObject }.toMutableList()
    val dropped = mutableSetOf<String>()
    val kept = mutableSetOf<String>()
    val moved = parts.map { element ->
        val part = element.jsonObject
        val surface = eyeSurfaceOf(part) ?: return@map part.also { kept += groupIdsOf(it) }
        val groups = surface[GROUPS_KEY]?.jsonArray.orEmpty().map { it.jsonObject }
        val (eyes, others) = groups.partition(::isEyeGroup)
        dropped += eyes.map { it.getValue(ID_KEY).jsonPrimitive.content }
        kept += others.map { it.getValue(ID_KEY).jsonPrimitive.content }
        val marks = surface[MARKS_KEY]?.jsonArray.orEmpty().map { it.jsonObject }
        val going = eyes.flatMap { markIdsOf(it) }.toSet() - others.flatMap { markIdsOf(it) }.toSet()
        val partId = part.getValue(ID_KEY).jsonPrimitive.content
        if (painted.none { it[ANCHOR_KEY]?.jsonObject?.get(PART_KEY)?.jsonPrimitive?.content == partId }) {
            val byId = marks.associateBy { it.getValue(ID_KEY).jsonPrimitive.content }
            paintedPair(partId, eyes, byId, painted)?.let(painted::add)
        }
        val left = JsonObject(surface + (GROUPS_KEY to JsonArray(others)) + (MARKS_KEY to JsonArray(marks.filter { it.getValue(ID_KEY).jsonPrimitive.content !in going })))
        val own = part.getValue(OWN_KEY).jsonObject
        JsonObject(part + (OWN_KEY to JsonObject(own + (SURFACE_KEY to left))))
    }
    val gone = dropped - kept
    val expressions = (character[EXPRESSIONS_KEY] as? JsonArray)?.let { held ->
        JsonArray(held.map { expression ->
            val shown = expression.jsonObject
            val marks = shown[MARKS_KEY] as? JsonObject ?: return@map shown
            JsonObject(shown + (MARKS_KEY to JsonObject(marks - gone)))
        })
    }
    val eyes = if (painted.isEmpty()) emptyList() else listOf(PAINTED_EYES_KEY to JsonArray(painted))
    return JsonObject(character - GAZE_CHEATS_KEY + (PARTS_KEY to JsonArray(moved)) + eyes + listOfNotNull(expressions?.let { EXPRESSIONS_KEY to it }))
}

/**
 * [document] with every eye group still held, on a paired part, its sides' overrides or a pair's data, dropped with the marks only
 * those groups held and the expressions' marks for them. No painted eye replaces them.
 */
internal fun leftoverEyeGroupsDropped(document: JsonObject): JsonObject {
    val dropped = mutableSetOf<String>()
    val kept = mutableSetOf<String>()
    val swept = withEverySurface(document) { surface ->
        val groups = surface[GROUPS_KEY]?.jsonArray.orEmpty().map { it.jsonObject }
        val (eyes, others) = groups.partition(::isEyeGroup)
        kept += others.map { it.getValue(ID_KEY).jsonPrimitive.content }
        if (eyes.isEmpty()) return@withEverySurface surface
        dropped += eyes.map { it.getValue(ID_KEY).jsonPrimitive.content }
        val going = eyes.flatMap { markIdsOf(it) }.toSet() - others.flatMap { markIdsOf(it) }.toSet()
        val marks = surface[MARKS_KEY]?.jsonArray.orEmpty().filter { it.jsonObject.getValue(ID_KEY).jsonPrimitive.content !in going }
        JsonObject(surface + (GROUPS_KEY to JsonArray(others)) + (MARKS_KEY to JsonArray(marks)))
    }
    val gone = dropped - kept
    val character = swept.getValue(CHARACTER_KEY).jsonObject
    val expressions = (character[EXPRESSIONS_KEY] as? JsonArray)?.let { held ->
        JsonArray(held.map { expression ->
            val shown = expression.jsonObject
            val marks = shown[MARKS_KEY] as? JsonObject ?: return@map shown
            JsonObject(shown + (MARKS_KEY to JsonObject(marks - gone)))
        })
    } ?: return swept
    return JsonObject(swept + (CHARACTER_KEY to JsonObject(character + (EXPRESSIONS_KEY to expressions))))
}

/** The surface work of [part]'s own when it is unpaired and holds an eye group, else null. */
private fun eyeSurfaceOf(part: JsonObject): JsonObject? {
    if (part[PAIR_KEY].let { it != null && it !is JsonNull }) return null
    val surface = (part[OWN_KEY] as? JsonObject)?.get(SURFACE_KEY) as? JsonObject ?: return null
    return surface.takeIf { held -> held[GROUPS_KEY]?.jsonArray.orEmpty().any { isEyeGroup(it.jsonObject) } }
}

private fun groupIdsOf(part: JsonObject): List<String> {
    val surface = (part[OWN_KEY] as? JsonObject)?.get(SURFACE_KEY) as? JsonObject
    return surface?.get(GROUPS_KEY)?.jsonArray.orEmpty().map { it.jsonObject.getValue(ID_KEY).jsonPrimitive.content }
}

private fun rolesOf(group: JsonObject): List<JsonObject> = group[ROLES_KEY]?.jsonArray.orEmpty().map { it.jsonObject }

private fun roleName(role: JsonObject): String = role.getValue(ROLE_KEY).jsonPrimitive.content

private fun isEyeGroup(group: JsonObject): Boolean = rolesOf(group).any { roleName(it) in EYE_ROLES }

private fun roleMarkIds(role: JsonObject): List<String> = role[MARKS_KEY]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }

private fun markIdsOf(group: JsonObject, onlyRole: String? = null): List<String> =
    rolesOf(group).filter { onlyRole == null || roleName(it) == onlyRole }.flatMap(::roleMarkIds)

/** A point of a drawn mark, on [patch] at [u] and [v]. */
private class Point(val patch: String, val u: Float, val v: Float)

private fun pointsOf(ids: List<String>, marks: Map<String, JsonObject>): List<Point> = ids.mapNotNull(marks::get).flatMap { mark ->
    mark[POINTS_KEY]?.jsonArray.orEmpty().map { element ->
        val point = element.jsonObject
        Point(point[PATCH_KEY]?.jsonPrimitive?.content ?: BODY_PATCH, point.getValue(U_KEY).jsonPrimitive.float, point.getValue(V_KEY).jsonPrimitive.float)
    }
}

/** The points an eye group is placed by, on the patch most of them lie on: its lid's, or all of its marks' when the lid holds none. */
private fun placingPoints(group: JsonObject, marks: Map<String, JsonObject>): List<Point> {
    val points = pointsOf(markIdsOf(group, LID_ROLE), marks).ifEmpty { pointsOf(markIdsOf(group), marks) }
    val patch = points.groupingBy { it.patch }.eachCount().maxByOrNull { it.value }?.key ?: return emptyList()
    return points.filter { it.patch == patch }
}

/** The mirrored painted eye made from [eyes] on [part], or null when none of them holds a mark. */
private fun paintedPair(part: String, eyes: List<JsonObject>, marks: Map<String, JsonObject>, painted: List<JsonObject>): JsonObject? {
    val placed = eyes.map { it to placingPoints(it, marks) }.filter { it.second.isNotEmpty() }
    if (placed.isEmpty()) return null
    fun middleV(points: List<Point>) = (points.minOf { it.v } + points.maxOf { it.v }) / 2f
    val (group, points) = placed.firstOrNull { middleV(it.second) <= HALF_ROUND } ?: placed.first()
    val u = (points.minOf { it.u } + points.maxOf { it.u }) / 2f
    val v = middleV(points).let { if (it > HALF_ROUND) 1f - it else it }
    val roles = rolesOf(group)
    val fills = roles.mapNotNull { role ->
        val key = FILLS[roleName(role)] ?: return@mapNotNull null
        (role[FILL_KEY] as? JsonPrimitive)?.let { key to it }
    }
    val lines = DRAWN_LINES.map { (role, key) -> key to JsonPrimitive(roles.any { roleName(it) == role && roleMarkIds(it).isNotEmpty() }) }
    val look = JsonObject(
        mapOf(
            "width" to JsonPrimitive((points.maxOf { it.u } - points.minOf { it.u }) / 2f),
            "height" to JsonPrimitive((points.maxOf { it.v } - points.minOf { it.v }) / 2f),
        ) + fills + lines,
    )
    val taken = painted.map { it.getValue(ID_KEY).jsonPrimitive.content }.toSet()
    val id = if (PAINTED_EYES_ID in taken) "$PAINTED_EYES_ID-$part" else PAINTED_EYES_ID
    val anchor = JsonObject(mapOf(PART_KEY to JsonPrimitive(part), PATCH_KEY to JsonPrimitive(points.first().patch), U_KEY to JsonPrimitive(u), V_KEY to JsonPrimitive(v)))
    return JsonObject(mapOf<String, JsonElement>(ID_KEY to JsonPrimitive(id), ANCHOR_KEY to anchor, LOOK_KEY to look))
}
