package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.GarmentSlotId
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.PartBind
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PickGarment
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SetPartBind
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.recipe.RecipeJson
import dev.chieppa.squash.recipe.build.RecipeBuilder
import dev.chieppa.squash.rig.Rig
import dev.chieppa.squash.rig.posed
import dev.chieppa.squash.screens.look.measure.shellPokes
import dev.chieppa.squash.screens.look.recipe.KeptSession
import dev.chieppa.squash.screens.look.recipe.OpenSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parts a garment owns bound to the body: the dressed woman's own heels, lapels, button, buckle, pendant and shades ride one bone by the
 * rule, while a shell round her leg over the knee follows the leg's bones, bends with the knee and keeps off the leg when it bends.
 * The leg may come through the shell only in the knee's closed crease: behind the knee and in the groove at its sides, where thigh and
 * calf both lie within reach and the body pinches into itself, so any surface there lies inside one of them. A poke anywhere else fails.
 */
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnedBindTest {
    private val kept = KeptSession()
    private lateinit var open: OpenSession

    @BeforeAll
    fun build() {
        val folder = File(checkNotNull(System.getProperty("squash.templates"))).parentFile
        val text = File(folder, "recipes/woman-dressed.recipe.json").readText()
        open = kept.open(RecipeBuilder(TemplateFolder.shipped()).build(RecipeJson.read(withShell(text))))
        listOf("blazer" to "outer", "belt" to "waist", "choker" to "neck", "shades" to "eyes", SHELLS.value to "knees").forEach { (garment, slot) ->
            open.session.send(PickGarment(OFFICE, GarmentSlotId(slot), GarmentId(garment)))
        }
        open.session.send(ShowOutfit(OFFICE))
        open.work.settle(open.session)
    }

    @AfterAll
    fun close() = kept.close()

    private val rig: Rig get() = checkNotNull(open.pose.rig.rig.output.value)

    @Test
    fun theExamplesOwnedPartsRideOneBone() {
        val owned = open.session.document.value.character.parts.filter { it.garment != null && it.garment != SHELLS }.map { it.id }
        val rigged = owned.mapNotNull(rig::part)
        assertTrue(rigged.size >= OWNED_WORN, "the office outfit wears the example's owned parts: ${rigged.map { it.id.value }}")
        rigged.forEach { assertNull(it.hold, "${it.id.value} stays on one bone") }
    }

    @Test
    fun aShellRoundTheKneeFollowsTheLegsBones() {
        val shell = assertNotNull(rig.part(SHELL_L))
        assertNotNull(shell.hold)
        val bones = (0 until shell.vertexCount).flatMap { v -> shell.weights.of(v).map { it.first } }.toSet()
        assertTrue(bones.size >= 2, "the shell takes the thigh's and the shin's bones: ${bones.map { rig.bones[it].name }}")
    }

    @Test
    fun aBentKneeKeepsTheShellOffTheLeg() {
        for (bend in listOf(KNEE_BEND, DEEP_BEND)) {
            val pokes = assertNotNull(shellPokes(rig, rig.posed(Pose(listOf(BonePose(LEG_L, KNEE, degrees = bend)))), SHELL_L))
            assertEquals(0, pokes.outsideCrease, "at $bend degrees the leg comes through the shell outside the closed crease behind the knee: $pokes")
        }
    }

    @Test
    fun theShellsOwnBoneStillMovesIt() {
        val skin = rig.posed(Pose(listOf(BonePose(LEG_L, KNEE, degrees = KNEE_BEND)))).parts.getValue(SHELL_L)
        val turned = rig.posed(Pose(listOf(BonePose(LEG_L, KNEE, degrees = KNEE_BEND), BonePose(SHELL_L, BASE, degrees = OWN_TURN)))).parts.getValue(SHELL_L)
        val most = (0 until skin.size / 3).maxOf { v -> Vec3(turned[v * 3] - skin[v * 3], turned[v * 3 + 1] - skin[v * 3 + 1], turned[v * 3 + 2] - skin[v * 3 + 2]).length }
        assertTrue(most > OWN_MOVES, "turning the shell's own bone moves it over the skin: $most")
    }

    @Test
    fun singleKeepsTheShellOnOneBoneAndUndoBindsItAgain() {
        open.session.send(SetPartBind(SHELL_L, PartBind.SINGLE, null))
        open.work.settle(open.session)
        assertNull(rig.part(SHELL_L)?.hold)
        assertEquals(PartBind.SINGLE, open.session.document.value.character.part(SHELL_R)?.bind)
        open.session.history.undo()
        open.work.settle(open.session)
        assertNotNull(rig.part(SHELL_L)?.hold)
    }

    @Test
    fun aPoseOnlySkinsTheBoundShell() {
        val before = rig
        open.session.send(SetPose(Pose(listOf(BonePose(LEG_L, KNEE, degrees = KNEE_BEND)))))
        open.work.settle(open.session)
        assertTrue(before === rig, "a pose builds no new rig")
        open.session.history.undo()
        open.work.settle(open.session)
    }

    /** [recipe] with a pair of stocking shells round the legs over the knees, owned by a garment of a new knees slot. */
    private fun withShell(recipe: String): String {
        val root = Json.parseToJsonElement(recipe).jsonObject
        val parts = root.getValue("parts").jsonArray.map { part ->
            if (part.jsonObject["id"]?.jsonPrimitive?.content != "legs") return@map part
            val sockets = part.jsonObject["sockets"]?.jsonArray.orEmpty() + Json.parseToJsonElement(SHELL_SOCKET)
            JsonObject(part.jsonObject + ("sockets" to JsonArray(sockets)))
        } + Json.parseToJsonElement(SHELL_PART)
        val wardrobe = root.getValue("wardrobe").jsonObject
        val slots = wardrobe.getValue("slots").jsonArray + Json.parseToJsonElement("""{"id": "knees", "name": "Knees"}""")
        val garments = wardrobe.getValue("garments").jsonArray + Json.parseToJsonElement("""{"id": "${SHELLS.value}", "name": "Shells", "slot": "knees", "color": "boot"}""")
        val dressed = JsonObject(wardrobe + ("slots" to JsonArray(slots)) + ("garments" to JsonArray(garments)))
        return JsonObject(root + ("parts" to JsonArray(parts)) + ("wardrobe" to dressed)).toString()
    }

    private companion object {
        val OFFICE = OutfitId("office")
        val SHELLS = GarmentId("stocking-shells")
        val SHELL_L = PartId("shells-l")
        val SHELL_R = PartId("shells-r")
        val LEG_L = PartId("leg-l")
        const val KNEE = "joint.1"
        const val BASE = "base"
        const val KNEE_BEND = 100f
        const val DEEP_BEND = 130f


        /** Degrees the shell's own bone turns on the leg. */
        const val OWN_TURN = 20f

        /** The least a turn of the shell's own bone carries its far end, in body units. */
        const val OWN_MOVES = 0.02f

        /** Heels, lapels, button, buckle, pendant, shades and temples, each side counted. */
        const val OWNED_WORN = 11

        const val SHELL_SOCKET = """{"n": 9, "u": 0.3072, "v": 0.0, "sink": 0.0}"""

        /** A tube round the left leg from mid-thigh to mid-shin, 0.01 off it, both ends tucked inside it. */
        const val SHELL_PART = """{"id": "shells", "template": "tube", "version": 1, "on": "legs", "socket": "socket.9",
            "pair": {"left": "shells-l", "right": "shells-r"}, "role": "other", "garment": "stocking-shells",
            "outlines": {"side": {"through": [[0.0, -0.006], [0.008, 0.0094], [0.04, 0.0052], [0.08, -0.0003], [0.12, -0.0058], [0.16, -0.0113],
              [0.2, -0.0138], [0.24, -0.0154], [0.28, -0.0178], [0.32, -0.0202], [0.36, -0.023], [0.392, -0.0254], [0.4, -0.042], [0.4, -0.1375],
              [0.392, -0.155], [0.36, -0.1612], [0.32, -0.1591], [0.28, -0.1543], [0.24, -0.1506], [0.2, -0.1496], [0.16, -0.1508], [0.12, -0.1575],
              [0.08, -0.1643], [0.04, -0.171], [0.008, -0.1762], [0.0, -0.1611]]},
             "top": {"through": [[0.0, 0.077], [0.008, 0.0923], [0.04, 0.0879], [0.08, 0.0823], [0.12, 0.0766], [0.16, 0.071], [0.2, 0.0674],
              [0.24, 0.066], [0.28, 0.0676], [0.32, 0.0692], [0.36, 0.0677], [0.392, 0.0639], [0.4, 0.0469], [0.4, -0.0469], [0.392, -0.0639],
              [0.36, -0.0677], [0.32, -0.0692], [0.28, -0.0676], [0.24, -0.066], [0.2, -0.0674], [0.16, -0.071], [0.12, -0.0766], [0.08, -0.0823],
              [0.04, -0.0879], [0.008, -0.0923], [0.0, -0.077]]}},
            "rest": "down", "generic": {"rings": 64, "segments": 24}, "color": "boot"}"""
    }
}
