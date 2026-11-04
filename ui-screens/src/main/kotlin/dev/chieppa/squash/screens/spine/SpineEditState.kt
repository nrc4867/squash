package dev.chieppa.squash.screens.spine

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.ReadSpan
import dev.chieppa.squash.geom.SpineLine
import dev.chieppa.squash.geom.SpineSample
import dev.chieppa.squash.geom.SpineShape
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.WidthProfile
import dev.chieppa.squash.geom.WidthSample
import dev.chieppa.squash.geom.dragProfile
import dev.chieppa.squash.geom.pressureProfile
import dev.chieppa.squash.geom.readSpans
import dev.chieppa.squash.geom.readStretches
import dev.chieppa.squash.geom.spineLine
import dev.chieppa.squash.geom.spineShape
import dev.chieppa.squash.geom.unreadStretches
import kotlin.math.max
import kotlin.math.roundToInt

/** A pen at full pressure draws this half-width, in screen pixels. */
private const val FULL_PRESSURE_HALF_PIXELS = 41.5f

/** A spine before any drag has this full width, as a share of its length. */
private const val DEFAULT_WIDTH_OF_LENGTH = 0.3f

/** Where a spine's width handle starts, along the stroke. */
private const val DEFAULT_HANDLE_AT = 0.5f

/** The narrowest half-width a drag leaves, in screen pixels. */
private const val MIN_HALF_PIXELS = 1f

/** A drag along the spine keeps one width per this many places. */
private const val DRAG_PLACES = 32

/** How far inside the crossings an outline may lie and still count as read, in screen pixels. */
private const val READ_TOLERANCE_PIXELS = 2f

/** How far apart the planes of the shading stand, in screen pixels. */
internal const val SHADE_STRIP_PIXELS = 4f

/** What a spine will write and how the build will take it, in the planes of Side and Top. */
class SpinePreview(
    val shape: SpineShape,
    val read: List<List<Vec2>>,
    val unread: List<List<Vec2>>,
    val sideSpans: List<ReadSpan>,
    val topSpans: List<ReadSpan>,
)

private class SpineSetup(val part: PartId, val ends: StrokeEnds, val pixel: Float)

private class WidthGrip(val half: Float, val distance: Float)

/**
 * A centre stroke and its width, between the press that starts it and the release that writes it. Never saved or undone; the
 * document only ever receives the outlines. [smoothing] is read live, so the drawer's Smooth reshapes a stroke in hand.
 */
@Stable
class SpineEditState(private val smoothing: () -> Float) {
    private var setup by mutableStateOf<SpineSetup?>(null)
    private val samples = mutableStateListOf<SpineSample>()
    private val drags = mutableStateListOf<WidthSample>()
    private var grip: WidthGrip? = null

    /** Whether the stroke is still growing under the pointer. */
    var stroking by mutableStateOf(false)
        private set

    /** Where along the stroke the width handle stands. */
    var handleAt by mutableStateOf(DEFAULT_HANDLE_AT)
        private set

    /** Whether the handle's dragged end lies to the spine's left. */
    var handleLeft by mutableStateOf(false)
        private set

    val part: PartId? get() = setup?.part
    val ends: StrokeEnds? get() = setup?.ends

    /** A screen pixel's size in the plane the stroke was drawn in. */
    val pixel: Float get() = setup?.pixel ?: 1f

    /** Whether a stroke is in hand, growing or waiting for its width. */
    val active: Boolean get() = setup != null

    /** Whether pressure set this stroke's width, so that nothing is left to drag. */
    val byPen: Boolean get() = samples.any { it.pressure != null }

    private val line: SpineLine? by derivedStateOf {
        setup?.let { spineLine(samples.toList(), it.ends, it.pixel, smoothing()) }
    }

    private val width: WidthProfile? by derivedStateOf {
        val spine = line ?: return@derivedStateOf null
        val fromPlane = ends is StrokeEnds.CutAtPlane
        val pressures = spine.pressureWidths(samples.toList(), FULL_PRESSURE_HALF_PIXELS * pixel)
        when {
            pressures.isNotEmpty() -> pressureProfile(pressures, fromPlane)
            drags.isNotEmpty() -> dragProfile(drags.toList(), fromPlane)
            else -> dragProfile(listOf(WidthSample(DEFAULT_HANDLE_AT, spine.length * DEFAULT_WIDTH_OF_LENGTH / 2f)), fromPlane)
        }
    }

    /** What release would write and how the build would read it, or null while the stroke makes nothing. */
    val preview: SpinePreview? by derivedStateOf {
        val spine = line ?: return@derivedStateOf null
        val profile = width ?: return@derivedStateOf null
        val shape = spineShape(spine, profile, ends ?: return@derivedStateOf null) ?: return@derivedStateOf null
        val tolerance = READ_TOLERANCE_PIXELS * pixel
        val strip = SHADE_STRIP_PIXELS * pixel
        SpinePreview(
            shape,
            readStretches(shape.side, tolerance),
            unreadStretches(shape.side, tolerance),
            readSpans(shape.side, strip),
            readSpans(shape.top, strip),
        )
    }

    /** The full width at the handle, or at a pen's tip, in screen pixels; null with no stroke. */
    val widthPixels: Float?
        get() {
            val profile = width ?: return null
            return 2f * profile.at(if (byPen) 1f else handleAt) / pixel
        }

    /** Starts a stroke for [part], dropping any in hand. */
    fun begin(part: PartId, ends: StrokeEnds, pixel: Float) {
        clear()
        setup = SpineSetup(part, ends, pixel)
        stroking = true
    }

    fun add(sample: SpineSample) {
        if (samples.lastOrNull()?.point != sample.point) samples += sample
    }

    /** The stroke has let go; it waits for its width unless a pen gave it one. */
    fun endStroke() {
        stroking = false
    }

    /** Takes hold of the width at [at] if that is on the shape or within [reach] of it. */
    fun pressWidth(at: Vec2, reach: Float): Boolean {
        val spine = line ?: return false
        val profile = width ?: return false
        if (stroking || byPen) return false
        val station = spine.nearest(at)
        val half = profile.at(station.at)
        if (station.distance > half + reach) return false
        grip = WidthGrip(half, station.distance)
        return true
    }

    /** Moves the held width to the pointer at [to]: the place on the spine nearest it takes the width, by how far the pointer has come out from the spine. */
    fun dragWidth(to: Vec2) {
        val spine = line ?: return
        val held = grip ?: return
        val station = spine.nearest(to)
        put(station.at, max(held.half + station.distance - held.distance, MIN_HALF_PIXELS * pixel))
        handleLeft = station.left
    }

    /** Sets the full width at the handle to [pixels] screen pixels. */
    fun setWidthPixels(pixels: Float) {
        if (active && !stroking && !byPen) put(handleAt, max(pixels / 2f, MIN_HALF_PIXELS) * pixel)
    }

    private fun put(at: Float, half: Float) {
        val place = (at * DRAG_PLACES).roundToInt()
        drags.removeAll { (it.at * DRAG_PLACES).roundToInt() == place }
        drags += WidthSample(at, half)
        handleAt = at
    }

    fun clear() {
        setup = null
        samples.clear()
        drags.clear()
        grip = null
        stroking = false
        handleAt = DEFAULT_HANDLE_AT
        handleLeft = false
    }
}
