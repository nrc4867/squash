package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SAME = 5e-3f

/** The least cosine between the two runs' normals at one place of the handover. */
private const val NORMALS_AGREE = 0.995

/** A drawn part built of several runs reads as one surface. See HLD: data-model, cap block. */
class RunsMeetTest {
    private val head = template("head")
    private val side = outline(0f to 0f, 0.1f to 0.2f, 0.5f to 0.25f, 0.9f to 0.2f, 1f to 0f, 0.9f to -0.2f, 0.5f to -0.25f, 0.1f to -0.2f, 0f to 0f)

    private fun run(name: String) = PartBuilder.build(head, outlines = mapOf(View.SIDE to side)).first { it.name == name }.slices!!

    private fun List<Slice>.heightAt(u: Float): Float {
        val i = indexOfFirst { it.u >= u }
        if (i <= 0) return this[i.coerceAtLeast(0)].height
        val a = this[i - 1]
        val b = this[i]
        if (b.u == a.u) return b.height
        return a.height + (b.height - a.height) * (u - a.u) / (b.u - a.u)
    }

    /** The middle of the overlap, where the surface passes from the skull to the muzzle. */
    private val handover = (0.6f + 0.75f) / 2f

    @Test
    fun theMuzzleStaysInsideTheSkullUpToTheHandover() {
        val skull = run("body")
        val muzzle = run("muzzle")
        val before = muzzle.filter { it.u < handover }
        assertTrue(before.size > 3, "rings before the handover: ${before.size}")
        for (ring in before) {
            assertTrue(ring.height < skull.heightAt(ring.u), "at ${ring.u}: muzzle ${ring.height}, skull ${skull.heightAt(ring.u)}")
        }
    }

    @Test
    fun theSkullStaysInsideTheMuzzlePastTheHandover() {
        val skull = run("body")
        val muzzle = run("muzzle")
        val past = skull.drop(1).zip(skull).filter { (_, before) -> before.u >= handover }.map { it.first }
        assertTrue(past.size > 3, "rings past the handover: ${past.size}")
        for (ring in past) {
            assertTrue(ring.height < muzzle.heightAt(ring.u), "at ${ring.u}: skull ${ring.height}, muzzle ${muzzle.heightAt(ring.u)}")
        }
    }

    @Test
    fun bothRunsStandAtTheDrawingsSizeAtTheHandover() {
        val skull = run("body")
        val muzzle = run("muzzle")
        assertTrue(abs(skull.heightAt(handover) - muzzle.heightAt(handover)) < SAME, "${skull.heightAt(handover)} and ${muzzle.heightAt(handover)}")
    }

    @Test
    fun theRunsShadeAsOneSurfaceAcrossTheOverlap() {
        val patches = PartBuilder.build(head, outlines = mapOf(View.SIDE to side))
        val skull = patches.first { it.name == "body" }
        val muzzle = patches.first { it.name == "muzzle" }
        val skullNormals = skull.proxyNormals!!
        val muzzleNormals = muzzle.proxyNormals!!
        for (segment in 0 until skull.nv) {
            val skullRing = skull.ringUs.indexOfLast { it <= handover }
            val muzzleRing = muzzle.ringUs.indexOfLast { it <= handover }
            val a = skull.index(skullRing, segment) * Patch.COMPONENTS
            val b = muzzle.index(muzzleRing, segment) * Patch.COMPONENTS
            val dot = (0..2).sumOf { (skullNormals[a + it] * muzzleNormals[b + it]).toDouble() }
            assertTrue(dot > NORMALS_AGREE, "around at segment $segment the runs' normals part: $dot")
        }
    }
}
