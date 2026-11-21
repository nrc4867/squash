package dev.chieppa.squash.document

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** A format 20 mouth's width and shape become the shaping controls, and one saved without an open stays open. */
class MouthControlsMigrationTest {
    private val head = PartInstance(PartId("head-1"), TemplateRef(TemplateId("head"), 1))
    private val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    private val mouth = mouthGroup(MarkGroupId("mouth"), "Mouth")
    private val placed = SetMarkGroup(head.id, mouth.id, mouth).apply(AddPart(head).apply(empty).document).document

    /** [placed] as format 20 saved it, its mouth's controls written as [controls]. */
    private fun savedWith(controls: String): String {
        val tree = Json.encodeToJsonElement(SquashDocument.serializer(), placed).jsonObject
        val character = tree.getValue("character").jsonObject
        val part = character.getValue("parts").jsonArray.single().jsonObject
        val own = part.getValue("own").jsonObject
        val surface = own.getValue("surface").jsonObject
        val group = surface.getValue("groups").jsonArray.single().jsonObject
        val saved = JsonObject(group + ("controls" to Json.parseToJsonElement(controls)))
        val withGroup = JsonObject(surface + ("groups" to kotlinx.serialization.json.JsonArray(listOf(saved))))
        val withOwn = JsonObject(part + ("own" to JsonObject(own + ("surface" to withGroup))))
        val withPart = JsonObject(character + ("parts" to kotlinx.serialization.json.JsonArray(listOf(withOwn))))
        return JsonObject(tree + ("character" to withPart) + ("format" to JsonPrimitive(20))).toString()
    }

    private fun controlsOf(text: String) = checkNotNull(readDocument(text).character.resolved(head.id)).surface.groups.single().controls

    @Test
    fun aMouthSavedWithoutAnOpenStaysOpen() {
        assertEquals(MarkControls(open = 1f), controlsOf(savedWith("{}")))
        assertEquals(MarkControls(open = 0f), controlsOf(savedWith("""{"open": 0}""")))
    }

    @Test
    fun aWidthAndAShapeBecomeTheShapingControls() {
        assertEquals(MarkControls(open = 0.5f, wide = 1f, smile = 1f), controlsOf(savedWith("""{"open": 0.5, "width": 1.25, "mouthShape": "SMILE"}""")))
        assertEquals(MarkControls(open = 0f, round = 1f), controlsOf(savedWith("""{"open": 0, "mouthShape": "O"}""")))
        assertEquals(MarkControls(open = 0f, smile = -1f, wide = -1f), controlsOf(savedWith("""{"open": 0, "width": 0.5, "mouthShape": "FROWN"}""")))
    }
}
