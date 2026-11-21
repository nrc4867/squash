package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.HairOutline
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.geom.Vec3

/**
 * [outline] in [head]'s drawn units: each view given in the sheet head's lengths ([dev.chieppa.squash.document.HairOutlineView.heads])
 * scaled about its origin by [drawnPerHead], the rest as they are. See HLD: parts, Hair locks.
 */
fun drawnOutline(character: Character, head: PlacedPart, outline: HairOutline): HairOutline {
    if (outline.views.none { it.heads }) return outline
    val scale = drawnPerHead(character, head, outline)
    val size = HeadView.sizeOf(character, head)
    return outline.copy(views = outline.views.map { view ->
        if (!view.heads) return@map view
        val scaled = view.points.map { CurvePoint(it.x * scale, it.y * scale) }
        val shift = if (view.view == OutlineView.FRONT) 0f else centring(head, HeadView.of(head, size, view.view), scaled)
        view.copy(points = scaled.map { CurvePoint(it.x + shift, it.y) }, heads = false)
    })
}

/** How far the outline's top lies, across [view], from over the head's skull: on a sheet a turned view's chin is not under the skull's middle. */
private fun centring(head: PlacedPart, view: HeadView, points: List<CurvePoint>): Float {
    val mesh = head.surface.mesh
    val skull = (0 until mesh.vertexCount).map { view.flat(head.origin + mesh.position(it)) }
    val top = skull.maxOfOrNull { it.y } ?: return 0f
    val from = top * SKULL_TOP
    val skullTop = skull.filter { it.y >= from }
    val drawnTop = points.filter { it.y >= from }
    if (skullTop.isEmpty() || drawnTop.isEmpty()) return 0f
    return (skullTop.minOf { it.x } + skullTop.maxOf { it.x }) / 2f - (drawnTop.minOf { it.x } + drawnTop.maxOf { it.x }) / 2f
}

/** How far up the head, as a share of its height over its origin, the skull and a turned view's outline are set over each other from. */
private const val SKULL_TOP = 0.6f

/**
 * How many of [head]'s drawn units one of the sheet head's lengths is: the head's eye line over its origin, seen from the front, over the
 * sheet's ([outline]'s front view's eyes) where both are there, else the head's height over its origin, its crown's.
 */
fun drawnPerHead(character: Character, head: PlacedPart, outline: HairOutline): Float {
    val view = HeadView.of(head, HeadView.sizeOf(character, head), OutlineView.FRONT)
    val eyes = outline.view(OutlineView.FRONT)?.takeIf { it.heads }?.eyes
    val built = head.id?.let { id ->
        character.paintedEyes.filter { it.anchor.part == id }.mapNotNull { head.surface.pointAt(it.anchor) }.map { view.flat(head.origin + it.position).y }
    }.orEmpty()
    if (eyes != null && built.isNotEmpty()) {
        val over = built.average().toFloat()
        if (over > 0f) return over / eyes
    }
    val box = head.box ?: return 1f
    return view.flat(Vec3(box.center.x, box.high.y, box.center.z)).y.coerceAtLeast(1e-3f)
}
