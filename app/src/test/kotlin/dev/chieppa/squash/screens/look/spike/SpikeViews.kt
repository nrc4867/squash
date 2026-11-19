package dev.chieppa.squash.screens.look.spike

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.cellsOfRuns
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.dress.bodyChart
import dev.chieppa.squash.screens.smoke.PROBE_GROUND
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.probeAzimuth
import dev.chieppa.squash.screens.smoke.renderSnapshot
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import dev.chieppa.stretch.gpu.GpuOptions
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.tan

const val TILE = 520
const val TILE_HEIGHT = 640
private const val MARGIN = 0.05f

/** Projects body points into one tile of a row of views. */
class TileCamera(private val left: Int, private val eye: Vec3, private val back: Vec3, private val fovY: Float) {
    private val right = (Vec3.Y cross back).normalized()
    private val up = back cross right
    val toViewer: Vec3 get() = back

    fun project(p: Vec3): Pair<Float, Float>? {
        val rel = p - eye
        val depth = -(rel dot back)
        if (depth <= 0f) return null
        val f = TILE_HEIGHT / 2f / tan(fovY / 2f)
        return (left + TILE / 2f + (rel dot right) / depth * f) to (TILE_HEIGHT / 2f - (rel dot up) / depth * f)
    }
}

/** The session's character from each of [azimuths], [TILE] by [TILE_HEIGHT] a view, framed on [framing] or all of it, with each tile's camera. */
fun renderRow(work: TestWork, session: CharacterSession, azimuths: List<Float>, errors: MutableList<String>, framing: Set<dev.chieppa.squash.document.PartId>? = null): Pair<BufferedImage, List<TileCamera>> {
    work.settle(session)
    val state = session.shownState()
    val parts = state.character.parts.filter { it.id != null && (framing == null || it.id in framing) }
    val cameras = ArrayList<TileCamera>()
    val views = azimuths.mapIndexed { i, degrees ->
        val toViewer = probeAzimuth(degrees)
        val rect = PixelRect(i * TILE, 0, TILE, TILE_HEIGHT)
        val camera = framedCamera(parts, toViewer, TILE, TILE_HEIGHT, MARGIN)
        cameras += TileCamera(i * TILE, Vec3(camera.camera.eye.x, camera.camera.eye.y, camera.camera.eye.z), toViewer.normalized(), camera.camera.fovY)
        characterView(rect, camera.camera, state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), camera.pixelsPerUnit) { id ->
            val head = state.character.part(id)
            if (head == null) FACE_BINS.binFor(90f) else FACE_BINS.binFor(headAngle(head, toViewer))
        }
    }
    val width = TILE * azimuths.size
    val image = renderSnapshot(SceneFrame(PROBE_GROUND, views), width, TILE_HEIGHT, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
    val out = BufferedImage(width, TILE_HEIGHT, BufferedImage.TYPE_INT_ARGB)
    out.setRGB(0, 0, width, TILE_HEIGHT, image.pixels, 0, width)
    return out to cameras
}

private val AREA_COLORS = listOf(Color(220, 20, 140), Color(20, 140, 230), Color(240, 140, 0))

/** Each area's cells of [garment], by part, as held arrays. */
fun areaCells(garment: Garment): List<Map<dev.chieppa.squash.document.PartId, BooleanArray>> =
    garment.areas.map { area -> area.cells.mapValues { (_, runs) -> cellsOfRuns(runs) } }

private class Placed(val position: Vec3, val normal: Vec3)

private fun placedAt(body: AssembledCharacter, part: dev.chieppa.squash.document.PartId, patch: String, u: Float, v: Float): Placed? {
    val placed = body.part(part) ?: return null
    val point = placed.surface.pointAt(Anchor(part, patch, u, v)) ?: return null
    return Placed(placed.origin + point.position, point.normal)
}

/** The sides each painted part is drawn on: itself and its pair's other side. */
private fun sidesOf(character: Character, part: dev.chieppa.squash.document.PartId): List<dev.chieppa.squash.document.PartId> {
    val instance = character.part(part) ?: return listOf(part)
    return listOfNotNull(part, character.twinOf(instance)?.id)
}

/** Dots on each painted cell's middle facing each camera, coloured by area, over [image]. */
fun drawCells(image: BufferedImage, cameras: List<TileCamera>, character: Character, body: AssembledCharacter, areas: List<Map<dev.chieppa.squash.document.PartId, BooleanArray>>) {
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val chart = bodyChart(character, body)
    areas.forEachIndexed { k, cells ->
        val color = AREA_COLORS[k % AREA_COLORS.size]
        g.color = Color(color.red, color.green, color.blue, 200)
        for ((part, held) in cells) {
            val patch = chart.part(part)?.patch ?: continue
            for (side in sidesOf(character, part)) for (cell in 0 until CHART_CELLS) {
                if (!held[cell]) continue
                val p = placedAt(body, side, patch, (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS, (cell % CHART_COLUMNS + 0.5f) / CHART_COLUMNS) ?: continue
                for (camera in cameras) {
                    if ((p.normal dot camera.toViewer) < 0.05f) continue
                    val (x, y) = camera.project(p.position) ?: continue
                    g.fillOval((x - 2.5f).toInt(), (y - 2.5f).toInt(), 5, 5)
                }
            }
        }
    }
    g.dispose()
}

/** The painted cells' edges, each area's, as lines where they face each camera, over [image]. */
fun drawEdges(image: BufferedImage, cameras: List<TileCamera>, character: Character, body: AssembledCharacter, areas: List<Map<dev.chieppa.squash.document.PartId, BooleanArray>>) {
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.stroke = BasicStroke(1.6f)
    val chart = bodyChart(character, body)
    areas.forEachIndexed { k, cells ->
        g.color = AREA_COLORS[k % AREA_COLORS.size]
        for ((part, held) in cells) {
            val patch = chart.part(part)?.patch ?: continue
            for (side in sidesOf(character, part)) for (cell in 0 until CHART_CELLS) {
                if (!held[cell]) continue
                val r = cell / CHART_COLUMNS
                val c = cell % CHART_COLUMNS
                val sides = ArrayList<FloatArray>()
                if (r + 1 < CHART_ROWS && !held[cell + CHART_COLUMNS]) sides += floatArrayOf((r + 1f) / CHART_ROWS, c.toFloat() / CHART_COLUMNS, (r + 1f) / CHART_ROWS, (c + 1f) / CHART_COLUMNS)
                if (r > 0 && !held[cell - CHART_COLUMNS]) sides += floatArrayOf(r.toFloat() / CHART_ROWS, c.toFloat() / CHART_COLUMNS, r.toFloat() / CHART_ROWS, (c + 1f) / CHART_COLUMNS)
                if (!held[r * CHART_COLUMNS + (c + 1) % CHART_COLUMNS]) sides += floatArrayOf(r.toFloat() / CHART_ROWS, (c + 1f) / CHART_COLUMNS, (r + 1f) / CHART_ROWS, (c + 1f) / CHART_COLUMNS)
                if (!held[r * CHART_COLUMNS + (c + CHART_COLUMNS - 1) % CHART_COLUMNS]) sides += floatArrayOf(r.toFloat() / CHART_ROWS, c.toFloat() / CHART_COLUMNS, (r + 1f) / CHART_ROWS, c.toFloat() / CHART_COLUMNS)
                for (s in sides) {
                    val a = placedAt(body, side, patch, s[0], s[1]) ?: continue
                    val b = placedAt(body, side, patch, s[2], s[3]) ?: continue
                    for (camera in cameras) {
                        if ((a.normal dot camera.toViewer) < 0.05f) continue
                        val pa = camera.project(a.position) ?: continue
                        val pb = camera.project(b.position) ?: continue
                        g.drawLine(pa.first.toInt(), pa.second.toInt(), pb.first.toInt(), pb.second.toInt())
                    }
                }
            }
        }
    }
    g.dispose()
}
