package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.copies.DEGREES_TO_RADIANS
import dev.chieppa.squash.character.copies.turnedAbout
import dev.chieppa.squash.character.hair.shellGroupId
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** [vector] turned [degrees] round [frame]'s up. */
internal fun turnedUp(vector: Vec3, frame: HeadFrame, degrees: Float): Vec3 =
    if (degrees == 0f) vector else vector.turnedAbout(frame.up, degrees * DEGREES_TO_RADIANS)

/**
 * [part]'s mesh's change at one direction in its own space, every vertex taking its run's or piece's map, then turned with its head's
 * face where the face reaches its root or, on the head, the piece's middle, half of it for an ear, a horn or hair; null where none moves it.
 * A short-hair shell lying on the head moves as the head does, each vertex turned with the face as far as the face reaches it.
 */
/** What sits on a head without being part of its face, ears, horns and hair, and how much of the face's turn it takes wherever it sits, so it stays clear of the turned face. */
private val OFF_FACE = setOf(Role.EAR, Role.HORN, Role.HAIR)
private const val OFF_FACE_SHARE = 0.5f

internal fun partDelta(part: PlacedPart, chains: KeyChains, head: PartId): FloatArray? {
    if (chains.still) return null
    val id = requireNotNull(part.id)
    val mesh = part.surface.mesh
    val frame = chains.frames.getValue(head)
    val degrees = chains.faceDegrees[head] ?: 0f
    val follow = chains.follow(head)
    val delta = FloatArray(mesh.vertexCount * DELTA_FLOATS)
    var first = 0
    val ranges = part.surface.patches.map { patch -> (first until first + patch.nu * patch.nv).also { first += patch.nu * patch.nv } to patch }
    val shell = part.locks?.group == shellGroupId(head)
    val offFace = (part.role ?: part.template.role) in OFF_FACE && !shell
    val rootTurn = if (id == head) 0f else if (offFace) degrees * OFF_FACE_SHARE else degrees * follow.mask(part.origin, frame)
    val pieceTurns = ranges.associate { (_, patch) ->
        patch.name to if (id == head && !isRun(patch)) degrees * follow.mask(part.origin + part.surface.shown(patch.middle()), frame) else rootTurn
    }
    val whole = if (shell) chains.of(head, null) else chains.of(id, null)
    var any = false
    for (v in 0 until mesh.vertexCount) {
        val patch = ranges.firstOrNull { v in it.first }?.second
        val map = if (shell) whole else patch?.let { chains.of(id, it.name) } ?: whole
        val at = v * RenderMesh.FLOATS_PER_VERTEX
        val point = part.origin + part.surface.shown(mesh.vertices.vec(at))
        val turn = if (shell) degrees * follow.mask(point, frame) else patch?.let { pieceTurns[it.name] } ?: rootTurn
        if (map == KeyAffine.IDENTITY && turn == 0f) continue
        any = true
        val moved = chains.follow(head).turned(map(point), frame, turn)
        delta.put(v * DELTA_FLOATS, part.surface.shown(moved - point))
        for (offset in listOf(RenderMesh.NORMAL_OFFSET_FLOATS, RenderMesh.PROXY_OFFSET_FLOATS)) {
            val normal = mesh.vertices.vec(at + offset)
            val turned = part.surface.shown(turnedUp(map.turn * part.surface.shown(normal), frame, turn))
            delta.put(v * DELTA_FLOATS + offset, turned - normal)
        }
    }
    return if (any) delta else null
}

