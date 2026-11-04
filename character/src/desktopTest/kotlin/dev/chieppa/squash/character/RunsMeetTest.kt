package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Slice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val SAME = 1e-3f

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

    @Test
    fun theMuzzleNeverStandsProudOfTheSkullWhereTheyOverlap() {
        val skull = run("body").dropLast(1)
        val muzzle = run("muzzle")
        val overlap = muzzle.filter { it.u <= skull.last().u }
        assertTrue(overlap.size > 3, "rings in the overlap: ${overlap.size}")
        for (ring in overlap) {
            assertTrue(ring.height <= skull.heightAt(ring.u) + SAME, "at ${ring.u}: muzzle ${ring.height}, skull ${skull.heightAt(ring.u)}")
        }
    }

    @Test
    fun theSkullHandsOverToTheMuzzleAtTheDrawingsSize() {
        val skull = run("body").dropLast(1)
        val muzzle = run("muzzle")
        val at = skull.last().u
        assertTrue(abs(skull.last().height - muzzle.heightAt(at)) < SAME, "${skull.last().height} and ${muzzle.heightAt(at)}")
    }
}
