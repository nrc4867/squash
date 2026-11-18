package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.surface.PaintLayer
import dev.chieppa.squash.geom.RenderMesh

/**
 * What a paint layer's values hold apart from its main body, over the triangles it was cut on: [islands], runs of vertices with a
 * value of zero or more that are cut off from the rest by vertices below zero, and [pinholes], runs below zero ringed by vertices at
 * zero or more. Each is where it lies on [render] and how many vertices it holds.
 */
class LayerSpecks(layer: PaintLayer, render: RenderMesh) {
    class Speck(val vertices: Int, val at: String)

    val islands: List<Speck>
    val pinholes: List<Speck>

    init {
        val parent = IntArray(layer.values.size) { it }
        fun find(x: Int): Int {
            var a = x
            while (parent[a] != a) {
                parent[a] = parent[parent[a]]
                a = parent[a]
            }
            return a
        }
        val touched = HashSet<Int>()
        for (tri in layer.candidates) for (k in 0 until 3) {
            val a = layer.indices[tri * 3 + k]
            val b = layer.indices[tri * 3 + (k + 1) % 3]
            touched += a
            touched += b
            if ((layer.values[a] >= 0f) == (layer.values[b] >= 0f)) parent[find(a)] = find(b)
        }
        val members = HashMap<Int, MutableList<Int>>()
        for (v in touched) members.getOrPut(find(v)) { ArrayList() } += v
        val all = members.values.map { vertices ->
            val sum = DoubleArray(3)
            for (v in vertices) {
                val p = render.position(v)
                sum[0] += p.x.toDouble()
                sum[1] += p.y.toDouble()
                sum[2] += p.z.toDouble()
            }
            Triple(layer.values[vertices.first()] >= 0f, vertices.size, sum.map { "%.2f".format(it / vertices.size) }.toString())
        }
        islands = all.filter { it.first }.map { Speck(it.second, it.third) }
        pinholes = all.filter { !it.first }.map { Speck(it.second, it.third) }
    }
}
