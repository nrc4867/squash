package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoinAnglesTest {
    /** An axis-aligned box from [lo] to [hi]. */
    private class Box(override val low: Vec3, override val high: Vec3) : Solid {
        override fun distance(p: Vec3): Float {
            val c = (low + high) * 0.5f
            val h = (high - low) * 0.5f
            val qx = abs(p.x - c.x) - h.x
            val qy = abs(p.y - c.y) - h.y
            val qz = abs(p.z - c.z) - h.z
            val outside = sqrt(max(qx, 0f) * max(qx, 0f) + max(qy, 0f) * max(qy, 0f) + max(qz, 0f) * max(qz, 0f))
            return outside + min(max(qx, max(qy, qz)), 0f)
        }
    }

    /** An upright round post of [radius] about the y axis from [bottom] to [top], flat at both ends. */
    private class Post(val radius: Float, val bottom: Float, val top: Float) : Solid {
        override val low = Vec3(-radius, bottom, -radius)
        override val high = Vec3(radius, top, radius)

        override fun distance(p: Vec3): Float {
            val dr = sqrt(p.x * p.x + p.z * p.z) - radius
            val dy = max(bottom - p.y, p.y - top)
            return min(max(dr, dy), 0f) + sqrt(max(dr, 0f) * max(dr, 0f) + max(dy, 0f) * max(dy, 0f))
        }
    }

    private fun field(parent: Solid, child: Solid, blend: Float, angled: Boolean): JoinedField {
        val p = JoinedMember(parent, Vec3.ZERO, false, 0f)
        val bare = JoinedMember(child, Vec3.ZERO, false, blend, parent = 0, part = 1)
        val angles = if (angled) JoinAngles.of(bare, p, blend) else null
        return JoinedField(listOf(p, JoinedMember(child, Vec3.ZERO, false, blend, parent = 0, part = 1, angles = angles)))
    }

    @Test
    fun surfacesRunningAlongsideDoNotSwell() {
        val ground = Box(Vec3(-1f, -1f, -1f), Vec3(1f, 0f, 1f))
        val slab = Box(Vec3(-0.5f, -0.2f, -0.3f), Vec3(0.5f, 0f, 0.3f))
        val blend = 0.2f
        val at = Vec3(0.1f, 0.01f, 0.05f)
        assertTrue(field(ground, slab, blend, angled = false).value(at) < 0f, "a plain smooth union swells two tops lying on each other")
        assertEquals(0.01f, field(ground, slab, blend, angled = true).value(at), 1e-3f, "the gradient blend leaves the shared top where it is")
        val above = Box(Vec3(-0.5f, -0.2f, -0.3f), Vec3(0.5f, 0.02f, 0.3f))
        assertEquals(0.01f, field(ground, above, blend, angled = true).value(Vec3(0.1f, 0.03f, 0.05f)), 1e-3f, "a top just above runs alongside unswollen")
    }

    @Test
    fun aCreaseAtAnAngleFills() {
        val ground = Box(Vec3(-1f, -1f, -1f), Vec3(1f, 0f, 1f))
        val post = Post(0.1f, -0.3f, 0.6f)
        val blend = 0.15f
        val corner = Vec3(0.1f + 0.03f, 0.03f, 0f)
        val plain = field(ground, post, blend, angled = false).value(corner)
        val angled = field(ground, post, blend, angled = true).value(corner)
        assertTrue(angled < 0f, "the crease round the post's foot fills: $angled")
        assertEquals(plain, angled, 1e-3f, "a crease at a right angle takes the whole blend")
        assertEquals(1f, angleShare(0f))
        assertEquals(0f, angleShare(1f))
    }
}
