package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.HeldPaint
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.surface.SurfaceSection
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw

/** How many parts' sections a [PaneSurface] keeps, at rest and posed. */
private const val KEPT_SECTIONS = 4

/**
 * A character's one surface as the panes show it: [rest], its surface with its paint and dress, and [posed], the same in the shown pose,
 * null while the pose leaves it at rest. Each part's own section is found once a surface and kept while it is asked for.
 */
class PaneSurface(val rest: PosedSurface, val posed: PosedSurface?) {
    /** The parts the surface holds; each draws only its pieces that are not runs. */
    val parts: Set<PartId> get() = rest.surface.parts

    /** What a pane shows: [posed] when it shows the pose and there is one, [rest] otherwise. */
    fun shown(posedPane: Boolean): PosedSurface = if (posedPane) posed ?: rest else rest

    private val sections = object : LinkedHashMap<Pair<PartId, CharacterSurface>, SurfaceSection>(KEPT_SECTIONS, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<PartId, CharacterSurface>, SurfaceSection>) = size > KEPT_SECTIONS
    }

    /** [part]'s own section of the surface a pane shows. */
    fun section(part: PartId, posedPane: Boolean): SurfaceSection {
        val surface = shown(posedPane).surface
        return synchronized(sections) { sections.getOrPut(part to surface) { SurfaceSection(surface, part) } }
    }
}

/**
 * The pane surface each settled piece of a one surface makes, one after another: the surface with its paint and dress where both are
 * laid on it, else the last surface that had both, since the graph settles the surface of a build before its paint and dress.
 */
class LastPaneSurface {
    private var kept: PaneSurface? = null

    /** What the panes show of [rest] with [paint] and [dress], and [posed], the surface in the shown pose. */
    fun next(rest: CharacterSurface, paint: HeldPaint?, dress: SurfaceDress?, posed: PosedSurface?): PaneSurface {
        val laid = PosedSurface.laid(rest, paint, dress)
        if (laid != null) return PaneSurface(laid, posed?.takeIf { it.surface !== rest }).also { kept = it }
        val held = kept?.rest ?: PosedSurface(rest, null)
        return PaneSurface(held, posed?.takeIf { it.surface !== held.surface })
    }
}

/**
 * What [color]'s one surface draws in a pane, the pose's when [posedPane], seen with the camera at [toCamera], each eye
 * along [toViewer] from its middle: its garments, then the
 * surface with its paint, or with [only] that part's own section of it; each at [opacity] in [layer], outlined [outline] wide. Nothing
 * for a character without one.
 */
fun SheetLook.surfaceDraws(
    color: PaneColor?,
    toCamera: Vec3,
    posedPane: Boolean,
    outline: Float = 0f,
    opacity: Float = 1f,
    layer: DepthLayer = DepthLayer.SCENE,
    only: PartId? = null,
    toViewer: (Vec3) -> Vec3 = { toCamera },
): List<MeshDraw> {
    val held = color?.surface ?: return emptyList()
    val shown = held.shown(posedPane)
    val fallback = part.copy(ink = color.palette.colorOf(INK_SLOT) ?: part.ink)
    val section = only?.let { held.section(it, posedPane) }
    val garments = if (only == null) shown.dress?.let { garmentDraws(it, color.palette, fallback, color.shading, toCamera) }.orEmpty() else emptyList()
    val body = surfaceDraw(shown.surface, shown.paint, shown.dress?.skin, color.body, color.palette, fallback, color.shading, toCamera, section, toViewer)
    return (garments + body).map { it.copy(outlinePixels = outline, opacity = opacity, layer = layer) }
}

/** Whether [part] draws as its section of [color]'s one surface rather than its own mesh. */
fun PaneColor?.holds(part: SheetPart): Boolean = part.id != null && this?.surface?.parts?.contains(part.id) == true
