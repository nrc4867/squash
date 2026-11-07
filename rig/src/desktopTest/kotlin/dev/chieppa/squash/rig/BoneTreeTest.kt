package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.golden.GoldenFixture
import java.nio.file.Path
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoneTreeTest {
    private val rig get() = fennec.rig

    private fun name(text: String) = text.split('/').let { (part, joint) -> BoneName(PartId(part), joint) }

    private fun parentOf(bone: String) = rig.bone(name(bone))?.parent?.toString()

    @Test
    fun everyTemplateJointOfTheFennecIsABone() {
        val legs = listOf("front-leg-l", "front-leg-r", "back-leg-l", "back-leg-r").flatMap { leg -> listOf("hip", "knee", "ankle").map { "$leg/$it" } }
        val paws = listOf("front-paw-l", "front-paw-r", "back-paw-l", "back-paw-r").map { "$it/wrist" }
        val expected = listOf("torso/hips", "torso/loin", "torso/waist", "torso/ribs", "torso/chest", "torso/withers", "head/jaw") +
            listOf("ear-l", "ear-r").flatMap { ear -> listOf("base", "fold", "tip").map { "$ear/$it" } } +
            listOf("root", "tail1", "tail2", "tail3", "tail4", "tail5", "tip").map { "tail/$it" } + legs + paws
        assertEquals(36, rig.bones.size)
        assertEquals(expected.sorted(), rig.bones.map { it.name.toString() }.sorted())
    }

    @Test
    fun theSocketTreeGivesTheBoneTree() {
        assertEquals(null, parentOf("torso/hips"))
        assertEquals("torso/hips", parentOf("torso/loin"))
        assertEquals("torso/chest", parentOf("head/jaw"))
        assertEquals("head/jaw", parentOf("ear-l/base"))
        assertEquals("ear-r/base", parentOf("ear-r/fold"))
        assertEquals("torso/hips", parentOf("tail/root"))
        assertEquals("torso/chest", parentOf("front-leg-l/hip"))
        assertEquals("torso/hips", parentOf("back-leg-r/hip"))
        assertEquals("back-leg-l/hip", parentOf("back-leg-l/knee"))
        assertEquals("back-leg-l/knee", parentOf("back-leg-l/ankle"))
        assertEquals("front-leg-r/ankle", parentOf("front-paw-r/wrist"))
        assertTrue(rig.parents.withIndex().all { (i, parent) -> parent < i }, "a parent comes before its children")
    }

    @Test
    fun jointsSayWhichBonesStretchAndHowTheyTurn() {
        assertEquals(listOf("back-leg-l/ankle", "back-leg-r/ankle", "front-leg-l/ankle", "front-leg-r/ankle", "torso/withers"), rig.bones.filter { it.stretch }.map { it.name.toString() }.sorted())
        val knee = checkNotNull(rig.bone(name("back-leg-l/knee")))
        val twin = checkNotNull(rig.bone(name("back-leg-r/knee")))
        assertEquals(0f, knee.limits?.min)
        assertEquals(140f, knee.limits?.max)
        assertEquals(knee.hinge.x, -twin.hinge.x, 1e-6f)
        assertEquals(knee.hinge.z, twin.hinge.z, 1e-6f, "a mirrored hinge turns the other way about the mirrored axis")
        assertEquals(knee.pivot.z, -twin.pivot.z, 1e-5f)
    }

    @Test
    fun restTransformsMatchTheGolden() {
        val text = rig.bones.joinToString("") { bone ->
            String.format(
                Locale.ROOT,
                "%s parent %s pivot %s along %s length %.4f hinge %s limits %s%s\n",
                bone.name, bone.parent ?: "-", bone.pivot.text(), bone.direction.text(), bone.length, bone.hinge.text(),
                bone.limits?.let { "${it.min}..${it.max}" } ?: "-", if (bone.stretch) " stretch" else "",
            )
        }
        GoldenFixture.check(Path.of("src/desktopTest/golden/fennec-bones.txt"), text, "rig")
    }

    private fun dev.chieppa.squash.geom.Vec3.text() = String.format(Locale.ROOT, "%.4f %.4f %.4f", x, y, z).replace("-0.0000", "0.0000")
}
