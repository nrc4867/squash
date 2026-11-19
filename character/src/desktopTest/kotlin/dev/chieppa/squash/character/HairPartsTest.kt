package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.HEM_AREA
import dev.chieppa.squash.character.block.PARTING_AREA
import dev.chieppa.squash.character.block.SHINE_AREA
import dev.chieppa.squash.character.block.TIP_AREA
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.rootFade
import dev.chieppa.squash.character.build.sweeps
import dev.chieppa.squash.character.copies.drooped
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The hair templates: a volume with its hem, parting and shine, clumps with their tips, tufts, presets and the copies' droop. See HLD: parts, Hair. */
class HairPartsTest {
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private fun latest(id: String): PartTemplate = assertNotNull(library.latest(TemplateId(id)))

    @Test
    fun `the hair templates lint clean and their presets set only their own parameters`() {
        listOf("hair-volume", "hair-clump", "hair-tuft").forEach { id ->
            val template = latest(id)
            assertEquals(emptyList(), TemplateLint.lint(template), id)
            assertTrue(template.presets.isNotEmpty(), id)
        }
        val wrong = latest("hair-clump").let { it.copy(presets = it.presets + ("odd" to mapOf("length" to 1f))) }
        assertEquals(listOf("$.presets.odd.length"), TemplateLint.lint(wrong).map { it.path })
    }

    @Test
    fun `clumps and tufts sweep in the field, so they blend into the hair and a curve gives their path`() {
        listOf("hair-clump", "hair-tuft", "hair-volume").forEach { assertEquals(SurfaceMethod.FIELD, latest(it).surface, it) }
        assertTrue(latest("hair-clump").sweeps)
        assertTrue(latest("hair-tuft").sweeps)
    }

    @Test
    fun `a volume inks strands flowing over it and a clump along its top`() {
        val volume = latest("hair-volume")
        val flowing = assertNotNull(volume.blocks.firstNotNullOfOrNull { it.clumpInk(volume.defaults) })
        assertTrue(flowing.flowing && flowing.strands > 0)
        val clump = latest("hair-clump")
        assertEquals(false, assertNotNull(clump.blocks.firstNotNullOfOrNull { it.clumpInk(clump.defaults) }).flowing)
    }

    @Test
    fun `a clump's tip area covers its last tip shade and its last ring closes`() {
        val clump = latest("hair-clump")
        val tip = PartBuilder.areas(clump, mapOf("tipShade" to 0.25f)).single { it.name == TIP_AREA }
        assertEquals(0.75f, tip.u.start, 1e-5f)
        val patch = PartBuilder.build(clump, emptyMap()).first()
        val last = patch.slices!!.last()
        assertEquals(0f, last.height, 1e-5f)
    }

    @Test
    fun `a clump's ink fades in from its root`() {
        val patch = PartBuilder.build(latest("hair-clump"), emptyMap()).first()
        val fade = rootFade(patch, 0.3f)
        assertEquals(0f, fade[0], 1e-5f)
        assertEquals(1f, fade.last(), 1e-5f)
    }

    @Test
    fun `a volume's presets change its shape and it paints a hem, a parting and a band of shine`() {
        val volume = latest("hair-volume")
        val short = PartBuilder.build(volume, volume.presets.getValue("short")).first()
        val long = PartBuilder.build(volume, volume.presets.getValue("long")).first()
        fun lowest(p: dev.chieppa.squash.geom.Patch) = (0 until p.nu * p.nv).minOf { p.point(it).y }
        assertTrue(lowest(long) < lowest(short) - 0.1f)
        val areas = PartBuilder.areas(volume, emptyMap()).associateBy { it.name }
        assertEquals(setOf(HEM_AREA, PARTING_AREA, SHINE_AREA), areas.keys)
        assertEquals(2, areas.getValue(SHINE_AREA).loops?.size)
        assertEquals(2, areas.getValue(HEM_AREA).loops?.size)
        assertNotNull(volume.blocks.firstNotNullOfOrNull { it.hairShine(volume.defaults, volume.axis.length) })
    }

    @Test
    fun `droop turns a copy from the normal toward down along the surface`() {
        val side = drooped(Vec3(0f, 0f, -1f), Vec3(1f, 0f, 0f), Math.toRadians(90.0).toFloat())
        assertEquals(-1f, side.y, 1e-4f)
        val top = drooped(Vec3(0f, 1f, 0f), Vec3(1f, 0f, 0f), Math.toRadians(90.0).toFloat())
        assertEquals(1f, top.x, 1e-4f)
    }

    @Test
    fun `a lobed clump at any parameters builds watertight`() {
        val clump = latest("hair-clump")
        val params = mapOf(
            "size" to 1.356519f, "width" to 0.91840464f, "thickness" to 0.76282585f, "taper" to 0.51495534f, "swell" to 0.23710667f, "tipRound" to 0.72126555f,
            "tipReach" to 0.3672296f, "points" to 2f, "notch" to 0.20899041f, "twist" to 315.32922f, "wave" to 0.81726134f, "waves" to 0.5238588f, "flare" to 0.9795382f,
            "cup" to -0.44330722f,
        )
        val patch = PartBuilder.build(clump, params).first()
        val mesh = PartBuilder.mesh("hair-clump", clump, listOf(patch))
        mesh.patches.forEach { assertEquals(emptyList(), dev.chieppa.squash.geom.MeshInvariants.check(it, PartBuilder.openRings(clump, it.patch))) }
    }
}
