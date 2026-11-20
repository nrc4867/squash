package dev.chieppa.squash.character.viewkeys

import dev.chieppa.squash.character.copies.DEGREES_TO_RADIANS
import dev.chieppa.squash.character.copies.scaled
import dev.chieppa.squash.character.copies.times
import dev.chieppa.squash.character.copies.turnedAbout
import dev.chieppa.squash.document.KeyMove
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3

/**
 * A head's own directions at rest, unit and square to each other, its [center], about which the whole head turns and scales, and its
 * [length], which a move's slides are shares of.
 */
data class HeadFrame(val forward: Vec3, val up: Vec3, val left: Vec3, val length: Float, val center: Vec3 = Vec3.ZERO) {
    companion object {
        /** The frame of a head facing [facing] with the body's up as near its up as it allows, [length] long, about [center]. */
        fun of(facing: Vec3, length: Float, center: Vec3 = Vec3.ZERO): HeadFrame {
            val forward = facing.normalized()
            val flat = Vec3.Y - forward * (Vec3.Y dot forward)
            val up = if (flat.length > 1e-4f) flat.normalized() else Vec3.X
            return HeadFrame(forward, up, up cross forward, length, center)
        }
    }
}

/** A key's map of body space: [linear] then a move by [offset]; [turn] is its turn alone, which normals take. */
data class KeyAffine(val linear: Mat3, val turn: Mat3, val offset: Vec3) {
    /** Where it takes [point]. */
    operator fun invoke(point: Vec3): Vec3 = linear * point + offset

    /** This map, then [after]. */
    fun then(after: KeyAffine): KeyAffine = KeyAffine(after.linear * linear, after.turn * turn, after.linear * offset + after.offset)

    companion object {
        val IDENTITY = KeyAffine(Mat3.IDENTITY, Mat3.IDENTITY, Vec3.ZERO)

        /**
         * [move] in [frame] on something rooted at [pivot] whose own length runs along [axis]: twisted about its axis, turned by yaw
         * about the head's up, pitch (front up) and roll (top toward the left), and scaled, all about [pivot], then slid.
         */
        fun of(move: KeyMove, frame: HeadFrame, pivot: Vec3, axis: Vec3): KeyAffine {
            if (move.isStill) return IDENTITY
            val turn = rotation(frame.up, move.yaw) * rotation(frame.left, -move.pitch) * rotation(frame.forward, -move.roll) * rotation(axis, move.twist)
            val linear = (turn * stretch(frame, move)).scaled(move.scale)
            val slide = (frame.forward * move.forward + frame.up * move.up + frame.left * move.left) * frame.length
            return KeyAffine(linear, turn, pivot - linear * pivot + slide)
        }
    }
}

/** Scaling by [move]'s width along [frame]'s left, height along its up and depth along its facing. */
private fun stretch(frame: HeadFrame, move: KeyMove): Mat3 {
    if (move.width == 1f && move.height == 1f && move.depth == 1f) return Mat3.IDENTITY
    fun scaled(v: Vec3) = v + frame.left * ((frame.left dot v) * (move.width - 1f)) + frame.up * ((frame.up dot v) * (move.height - 1f)) +
        frame.forward * ((frame.forward dot v) * (move.depth - 1f))
    return Mat3(scaled(Vec3.X), scaled(Vec3.Y), scaled(Vec3.Z))
}

/** The turn by [degrees] about the unit direction [axis]. */
private fun rotation(axis: Vec3, degrees: Float): Mat3 {
    if (degrees == 0f) return Mat3.IDENTITY
    val radians = degrees * DEGREES_TO_RADIANS
    return Mat3(Vec3.X.turnedAbout(axis, radians), Vec3.Y.turnedAbout(axis, radians), Vec3.Z.turnedAbout(axis, radians))
}
