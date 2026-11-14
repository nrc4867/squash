package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.geom.Vec3
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max

private val BACKGROUND = Color(0xF2, 0xEE, 0xE6)
private val EDGE = Color(0x40, 0x30, 0x28)
private val LIGHT = Vec3(0.7f, 0.5f, -0.5f).normalized()
private const val EDGE_WIDTH = 0.6f
private const val FILL_SHARE = 0.9f
private const val GOLDEN = 0.618034f

/**
 * Draws a character's one surface as its triangles, nearest last, each filled by how it faces the light and edged, seen orthographically
 * from each of [views] (toward the viewer) as tiles [tile] pixels a side framed on a ball of [radius] round [center], or on the whole
 * surface without one.
 */
fun keepWireframe(surface: CharacterSurface, views: List<Vec3>, tile: Int, file: File, center: Vec3? = null, radius: Float? = null, owners: Boolean = false) {
    val image = BufferedImage(tile * views.size, tile, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = BACKGROUND
    g.fillRect(0, 0, image.width, image.height)
    val mesh = surface.mesh
    val count = mesh.vertexCount
    views.forEachIndexed { i, toViewer ->
        val back = toViewer.normalized()
        val right = (Vec3.Y cross back).normalized()
        val up = back cross right
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        val zs = FloatArray(count)
        for (v in 0 until count) {
            val p = mesh.position(v)
            xs[v] = p dot right
            ys[v] = p dot up
            zs[v] = p dot back
        }
        val middleX = center?.let { it dot right } ?: ((xs.min() + xs.max()) / 2f)
        val middleY = center?.let { it dot up } ?: ((ys.min() + ys.max()) / 2f)
        val half = radius ?: (max(xs.max() - xs.min(), ys.max() - ys.min()) / 2f)
        val scale = tile * FILL_SHARE / (2f * half)
        fun sx(v: Int) = i * tile + tile / 2f + (xs[v] - middleX) * scale
        fun sy(v: Int) = tile / 2f - (ys[v] - middleY) * scale
        val t = mesh.triangles
        val order = (0 until mesh.triangleCount).filter { tri ->
            val a = mesh.position(t[tri * 3])
            val n = (mesh.position(t[tri * 3 + 1]) - a) cross (mesh.position(t[tri * 3 + 2]) - a)
            (n dot back) > 0f
        }.sortedBy { tri -> zs[t[tri * 3]] + zs[t[tri * 3 + 1]] + zs[t[tri * 3 + 2]] }
        g.clipRect(i * tile, 0, tile, tile)
        g.stroke = BasicStroke(EDGE_WIDTH)
        for (tri in order) {
            val a = t[tri * 3]
            val b = t[tri * 3 + 1]
            val c = t[tri * 3 + 2]
            val path = Path2D.Float().apply {
                moveTo(sx(a), sy(a))
                lineTo(sx(b), sy(b))
                lineTo(sx(c), sy(c))
                closePath()
            }
            val n = ((mesh.position(b) - mesh.position(a)) cross (mesh.position(c) - mesh.position(a))).normalized()
            val lit = (0.55f + 0.45f * (n dot LIGHT)).coerceIn(0.2f, 1f)
            g.color = if (owners) ownerColor(surface, a, lit) else Color(0.93f * lit, 0.82f * lit, 0.62f * lit)
            g.fill(path)
            g.color = EDGE
            g.draw(path)
        }
        g.clip = null
    }
    g.dispose()
    file.parentFile.mkdirs()
    ImageIO.write(image, "png", file)
}

/** A colour for the part that owns vertex [v], lighter by [lit], striped by the owner's share across a join. */
private fun ownerColor(surface: CharacterSurface, v: Int, lit: Float): Color {
    val mesh = surface.mesh
    val winner = mesh.winners[v]
    val hue = if (winner >= 0) (winner * GOLDEN) % 1f else 0f
    val share = mesh.shares[v]
    return Color.getHSBColor(hue, 0.25f + 0.6f * share, lit)
}
