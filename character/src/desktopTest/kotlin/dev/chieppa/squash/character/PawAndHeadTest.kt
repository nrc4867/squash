package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.MeshInvariants
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The blocks that draw what the user never does: toes and pads on the paw, skull and muzzle on the head. */
class PawAndHeadTest {
    private val paw = template("paw")
    private val head = template("head")
    private val leg = template("leg")
    private val ear = template("ear")

    private fun List<Patch>.named(name: String) = first { it.name == name }

    private fun Patch.maxOf(selector: (Vec3) -> Float) = vertices().maxOf(selector)

    private fun Patch.minOf(selector: (Vec3) -> Float) = vertices().minOf(selector)

    @Test
    fun aPawHasABodyAToeRowAndPads() {
        val names = PartBuilder.build(paw).map { it.name }
        val toes = List(4) { "toes:$it" }
        val pads = List(4) { "pad:toe:$it" }
        assertEquals(listOf("body") + toes + "pad:palm" + pads, names)
    }

    @Test
    fun thePadsSitUnderneath() {
        val patches = PartBuilder.build(paw)
        val body = patches.named("body")
        val palm = patches.named("pad:palm")
        assertTrue(palm.maxOf { it.y } < 0f, "the palm pad is on the underside")
        assertTrue(palm.minOf { it.y } < body.minOf { it.y }, "the palm pad stands proud of the body")
        repeat(4) { i ->
            val toe = patches.named("toes:$i")
            val pad = patches.named("pad:toe:$i")
            assertTrue(pad.minOf { it.y } < toe.minOf { it.y }, "toe $i's pad stands proud of the toe")
            assertTrue(pad.maxOf { it.y } < toe.maxOf { it.y }, "toe $i's pad is on its underside")
        }
    }

    @Test
    fun theToesFanAcrossTheEndAndReachPastTheBody() {
        val patches = PartBuilder.build(paw)
        val body = patches.named("body")
        val toes = (0 until 4).map { patches.named("toes:$it") }
        assertTrue(toes.all { it.maxOf { p -> p.x } > body.maxOf { p -> p.x } })
        val centres = toes.map { toe -> (toe.maxOf { it.z } + toe.minOf { it.z }) / 2f }
        assertEquals(centres.sorted().size, centres.distinct().size)
        assertEquals(centres.first(), -centres.last(), 1e-3f)
    }

    @Test
    fun theToeParametersReachTheBuild() {
        val five = PartBuilder.build(paw, mapOf("toes" to 5f))
        assertEquals(5, five.count { it.name.startsWith("toes:") })
        assertEquals(5, five.count { it.name.startsWith("pad:toe:") })
        fun reach(spread: Float) = PartBuilder.build(paw, mapOf("spread" to spread)).named("toes:0").vertices().maxOf { abs(it.z) }
        assertTrue(reach(40f) > reach(0f))
    }

    @Test
    fun aPawFromADrawingStaysOnItsDrawing() {
        val axes = PartBuilder.axes(paw)
        val dome = outline(0f to 0f, 0f to 0.4f, 0.5f to 0.5f, 0.9f to 0.2f, 1f to 0f)
        val under = outline(0f to 0.35f, 0.6f to 0.4f, 1f to 0.3f, 1f to -0.3f, 0.6f to -0.4f, 0f to -0.35f)
        val patches = PartBuilder.build(paw, outlines = mapOf(View.SIDE to dome, View.TOP to under))
        val toes = patches.filter { it.name.startsWith("toes:") }
        val height = toes.maxOf { toe -> toe.vertices().maxOf { it dot axes.up } }
        assertTrue(height <= 0.5f + 1e-3f, "toes stay inside the drawn Side outline, reached $height")
        val across = toes.maxOf { toe -> toe.vertices().maxOf { abs(it dot axes.side) } }
        assertTrue(across <= 0.42f, "toes stay near the drawn Top outline, reached $across")
        assertEquals(1f, toes.maxOf { toe -> toe.vertices().maxOf { it dot axes.along } }, 1e-2f)
        val palm = patches.named("pad:palm")
        assertTrue(palm.vertices().minOf { it dot axes.up } < patches.named("body").vertices().minOf { it dot axes.up } + 1e-2f)
    }

    @Test
    fun theHeadReadsAsSkullAndMuzzle() {
        val patches = PartBuilder.build(head)
        assertEquals(listOf("body", "muzzle"), patches.map { it.name })
        val skull = patches.named("body")
        val muzzle = patches.named("muzzle")
        assertTrue(muzzle.maxOf { it.x } > skull.maxOf { it.x }, "the muzzle reaches past the skull")
        assertTrue(skull.maxOf { it.y } - skull.minOf { it.y } > (muzzle.maxOf { it.y } - muzzle.minOf { it.y }) * 1.5f)
        assertTrue(skull.maxOf { it.z } - skull.minOf { it.z } > (muzzle.maxOf { it.z } - muzzle.minOf { it.z }) * 1.5f)
        assertTrue(muzzle.maxOf { it.y } < skull.maxOf { it.y }, "the muzzle sits below the top of the skull")
        val widest = skull.slices!!.maxBy { it.height }
        assertTrue(widest.u in 0.25f..0.5f, "the skull is fullest near its middle, at u = ${widest.u}")
        assertEquals(0f, skull.slices!!.first().height)
        assertEquals(0f, skull.slices!!.last().height)
    }

    @Test
    fun aDrawnHeadFollowsItsOutlineOutsideTheOverlap() {
        val axes = PartBuilder.axes(head)
        val side = outline(0f to 0f, 0.1f to 0.3f, 0.5f to 0.4f, 0.7f to 0.2f, 1f to 0.1f, 1f to -0.1f, 0.7f to -0.2f, 0.5f to -0.4f, 0.1f to -0.3f, 0f to 0f)
        val patches = PartBuilder.build(head, outlines = mapOf(View.SIDE to side))
        val skull = patches.named("body")
        val fullest = skull.slices!!.minBy { abs(it.u - 0.5f) }
        assertEquals(0.8f, fullest.height, 3e-2f)
        val ring = skull.ring(skull.slices!!.indexOf(fullest))
        assertEquals(0.8f, ring.maxOf { it dot axes.up } - ring.minOf { it dot axes.up }, 3e-2f)
        assertEquals(0f, patches.named("muzzle").slices!!.last().height)
    }

    @Test
    fun stanceBendsTheLegsRestAxisAndKeepsTheOpenEndOnTheSocketPlane() {
        val axes = PartBuilder.axes(leg)
        val bent = PartBuilder.build(leg, mapOf("stance" to 0.2f)).single()
        assertNotEquals(PartBuilder.build(leg).single(), bent)
        val hock = bent.slices!!.minBy { abs(it.u - 0.55f) }
        assertEquals(-0.2f, hock.frame.center dot axes.up, 1e-3f)
        for (p in bent.ring(0)) assertEquals(0f, p dot axes.along, 1e-4f)
        assertEquals(emptyList(), MeshInvariants.check(PartBuilder.mesh("leg", leg, listOf(bent)).patches.single(), setOf(0)))
    }

    @Test
    fun cupCurlsTheEarsSectionTowardItsFront() {
        val flat = PartBuilder.build(ear).single()
        val cupped = PartBuilder.build(ear, mapOf("cup" to 0.5f)).single()
        assertNotEquals(flat, cupped)
        val section = cupped.slices!![4].section
        val midline = section.pointAt(0f).y
        val edge = section.pointAt(0.25f).y
        assertTrue(edge > midline - 0.5f, "the side rises toward the front")
        assertEquals(emptyList(), MeshInvariants.check(PartBuilder.mesh("ear", ear, listOf(cupped)).patches.single(), setOf(0)))
    }

    @Test
    fun aPadHasNoSlicesAndAToeDoes() {
        val patches = PartBuilder.build(paw)
        assertNull(patches.named("pad:palm").slices)
        assertEquals(0.55f, patches.named("toes:0").slices!!.first().u, 1e-5f)
    }
}
