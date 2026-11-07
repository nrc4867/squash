package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.readDocument
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** How near two solves of the same pose must agree, in body units. */
private const val SAME = 1e-5f

/** The fennec poses by its parts' roles: with every template id renamed, its rig, handles and solves are the same. */
class RolesNotTemplatesTest {
    private val renamed = mapOf("torso" to "kind-a", "head" to "kind-b", "tail" to "kind-c", "ear" to "kind-d", "leg" to "kind-e", "paw" to "kind-f", "wing-feathered" to "kind-g")
    private val templates: Path = Path.of(System.getProperty("squash.templates"))

    @Test
    fun theFennecWithEveryTemplateIdRenamedPosesTheSame() {
        val folder = renamedFolder()
        val document = readDocument(rename(Json.parseToJsonElement(Path.of(System.getProperty("squash.examples"), "fennec.json").readText()), Spot.DOCUMENT).toString())
        assertEquals((renamed - "wing-feathered").values.toSet(), document.character.parts.map { it.template.id.value }.toSet())
        val rig = rigOf(document, TemplateLibrary(folder.parts, folder.plans))
        val shipped = fennec.rig

        assertEquals(shipped.bones.map { it.name }, rig.bones.map { it.name })
        assertEquals(shipped.parents.toList(), rig.parents.toList())
        shipped.bones.indices.forEach { assertNear(shipped.bones[it].pivot, rig.bones[it].pivot) }

        val before = PoseSolver(shipped)
        val after = PoseSolver(rig)
        assertEquals(before.chains.root, after.chains.root)
        assertEquals(before.chains.limbs.map { listOf(it.leg, it.paw, it.upper, it.lower, it.end, it.foot) }, after.chains.limbs.map { listOf(it.leg, it.paw, it.upper, it.lower, it.end, it.foot) })
        assertEquals(before.chains.tails.map { it.part to it.bones }, after.chains.tails.map { it.part to it.bones })
        assertEquals(before.chains.look?.let { it.head to it.neck }, after.chains.look?.let { it.head to it.neck })
        assertEquals(curveBones(shipped)?.let { listOf(it.behind, it.spine, it.ahead, it.stretched) }, curveBones(rig)?.let { listOf(it.behind, it.spine, it.ahead, it.stretched) })

        val planted = Pose(planted = before.chains.limbs.map { it.id }.toSet())
        val limb = assertNotNull(before.chains.limb(PartId("front-paw-l")))
        val tail = before.chains.tails.single()
        val solves = listOf<(PoseSolver) -> Pose>(
            { it.reach(planted.withPlanted(limb.id, false), limb, it.endOf(planted, limb) + Vec3(0.1f, 0.15f, 0f)).pose },
            { it.moveBody(planted, Vec3(0f, -0.08f, 0.1f)).pose },
            { it.curl(planted, tail, Vec3(-0.6f, 0.9f, 0.2f)).pose },
            { it.look(planted, Vec3(1.2f, 0.6f, 0.4f)).pose },
        )
        solves.forEach { solve ->
            val one = solve(before)
            val other = solve(after)
            assertEquals(one, other)
            val handles = before.handles(one)
            val same = after.handles(other)
            assertEquals(handles.ends.keys, same.ends.keys)
            handles.ends.forEach { (id, at) -> assertNear(at, same.ends.getValue(id)) }
            assertEquals(handles.curls.size, same.curls.size)
            handles.curls.zip(same.curls).forEach { (a, b) -> assertNear(a.tip, b.tip) }
            assertNear(checkNotNull(handles.look), checkNotNull(same.look))
            assertEquals(handles.pins.keys, same.pins.keys)
        }
    }

    private fun assertNear(expected: Vec3, actual: Vec3) = kotlin.test.assertTrue((expected - actual).length < SAME, "$expected and $actual differ")

    /** The shipped templates and plans with every template id renamed, written to a folder of their own and loaded from it. */
    private fun renamedFolder(): TemplateFolder {
        val out = Files.createTempDirectory("renamed-templates")
        out.resolve("plans").createDirectories()
        templates.listDirectoryEntries("*.part.json").forEach { file ->
            val id = renamed.keys.first { file.name == "$it.part.json" || file.name.startsWith("$it-") }
            out.resolve(renamed.getValue(id) + file.name.removePrefix(id)).writeText(rename(Json.parseToJsonElement(file.readText()), Spot.TEMPLATE).toString())
        }
        templates.resolve("plans").listDirectoryEntries("*.plan.json").forEach { file ->
            out.resolve("plans").resolve(file.name).writeText(rename(Json.parseToJsonElement(file.readText()), Spot.PLAN).toString())
        }
        return TemplateFolder.load(out)
    }

    private enum class Spot { TEMPLATE, PLAN, DOCUMENT }

    /**
     * [element] with every template id renamed where a [spot] names one: a template's id and its sockets' takes, a plan's root, sockets and
     * slots, a document's parts' templates. [key] is the element's own key and [within] the key of the object holding it.
     */
    private fun rename(element: JsonElement, spot: Spot, key: String? = null, within: String? = null): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (k, v) -> rename(v, spot, k, key) })
        is JsonArray -> JsonArray(element.map { rename(it, spot, key, within) })
        is JsonPrimitive -> {
            val names = when (spot) {
                Spot.TEMPLATE -> (key == "id" && within == null) || key == "takes"
                Spot.PLAN -> key == "template" || key == "on" || key == "takes"
                Spot.DOCUMENT -> key == "id" && within == "template"
            }
            if (element.isString && names) JsonPrimitive(renamed[element.content] ?: element.content) else element
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun rigOf(document: SquashDocument, library: TemplateLibrary): Rig {
        var rigged: Rig? = null
        runTest {
            val session = CharacterSession(document, library, backgroundScope, StandardTestDispatcher(testScheduler))
            val node = SessionRig(session).rig
            runCurrent()
            rigged = checkNotNull(node.output.value)
        }
        return checkNotNull(rigged)
    }
}
