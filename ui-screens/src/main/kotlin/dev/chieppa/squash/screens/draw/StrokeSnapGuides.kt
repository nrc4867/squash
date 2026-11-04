package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.SnapGuide
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneLine
import dev.chieppa.squash.screens.sheet.lineAcross
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.squash.screens.sheet.paneGuides
import dev.chieppa.squash.screens.sheet.sideExtents

/** How far from a guide a stroke's end or widest point is caught, in screen pixels. */
const val SNAP_GUIDE_PIXELS = 8f

/**
 * The lines in [camera]'s pane a stroke there can catch, from the other views: what Side reaches, drawn or as [extents]
 * show it, which is what the pane's guides show, then the ends of a drawn Top and Front along the directions they share
 * with this view.
 */
fun strokeSnapLines(camera: LockedCamera, outlines: Map<View, OutlineCurve>, extents: PartExtents?): List<PaneLine> = buildList {
    if (camera.view != View.SIDE && extents != null) addAll(paneGuides(camera, sideExtents(outlines[View.SIDE], extents, camera.drawingScale)).lines)
    val top = outlines[View.TOP]?.takeIf { camera.view != View.TOP }?.let(::outlineSamples)
    val front = outlines[View.FRONT]?.takeIf { camera.view != View.FRONT }?.let(::outlineSamples)
    val axes = camera.axes
    fun across(direction: Vec3, reaches: List<Float>) =
        listOf(reaches.min(), reaches.max()).forEach { add(camera.lineAcross(direction, it * camera.drawingScale)) }
    if (top != null) {
        if (camera.view == View.SIDE) across(axes.along, top.map { it.x })
        if (camera.view == View.FRONT) across(axes.side, top.map { 0f - it.y })
    }
    if (front != null) {
        if (camera.view == View.SIDE) across(axes.up, front.map { it.y })
        if (camera.view == View.TOP) across(axes.side, front.map { it.x })
    }
}

/** [line] in [camera]'s plane, where a stroke's samples lie. */
internal fun LockedCamera.guideInPlane(line: PaneLine): SnapGuide {
    val from = toPlane(line.start)
    val to = toPlane(line.end)
    return SnapGuide(Vec2(from.x, from.y), Vec2(to.x, to.y))
}
