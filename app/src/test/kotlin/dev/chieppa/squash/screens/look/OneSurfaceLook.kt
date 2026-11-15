package dev.chieppa.squash.screens.look

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.rig.SessionPose
import dev.chieppa.squash.screens.look.drag.moveDrag
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.smoke.WomanViews
import dev.chieppa.squash.screens.smoke.probeAzimuth
import java.io.File

/** How far round a join its spot close-up reaches, in body units. */
private const val JOIN_RADIUS = 0.16f

/** How far round a place on the outline its close-up reaches, in body units. */
private const val OUTLINE_RADIUS = 0.14f

/** How far below the forelegs' sockets their parting lies, in body units. */
private const val PARTING_DROP = 0.08f

/** How much larger a drag makes the torso. */
private const val DRAG_GROWTH = 1.02f

/** The face close-up's views and square tile. */
private val FACE_VIEWS = listOf(0f, 40f, 90f)
private const val FACE_TILE = 600

/** The full-body views, front, three-quarter and side, toward the viewer. */
private val FULL_VIEWS = listOf(0f, 40f, 90f).map(::probeAzimuth)

/**
 * The sheets a character built as one surface is judged on, each beside the same sheet of the character as its parts build it: a spot
 * close-up of each join from six sides; the outline close up at its silhouette from the front, three-quarter and side; the mesh itself;
 * and the end of a torso drag, at the coarse cell while the gesture is open and at the full one after.
 */
class OneSurfaceLook(private val work: TestWork, private val errors: MutableList<String>, private val folder: File) {
    private val spots = SpotViews(work, errors, folder)

    fun keepAll(session: CharacterSession, name: String) {
        work.settle(session)
        val body = checkNotNull(session.assembly.output.value)
        val parts = LookParts(session.document.value.character)
        fun origin(id: PartId?) = id?.let(body::part)?.origin
        val joins = buildList {
            origin(parts.head)?.let { add("neck" to it) }
            origin(parts.arm)?.let { add("shoulder" to it) }
            origin(parts.leg)?.let { add("hip" to it) }
            origin(parts.tail)?.let { add("tail-root" to it) }
            val character = session.document.value.character
            character.parts.filter { it.socket?.parent == parts.arm }.firstOrNull()?.let { origin(it.id) }?.let { add("front-ankle" to it) }
            character.parts.filter { it.socket?.parent == parts.leg }.firstOrNull()?.let { origin(it.id) }?.let { add("back-ankle" to it) }
            val fore = parts.arms.mapNotNull(::origin)
            if (fore.size == 2) add("parting" to (fore[0] + fore[1]) * 0.5f - Vec3(0f, PARTING_DROP, 0f))
        }
        for ((join, at) in joins) spots.keep(session, SpotViews.AROUND.chunked(3).map { row -> row.map { Spot(at, JOIN_RADIUS, it) } }, "$name-join-$join")
        val outline = listOfNotNull(origin(parts.head), origin(parts.arm), origin(parts.tail), origin(parts.leg))
        spots.keep(session, FULL_VIEWS.map { view -> outline.map { Spot(silhouetteNear(body.box?.center ?: Vec3.ZERO, it, view), OUTLINE_RADIUS, view) } }, "$name-outline")
        session.characterSurface.output.value?.surface?.let { surface ->
            keepWireframe(surface, FULL_VIEWS, WIRE_TILE, File(folder, "$name-wireframe.png"))
            keepWireframe(surface, FULL_VIEWS, WIRE_TILE, File(folder, "$name-wireframe-owners.png"), owners = true)
            val shoulder = origin(parts.arm)
            if (shoulder != null) keepWireframe(surface, FULL_VIEWS, WIRE_TILE, File(folder, "$name-wireframe-shoulder.png"), shoulder, JOIN_RADIUS)
        }
        session.oneSurface.dress.output.value?.dress?.garments?.forEach { worn ->
            keepWireframe(worn.surface, FULL_VIEWS, WIRE_TILE, File(folder, "$name-wireframe-${worn.cover.garment.id.value}.png"), owners = true)
        }
    }

    /**
     * The torso dragged larger: the whole figure and the shoulder while the gesture is open, at the coarse cell, and after it ends, at the
     * full one; the surface's cell printed with each.
     */
    fun keepDrag(session: CharacterSession, name: String, torso: PartId) {
        val views = WomanViews(work, errors, folder)
        val character = session.document.value.character
        val instance = checkNotNull(character.part(torso))
        val size = character.resolved(instance).params[SIZE] ?: checkNotNull(session.builtTemplate(instance).defaults[SIZE])
        val gesture = session.history.begin()
        gesture.update(SetParameter(torso, SIZE, size * DRAG_GROWTH))
        work.settle(session)
        println("$name drag: during, cell ${session.characterSurface.output.value?.surface?.mesh?.cell}")
        views.keep(session, "$name-drag-during")
        session.characterSurface.output.value?.surface?.let { keepWireframe(it, FULL_VIEWS, WIRE_TILE, File(folder, "$name-drag-during-wireframe.png")) }
        gesture.commit()
        work.settle(session)
        println("$name drag: after, cell ${session.characterSurface.output.value?.surface?.mesh?.cell}")
        views.keep(session, "$name-drag-after")
        session.characterSurface.output.value?.surface?.let { keepWireframe(it, FULL_VIEWS, WIRE_TILE, File(folder, "$name-drag-after-wireframe.png")) }
        session.history.undo()
        work.settle(session)
    }

    /**
     * A Move drag of [part]'s socket with the mirror on, as the Move tool makes it: the whole figure and the face close-up of [face] while
     * the gesture is open, at its last move, after the release, and as a session opened afresh on the released document builds it, which
     * the release must match; the character is left as it was.
     */
    fun keepRelease(session: CharacterSession, name: String, part: PartId, face: Set<PartId>?) {
        val views = WomanViews(work, errors, folder)
        val character = session.document.value.character
        val placed = checkNotNull(session.placed.output.value)
        val script = moveDrag(character, placed, session.templates.plan(character.bodyPlan), part)
        val gesture = session.history.begin()
        gesture.update(script.frames(0).last())
        fun keep(shown: CharacterSession, by: WomanViews, moment: String) {
            by.keep(shown, "$name-release-$moment")
            face?.let { by.keep(shown, "$name-release-$moment-face", it, FACE_VIEWS, FACE_TILE, FACE_TILE) }
        }
        work.settle(session)
        keep(session, views, "during")
        gesture.commit()
        work.settle(session)
        keep(session, views, "after")
        TestWork().use { afresh ->
            val opened = CharacterSession(session.document.value, session.templates, afresh.scope, afresh.dispatcher)
            SessionPose(opened)
            afresh.settle(opened)
            keep(opened, WomanViews(afresh, errors, folder), "fresh")
        }
        session.history.undo()
        work.settle(session)
    }

    /** The point of the figure's outline seen from [view] beside [place]: [place] carried out from the body's [middle] across the view. */
    private fun silhouetteNear(middle: Vec3, place: Vec3, view: Vec3): Vec3 {
        val back = view.normalized()
        val across = (place - middle).let { it - back * (it dot back) }
        return place + (if (across.length > 0f) across.normalized() else Vec3.Y) * (OUTLINE_RADIUS * 0.5f)
    }

    companion object {
        const val SIZE = "size"
        private const val WIRE_TILE = 900
    }
}
