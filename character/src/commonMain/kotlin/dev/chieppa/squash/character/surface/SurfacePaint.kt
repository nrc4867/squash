package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.character.build.HeldShapes
import dev.chieppa.squash.character.build.LandedShape
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.LevelCut
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.UvShapeDistance
import dev.chieppa.squash.geom.surface.HELD

/** The rank a part's own base slot paints at, under every shape. */
private const val RANK_BASE = -1

/**
 * What is painted on the one surface, in the order its layers overdraw, and the ink [marks] each part draws on it, their places
 * resolved on the field in body space; then its painted [eyes], each in the look a view sees it in (see [seenFrom]).
 */
class SurfacePaint(val layers: List<PaintLayer>, val marks: Map<PartId, List<ResolvedMark>> = emptyMap(), val eyes: List<EyeVersions> = emptyList()) {
    /** These layers, then [over]'s, and both's marks and eyes. */
    operator fun plus(over: SurfacePaint): SurfacePaint = SurfacePaint(layers + over.layers, merged(marks, over.marks), eyes + over.eyes)

    /**
     * This paint's layers kept to where [shown], one value a vertex of [surface]'s drawn mesh, is at least zero, as the skin its garments
     * leave showing, laid on [render], the same vertices moved or posed; its marks and eyes as they are. Itself where [shown] does not fit
     * the layers.
     */
    fun within(shown: FloatArray, surface: CharacterSurface, render: RenderMesh = surface.render): SurfacePaint {
        if (shown.size != surface.render.vertexCount || layers.any { it.values.size != shown.size }) return this
        return SurfacePaint(layers.mapNotNull { it.within(shown, surface, render) }, marks, eyes)
    }

    /** This paint laid on [render], the same surface moved. */
    fun on(render: RenderMesh): SurfacePaint = SurfacePaint(layers.map { it.on(render) }, marks, eyes.map { it.on(render) })

    /** This paint laid on [render], the same surface posed, its marks and eyes' facings moved by [carry]. */
    fun posed(render: RenderMesh, carry: (ResolvedPoint) -> ResolvedPoint): SurfacePaint =
        SurfacePaint(layers.map { it.on(render) }, carried(marks, carry), eyes.map { it.posed(render, carry) })

    /** This paint as a view sees it, [toViewer] giving the way to the viewer from any point: each eye in its look for that view. */
    fun seenFrom(toViewer: (Vec3) -> Vec3): SurfacePaint =
        eyes.fold(SurfacePaint(layers, marks)) { all, eye -> all + eye.seenFrom(toViewer(eye.middle)) }

    companion object {
        val NONE = SurfacePaint(emptyList())

        /** Both's marks, part by part, [first]'s first. */
        fun merged(first: Map<PartId, List<ResolvedMark>>, then: Map<PartId, List<ResolvedMark>>): Map<PartId, List<ResolvedMark>> =
            (first.keys + then.keys).associateWith { first[it].orEmpty() + then[it].orEmpty() }

        /** [marks] with each point moved by [carry]. */
        fun carried(marks: Map<PartId, List<ResolvedMark>>, carry: (ResolvedPoint) -> ResolvedPoint): Map<PartId, List<ResolvedMark>> =
            marks.mapValues { (_, held) -> held.map { it.copy(points = it.points.map(carry)) } }

        /**
         * The paint of [held], each holder's color shapes, on [surface]: each part whose base slot differs from the root part's painted
         * over its own section, then every shape on a run of the surface in layer order. A shape is read in its run's own (u, v) as a
         * signed distance in body units and kept to the run's own section, so it ends where a neighbour takes the surface over. Each holder's
         * ink lines on the surface are resolved again on the field. Each shape's layer is one piece of [runAll]'s work, and the ink lines
         * another. With [memo], a shape's values at a vertex the surface's mesh keeps from the mesh the memo last painted are taken from
         * there rather than read again. With [onOwnRuns], an ink place that lands off its own run's surface, hidden under another part
         * there, is left out (see [FieldAnchors.resolveOnOwn]).
         */
        suspend fun of(
            surface: CharacterSurface,
            held: Map<PartId, HeldShapes>,
            runAll: RunAll = IN_TURN,
            memo: PaintMemo? = null,
            onOwnRuns: Boolean = false,
        ): SurfacePaint {
            val chart = surface.chart
            val runs = surface.field.runs
            val root = (runs.firstOrNull { it.part.socket == null } ?: runs.first()).partId
            val rootSlot = held[root]?.baseSlot
            val bases = surface.parts.mapNotNull { part ->
                val slot = held[part]?.baseSlot ?: return@mapNotNull null
                if (slot == rootSlot) return@mapNotNull null
                runs.filter { it.partId == part }.map { LandedShape(RANK_BASE, slot, part, it.patch.name, ColorShape.WholePatch) }
            }.flatten()
            val shapes = (bases + held.values.flatMap { it.shapes }).filter { it.part in surface.parts }.sortedBy { it.rank }
            var marks: Map<PartId, List<ResolvedMark>> = emptyMap()
            val layers = arrayOfNulls<PaintLayer>(shapes.size)
            val laid = arrayOfNulls<LaidShape>(shapes.size)
            val earlier = memo?.earlier(chart)
            val runsLaid = shapes.map { chart.members(it.part, it.patch) }.filter { it.isNotEmpty() }.distinct()
            val sectionLays = runsLaid.map { members -> sectionOf(chart, members, earlier) }
            val lays = shapes.map { layOf(chart, it, earlier) }
            runAll((sectionLays + lays.filterNotNull()).flatMap { it.pieces } + listOf({ marks = marksOf(surface, held, onOwnRuns) }))
            val sections = runsLaid.indices.associate { i -> runsLaid[i] to lazy { sectionLays[i].finish(chart.trianglesOf(runsLaid[i])) } }
            val margin = SectionMargin(chart)
            val shown = sections.mapValues { (members, raw) -> lazy { margin.widened(raw.value, members, chart.trianglesOf(members)) } }
            runAll(
                shapes.indices.map { i ->
                    {
                        val made = lays[i]?.let { layerOf(chart, shapes[i], it, shown, earlier) }
                        layers[i] = made?.first
                        laid[i] = made?.second
                    }
                },
            )
            memo?.keep(surface.mesh, shapes.indices.mapNotNull { i -> laid[i]?.let { shapes[i] to it } }.toMap(), sections.mapValues { it.value.value })
            return SurfacePaint(layers.filterNotNull(), marks)
        }

        /** Each holder's ink lines on [surface], resolved again on its field; a line broken where [onOwnRuns] leaves places out is as many lines. */
        private fun marksOf(surface: CharacterSurface, held: Map<PartId, HeldShapes>, onOwnRuns: Boolean): Map<PartId, List<ResolvedMark>> {
            val anchors = FieldAnchors(surface.field)
            return held.filterKeys { it in surface.parts }.mapValues { (_, shapes) ->
                shapes.lines.flatMap { mark ->
                    if (mark.kind == MarkKind.FLOATING) {
                        val points = floatingFrom(mark.points, anchors)
                        return@flatMap if (points == null || points.size < 2) emptyList() else listOf(mark.copy(points = points, versions = emptyMap()))
                    }
                    val runs = ArrayList<List<ResolvedPoint>>()
                    var run = ArrayList<ResolvedPoint>()
                    for (place in mark.points) {
                        val point = if (onOwnRuns) anchors.resolveOnOwn(place.anchor) else anchors.resolve(place.anchor)
                        if (point != null) {
                            run += point
                        } else if (onOwnRuns) {
                            if (run.size >= 2) runs += run
                            run = ArrayList()
                        }
                    }
                    if (run.size >= 2) runs += run
                    runs.map { mark.copy(points = it, versions = emptyMap()) }
                }
            }
        }

        /**
         * A floating mark's [points] with its root resolved again on the field and the rest of its flight carried with it, or null where the
         * field holds no root. Every point of a floating mark names its root's anchor, so they are not resolved one by one.
         */
        private fun floatingFrom(points: List<ResolvedPoint>, anchors: FieldAnchors): List<ResolvedPoint>? {
            val first = points.firstOrNull() ?: return null
            val root = anchors.resolve(first.anchor) ?: return null
            val moved = root.position - first.position
            return points.mapIndexed { i, point -> if (i == 0) point.copy(position = root.position) else point.copy(position = point.position + moved) }
        }

        /** How far inside its runs' own section of [chart]'s surface each vertex [members] hold lies, those [earlier] keeps taken from there. */
        private fun sectionOf(chart: SurfaceChart, members: List<Int>, earlier: PaintMemo.Earlier?): ChartLay {
            val before = earlier?.sections?.get(members)
            val previousOf = earlier?.carry?.previousOf
            return ChartLay(chart, members) { v, _, member ->
                val old = if (before != null && previousOf != null) previousOf[v] else -1
                if (old >= 0) before!![old] else chart.section(v, member)
            }
        }

        /** How [shape]'s distance is laid on [chart], in its run's own (u, v), those [earlier] keeps taken from there; null where no run holds it. */
        private fun layOf(chart: SurfaceChart, shape: LandedShape, earlier: PaintMemo.Earlier?): ChartLay? {
            val members = chart.members(shape.part, shape.patch)
            if (members.isEmpty()) return null
            val mesh = chart.surface.mesh
            val distance = UvShapeDistance(shape.shape, chart.surface.field.runs[members.first()].patch.closedV)
            val before = earlier?.shapes?.get(shape)?.distances
            val previousOf = earlier?.carry?.previousOf
            return ChartLay(chart, members) { v, k, member ->
                val old = if (before != null && previousOf != null) previousOf[v] else -1
                if (old >= 0) {
                    before!![old]
                } else {
                    val u = mesh.heldU[v * HELD + k]
                    val scales = chart.scales(member)
                    distance.at(u, mesh.heldV[v * HELD + k], scales.along(u), scales.around(u))
                }
            }
        }

        /**
         * [shape]'s layer on [chart]'s surface from its distance [laid], eased off its run's own surface and kept to the run's own section
         * of its runs' [sections], each found by the first shape on it, and what it laid; what [earlier] keeps is taken from there.
         */
        private fun layerOf(
            chart: SurfaceChart,
            shape: LandedShape,
            laid: ChartLay,
            sections: Map<List<Int>, Lazy<FloatArray>>,
            earlier: PaintMemo.Earlier?,
        ): Pair<PaintLayer?, LaidShape> {
            val members = chart.members(shape.part, shape.patch)
            val before = earlier?.shapes?.get(shape)
            val triangles = chart.trianglesOf(members)
            val distances = laid.finish(triangles)
            val section = sections.getValue(members).value
            val eased = chart.easing.eased(distances, members, triangles, earlier?.carry.takeIf { before != null }, before?.values)
            val values = if (eased === distances) distances.copyOf() else eased
            for (i in values.indices) if (section[i] < values[i]) values[i] = section[i]
            val split = if (shape.shape == ColorShape.WholePatch) {
                null
            } else {
                val closedV = chart.surface.field.runs[members.first()].patch.closedV
                PaintSplits.of(chart, members, triangles, UvShapeDistance(shape.shape, closedV), closedV, eased, section, values)
            }
            return PaintLayer.of(shape.slot, chart.surface, values, triangles, split = split, source = shape.source) to LaidShape(distances, values)
        }
    }
}
