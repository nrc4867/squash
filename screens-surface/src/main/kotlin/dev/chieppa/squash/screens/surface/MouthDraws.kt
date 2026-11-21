package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.AnchoredMark
import dev.chieppa.squash.character.mouth.FlatPiece
import dev.chieppa.squash.character.mouth.MouthDentition
import dev.chieppa.squash.character.mouth.MouthMarks
import dev.chieppa.squash.character.mouth.shapeMouth
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.TongueLevel
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.cutShape
import dev.chieppa.squash.geom.surfaceAt
import dev.chieppa.squash.render.ColorLayerDraw
import dev.chieppa.squash.render.Tones
import dev.chieppa.squash.screens.sheet.colorOf

/** What a mouth adds to a view: fills over its part in draw order, and its lip lines as marks, each with its [ResolvedMark.points] where the view shows them. */
class MouthDraws(val layers: List<ColorLayerDraw>, val lips: List<ResolvedMark>)

/**
 * [group] as [bin] draws it on [surface], which sits at [origin], shaped by the same mouth shaper the built surface uses, in [style]: its
 * lip line as drawn while the upper lip holds it, else the shaped upper lip, then the lower lip, a pressed crease, the opening in the inner
 * role's color and the teeth and tongue in theirs, those flagged over drawn over the lips. Cut to the mesh in the patch's u and v.
 */
fun mouthDraws(
    group: MarkGroup,
    marks: Map<MarkId, List<ResolvedMark>>,
    expressed: Map<MarkRole, List<ResolvedMark>>?,
    surface: PartSurface,
    bin: CameraBin,
    palette: Palette,
    tolerance: Float,
    origin: Vec3 = Vec3.ZERO,
    style: MouthStyle = MouthStyle.PLAIN,
    dentition: MouthDentition = MouthDentition.NONE,
): MouthDraws {
    val shown = MarkRole.entries.associateWith { roleShapes(group, it, marks, expressed, bin) }
    val lipShapes = shown.getValue(MarkRole.LIP_LINE)
    val first = lipShapes.firstOrNull() ?: return MouthDraws(emptyList(), emptyList())
    val like = first.source.pointsIn(bin).first()
    val holder = like.anchor.part
    fun anchors(shape: ShownShape) = shape.places.map { Anchor(holder, shape.patch, it.x, it.y) }
    fun role(role: MarkRole) = group.roles.firstOrNull { it.role == role }
    fun anchored(role: MarkRole) = shown.getValue(role).map { AnchoredMark(it.source.id, anchors(it), it.source.source in role(role)?.over.orEmpty()) }
    val placed = MouthMarks(
        lip = lipShapes.map(::anchors),
        inner = shown.getValue(MarkRole.INNER).firstOrNull()?.let(::anchors),
        teeth = anchored(MarkRole.TEETH),
        tongue = anchored(MarkRole.TONGUE),
        tongueFilled = role(MarkRole.TONGUE)?.fill != null,
        lipsFilled = role(MarkRole.LIP_LINE)?.fill != null,
        tongueShown = group.tongueLevel != TongueLevel.NONE,
    )
    val drawn = lipShapes.mapNotNull { shape -> lineOn(surface, origin, like, shape.patch, shape.places)?.let { shape.source.copy(id = shape.source.id, points = it, versions = emptyMap(), width = shape.width) } }
    val mouth = shapeMouth(holder, placed, group.controls, style, surface, dentition) ?: return MouthDraws(emptyList(), drawn)
    val flat = mouth.flat
    fun line(id: String, places: List<Vec2>) = lineOn(surface, origin, like, mouth.patch, mouth.uv(places).map { Vec2(it.u, it.v) })?.let {
        first.source.copy(id = MarkId(id), points = it, versions = emptyMap(), width = first.width)
    }
    val base = group.id.value
    fun lineUv(id: String, places: List<SurfaceUv>) = lineOn(surface, origin, like, mouth.patch, places.map { Vec2(it.u, it.v) })?.let {
        first.source.copy(id = MarkId(id), points = it, versions = emptyMap(), width = first.width)
    }
    val lips = (if (flat.lipMoved) mouth.upperUv().mapIndexedNotNull { i, piece -> lineUv("$base~upper-$i", piece) } else drawn) +
        flat.lower.mapIndexedNotNull { i, piece -> line("$base~lower-$i", piece) } +
        listOfNotNull(flat.crease?.let { line("$base~crease", it) }) +
        inked(group, MarkRole.TONGUE, flat.tongue, ::line) + inked(group, MarkRole.TEETH, flat.teeth, ::line)
    val grid = surface.grid(mouth.patch) ?: return MouthDraws(emptyList(), lips)
    val layers = ArrayList<ColorLayerDraw>()
    fun fill(role: MarkRole, loop: List<Vec2>, corners: Set<Int>) {
        val color = palette.colorOf(role(role)?.fill) ?: return
        val cut = cutShape(ColorShape.Border(mouth.uv(loop), corners = corners, curved = true), grid, tolerance)
        if (!cut.isEmpty) layers += ColorLayerDraw(cut.mesh, Tones(color, color))
    }
    fun tooth(piece: FlatPiece) = fill(MarkRole.TEETH, piece.loop, piece.loop.indices.toSet())
    flat.lips.forEach { fill(MarkRole.LIP_LINE, it, emptySet()) }
    flat.opening?.let { fill(MarkRole.INNER, it, flat.openingCorners) }
    flat.tongue.filterNot { it.over }.forEach { fill(MarkRole.TONGUE, it.loop, emptySet()) }
    flat.teeth.filterNot { it.over }.forEach(::tooth)
    flat.tongue.filter { it.over }.forEach { fill(MarkRole.TONGUE, it.loop, emptySet()) }
    flat.teeth.filter { it.over }.forEach(::tooth)
    return MouthDraws(layers, lips)
}

/** [pieces] of [role] closed and inked as lines through [line] where [group] inks that role. */
private fun inked(group: MarkGroup, role: MarkRole, pieces: List<FlatPiece>, line: (String, List<Vec2>) -> ResolvedMark?): List<ResolvedMark> {
    if (group.roles.firstOrNull { it.role == role }?.ink != true) return emptyList()
    return pieces.mapIndexedNotNull { i, piece -> line("${group.id.value}~${role.name.lowercase()}-$i", piece.loop + piece.loop.take(1)) }
}

/** Places of [patchName] on [surface] at [places] in u and v, landed as anchors like [like], or null where fewer than two land. */
private fun lineOn(surface: PartSurface, origin: Vec3, like: ResolvedPoint, patchName: String, places: List<Vec2>): List<ResolvedPoint>? {
    val patch = surface.patches.firstOrNull { it.name == patchName } ?: return null
    return places.map { resolvedOn(surface, origin, like, patch, it.x, it.y) }.takeIf { it.size >= 2 }
}

/** Where the place at [u] and [v] of [patch] on [surface] lands in body space, the surface sitting at [origin], as an anchor like [like]. */
internal fun resolvedOn(surface: PartSurface, origin: Vec3, like: ResolvedPoint, patch: Patch, u: Float, v: Float): ResolvedPoint {
    val at = patch.surfaceAt(u, v)
    return ResolvedPoint(like.anchor.copy(patch = patch.name, u = u, v = v), origin + surface.shown(at.position), surface.shown(at.normal))
}
