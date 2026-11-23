package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.Claws
import dev.chieppa.squash.character.build.JointTree
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.MeshInvariants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The template versions that offer hard claws, by id and version, and the digit row each claws block sits on. */
private val CLAWED = listOf(Triple("hand", 10, "fingers"), Triple("foot", 11, "toes"), Triple("paw", 13, "toes"), Triple("foot-bird", 6, "toes"))

private val ON = mapOf("clawParts" to 1f)

/**
 * Hard claws: a claws block on each digit kind's newest template builds one claw a digit, its own mesh out of the field, closed, past
 * the digit's tip, riding two rigid joints that hang from the digit's last; off by default, the template builds what the version
 * before it builds, and on, it leaves the row's nails and grown claws out.
 */
class ClawPartsTest {
    private fun template(id: String, version: Int): PartTemplate = shipped.parts.single { it.id.value == id && it.version == version }

    @Test
    fun offByDefaultEachVersionBuildsWhatTheOneBeforeItBuilds() {
        for ((id, version, _) in CLAWED) {
            val clawed = template(id, version)
            assertEquals(emptyList(), TemplateLint.lint(clawed), "$id $version")
            val before = PartBuilder.build(template(id, version - 1))
            val now = PartBuilder.build(clawed)
            assertEquals(before.map { it.name }, now.map { it.name }, "$id $version")
            before.zip(now).forEach { (a, b) -> assertTrue(a.points.contentEquals(b.points), "$id $version ${a.name} keeps its points") }
            assertEquals(PartBuilder.areas(template(id, version - 1), emptyMap()).map { it.name }, PartBuilder.areas(clawed, emptyMap()).map { it.name })
        }
    }

    @Test
    fun eachDigitGetsAClosedClawOfItsOwnPastItsTip() {
        for ((id, version, row) in CLAWED) {
            val clawed = template(id, version)
            val patches = PartBuilder.build(clawed, ON)
            val digits = patches.filter { it.name.startsWith("$row:") }
            val claws = patches.filter { it.name.startsWith("claws:") }
            assertEquals(digits.map { "claws:" + it.name.substringAfter(':') }, claws.map { it.name }, "$id: a claw on every digit")
            assertTrue(clawed.blocks.filterIsInstance<Claws>().all { it.surfaceMethod() == SurfaceMethod.SEPARATE }, "$id: claws are their own mesh")
            claws.forEach { claw ->
                assertEquals(emptyList(), MeshInvariants.check(MeshBuilder.build(claw) { 0 }, emptySet()), "$id ${claw.name}")
                val digit = digits.single { it.name.substringAfter(':') == claw.name.substringAfter(':') }
                val tip = claw.point(claw.index(claw.nu - 1, 0))
                val root = digit.point(digit.index(0, 0))
                val digitReach = (0 until digit.nu * digit.nv).maxOf { (digit.point(it) - root).length }
                assertTrue((tip - root).length > digitReach, "$id ${claw.name}: its point stands past its digit")
            }
        }
    }

    @Test
    fun aClawRidesTwoRigidJointsFromItsDigitsLast() {
        for ((id, version, row) in CLAWED) {
            val clawed = template(id, version)
            val tree = JointTree(clawed, PartBuilder.build(clawed, ON).map { it.name })
            tree.digits.filter { it.on != null }.forEach { claw ->
                assertTrue(claw.rigid, "$id ${claw.patch}")
                assertEquals(listOf("${claw.patch}.1", "${claw.patch}.2"), claw.joints.map { it.name })
                assertEquals("$row:" + claw.patch.substringAfter(':'), claw.on)
                val digit = tree.digits.indexOfFirst { it.patch == claw.on }
                val last = tree.indexOf(digit, tree.digits[digit].joints.size - 1)
                assertEquals(last, tree.rootOf(tree.digits.indexOf(claw)), "$id ${claw.patch} hangs from its digit's last joint")
            }
            assertTrue(tree.digits.any { it.on != null }, "$id has claws")
        }
    }

    @Test
    fun hardClawsLeaveTheNailsAndGrownClawsOut() {
        val paw = template("paw", 13)
        val names = PartBuilder.areas(paw, ON).map { it.name }
        assertTrue(names.none { it.startsWith("claw:") }, "no grown claw's area")
        assertTrue("claws" in names && Claws.SHEATH_AREA in names, "the claws and their collars are areas of their own")
        val grown = PartBuilder.build(paw).first { it.name == "toes:0" }
        val hard = PartBuilder.build(paw, ON).first { it.name == "toes:0" }
        assertTrue(hard.ringUs.last() < grown.ringUs.last(), "the toe no longer grows its claw past its tip")
    }
}
