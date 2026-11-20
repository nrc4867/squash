package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.character.viewkeys.ViewKeyShapes
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetPose
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.render.Shading
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.FrameSnapshots
import dev.chieppa.squash.screens.smoke.PROBE_GROUND
import dev.chieppa.squash.screens.smoke.PROBE_LOOK
import dev.chieppa.squash.screens.smoke.keepImage
import dev.chieppa.squash.screens.smoke.probeAzimuth
import dev.chieppa.squash.screens.smoke.shownState
import dev.chieppa.squash.screens.surface.CharacterState
import dev.chieppa.squash.screens.surface.FACE_BINS
import dev.chieppa.squash.screens.surface.characterView
import dev.chieppa.squash.screens.surface.framedCamera
import dev.chieppa.squash.screens.surface.headAngle
import dev.chieppa.squash.screens.surface.keyedFor
import java.io.File
import kotlin.test.assertNotNull

/** The orbit's camera angles round the head from its front toward its left, in degrees. */
private val ORBIT = listOf(0f, 22.5f, 45f, 67.5f, 90f, 112.5f, 135f, 157.5f, 180f)

/** How far the head turns in the posed row, in degrees of the neck's lean, under a camera held at the front. */
private val HEAD_TURNS = listOf(-60f, -40f, -20f, 0f, 20f, 40f, 60f)

private const val TILE = 360
private const val MARGIN = 0.12f
private const val NECK = "neck"

/**
 * A head's view keys judged by eye: the head orbited from its front round its left to its back, the row without keys over the row with
 * them, kept as `<name>-orbit`; and the head turned on its neck under a camera held at the front, kept as `<name>-head-turn`.
 */
class ViewKeysLook(private val work: TestWork, private val errors: MutableList<String>, private val images: File) {
    fun keep(session: CharacterSession, name: String, head: PartId, framed: Set<PartId>) {
        work.settle(session)
        val shapes = shapesOf(session)
        val state = session.shownState()
        keepRows("$name-orbit", listOf({ _ -> state }, state.keyedRow(shapes)), ORBIT.map(::probeAzimuth), framed, head)
        val pose = session.document.value.character.pose
        val turned = HEAD_TURNS.map { degrees ->
            session.send(SetPose(Pose(listOf(BonePose(head, NECK, lean = degrees)))))
            work.settle(session)
            session.shownState()
        }
        session.send(SetPose(pose))
        work.settle(session)
        keepTurns("$name-head-turn", turned, shapes, framed, head)
    }

    private fun shapesOf(session: CharacterSession): ViewKeyShapes {
        val rest = assertNotNull(session.assembly.output.value)
        val surface = session.characterSurface.output.value?.surface
        return ViewKeyShapes.of(rest, surface, session.document.value.character.viewKeys)
    }

    /** Each of [rows], the state a view shows by its direction, from every one of [toViewers] by column. */
    private fun keepRows(name: String, rows: List<(Vec3) -> CharacterState>, toViewers: List<Vec3>, framed: Set<PartId>, head: PartId) {
        val views = rows.flatMapIndexed { row, stateFor ->
            toViewers.mapIndexed { column, toViewer -> view(stateFor(toViewer), toViewer, column, row, framed, head) }
        }
        keep(name, views, toViewers.size, rows.size)
    }

    private fun keepTurns(name: String, turned: List<CharacterState>, shapes: ViewKeyShapes, framed: Set<PartId>, head: PartId) {
        val toViewer = probeAzimuth(0f)
        val views = listOf(false, true).flatMapIndexed { row, keyed ->
            turned.mapIndexed { column, state ->
                val shown = if (keyed) state.keyedFor(shapes) { toViewer } else state
                view(shown, toViewer, column, row, framed, head)
            }
        }
        keep(name, views, turned.size, 2)
    }

    private fun view(state: CharacterState, toViewer: Vec3, column: Int, row: Int, framed: Set<PartId>, head: PartId): SceneView {
        val parts = state.character.parts.filter { it.id == head || (it.id in framed && (it.role ?: it.template.role) != Role.HAIR) }
        val camera = framedCamera(parts, toViewer, TILE, TILE, MARGIN)
        val rect = PixelRect(column * TILE, row * TILE, TILE, TILE)
        return characterView(rect, camera.camera, state, PROBE_LOOK, ObjectShading(Shading.TWO_TONE), camera.pixelsPerUnit) { id ->
            val part = state.character.part(id)
            if (part == null || id != head) FACE_BINS.binFor(90f) else FACE_BINS.binFor(headAngle(part, toViewer))
        }
    }

    private fun keep(name: String, views: List<SceneView>, columns: Int, rows: Int) {
        val image = FrameSnapshots.render(SceneFrame(PROBE_GROUND, views), TILE * columns, TILE * rows, errors)
        keepImage(image.pixels, image.width, name, images)
    }
}

/** A row of the orbit: this state keyed by [shapes] for each view. */
private fun CharacterState.keyedRow(shapes: ViewKeyShapes): (Vec3) -> CharacterState = { toViewer -> keyedFor(shapes) { toViewer } }
