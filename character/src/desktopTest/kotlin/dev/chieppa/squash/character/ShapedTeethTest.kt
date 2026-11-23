package dev.chieppa.squash.character

import dev.chieppa.squash.character.model.TeethPreset
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.layTeeth
import dev.chieppa.squash.character.mouth.TeethSeat
import dev.chieppa.squash.character.mouth.shaped.LipPath
import dev.chieppa.squash.character.mouth.shaped.TONGUE_JOINTS
import dev.chieppa.squash.character.mouth.shaped.TONGUE_PATCH
import dev.chieppa.squash.character.mouth.shaped.isLowerTooth
import dev.chieppa.squash.character.mouth.shaped.isTooth
import dev.chieppa.squash.character.mouth.shaped.mouthPartChains
import dev.chieppa.squash.character.mouth.shaped.shapedTeeth
import dev.chieppa.squash.character.mouth.shaped.shapedTongue
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.surface.JAW_JOINT
import dev.chieppa.squash.document.Jaw
import dev.chieppa.squash.document.JawName
import dev.chieppa.squash.document.JawTeeth
import dev.chieppa.squash.document.MouthTeeth
import dev.chieppa.squash.document.TeethLevel
import dev.chieppa.squash.document.TongueTip
import dev.chieppa.squash.document.ToothRow
import dev.chieppa.squash.document.ToothSlot
import dev.chieppa.squash.document.ToothTip
import dev.chieppa.squash.document.ToothType
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JawCut
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shaped teeth and tongue: the level a jaw built open starts at, the parts laid on a lip line, which jaw they ride, and their joints. */
class ShapedTeethTest {
    private val types = mapOf("front" to ToothType(width = 0.08f, length = 0.1f), "fang" to ToothType(width = 0.06f, length = 0.2f, tip = ToothTip.POINT, curve = 0.2f))
    private val row = ToothRow(listOf(ToothSlot("front"), ToothSlot("fang", over = true)))
    private val preset = TeethPreset(id = "probe", name = "probe", types = types, upper = Jaw(TeethLevel.BLOCKS, listOf(row)), lower = Jaw(TeethLevel.BAR, listOf(row)))

    /** A snout along +x from a hinge at the origin turning about -z so up is +y, its lip line flat at y = 0 from x = 1 back to x = 0.4 on both sides. */
    private val cut = JawCut(Vec3.ZERO, Vec3.Z * -1f, Vec3.X, floatArrayOf(0.4f, 1f), floatArrayOf(0f, 0f), 0.4f, (-PI / 2).toFloat(), (30 * PI / 180).toFloat())
    private val lips = listOf(1f, 0.8f, 0.6f, 0.4f).flatMap { x -> listOf(Vec3(x, 0f, 0.3f * x), Vec3(x, 0f, -0.3f * x)) }
    private val path = requireNotNull(LipPath.of(cut, lips))

    @Test
    fun aJawBuiltOpenStartsItsBlocksAtShapedAndKeepsWhatTheMouthSets() {
        val open = MouthDentition.of(preset, MouthTeeth(), builtOpen = true)
        assertEquals(TeethLevel.SHAPED, open.upper.level)
        assertEquals(TeethLevel.BAR, open.lower.level)
        val kept = MouthDentition.of(preset, MouthTeeth(upper = JawTeeth(level = TeethLevel.BLOCKS)), builtOpen = true)
        assertEquals(TeethLevel.BLOCKS, kept.upper.level)
    }

    @Test
    fun shapedOnAMouthNotBuiltOpenPaintsAsBlocks() {
        val shut = MouthDentition.of(preset, MouthTeeth(upper = JawTeeth(level = TeethLevel.SHAPED)))
        assertEquals(TeethLevel.BLOCKS, shut.upper.level)
    }

    @Test
    fun aShapedJawPaintsNothing() {
        val seat = object : TeethSeat {
            override val middle = 0f
            override val width = 2f
            override fun reach(sign: Int) = 1f
            override fun upperLine(x: Float) = 0f
            override fun lowerLine(x: Float) = 0f
        }
        val dentition = MouthDentition(types, Jaw(TeethLevel.SHAPED, listOf(row)), Jaw())
        assertTrue(layTeeth(dentition, seat).isEmpty())
    }

    @Test
    fun theLipPathRunsFromItsMiddleBackAlongEachSide() {
        val (middle, _) = assertNotNull(path.at(1, 0f))
        assertEquals(0f, middle.z, 1e-5f)
        assertEquals(1f, middle.x, 1e-5f)
        val (_, back) = assertNotNull(path.at(1, path.reach(1) * 0.8f))
        assertTrue(back.x < 0f, "past the front the line runs back toward the corner")
        assertEquals(path.reach(1) + path.reach(-1), path.width, 1e-4f)
    }

    @Test
    fun eachSlotIsAToothOnBothSidesTheUpperHangingAndTheLowerBuiltOpenWithItsJaw() {
        val dentition = MouthDentition(types, Jaw(TeethLevel.SHAPED, listOf(row)), Jaw(TeethLevel.SHAPED, listOf(row)))
        val teeth = shapedTeeth(dentition, path)
        assertEquals(8, teeth.size)
        assertTrue(teeth.all { isTooth(it.name) && it.slices == null })
        val upper = teeth.filterNot { isLowerTooth(it.name) }
        val lower = teeth.filter { isLowerTooth(it.name) }
        assertEquals(4, lower.size)
        fun tipOf(patch: dev.chieppa.squash.geom.Patch) = patch.point(patch.index(patch.nu - 1, 0))
        upper.forEach { assertTrue(tipOf(it).y < 0f, "an upper tooth hangs below the palate") }
        val opened = Vec3(cos(-cut.built), sin(-cut.built), 0f)
        lower.forEach { tooth -> assertTrue((tipOf(tooth) dot Vec3(-opened.y, opened.x, 0f)) > 0f, "a lower tooth stands up off the floor turned open") }
    }

    @Test
    fun aPointedToothClosesToAPoint() {
        val dentition = MouthDentition(types, Jaw(TeethLevel.SHAPED, listOf(ToothRow(listOf(ToothSlot("fang"))))), Jaw())
        val fang = shapedTeeth(dentition, path).first()
        assertTrue(fang.isPole(fang.nu - 1))
    }

    @Test
    fun aTongueLiesAlongTheFloorAndRidesTheJawOnJointsOfItsOwn() {
        val tongue = shapedTongue(path, TongueTip.FORK)
        assertEquals(TONGUE_PATCH, tongue.name)
        val chain = listOf(JointSpec("neck", 0f, null, Hinge.X, null, false), JointSpec(JAW_JOINT, 0.6f, 0.5f, Hinge.X, Limits(0f, 30f), false))
        val chains = mouthPartChains(chain, setOf(TONGUE_PATCH, "teeth:lower-0-0-l", "teeth:upper-0-0-l"))
        val tongueChain = chains.single { it.patch == TONGUE_PATCH }
        assertEquals(TONGUE_JOINTS, tongueChain.joints.map { it.name })
        assertEquals(JAW_JOINT, tongueChain.from)
        assertEquals(listOf("teeth:lower-0-0-l"), chains.filter { it.joints.isEmpty() }.map { it.patch })
        assertNull(mouthPartChains(chain.take(1), setOf(TONGUE_PATCH)).firstOrNull())
    }
}
