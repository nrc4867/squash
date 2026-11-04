package dev.chieppa.squash.screens.draw

import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.SnapGuide
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.AppState
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneLine
import dev.chieppa.squash.screens.sheet.lineAcross
import dev.chieppa.squash.screens.sheet.outlineSamples
import dev.chieppa.squash.screens.sheet.paneGuides
import dev.chieppa.squash.screens.sheet.sideExtents

/**
 * The lines in [camera]'s pane a stroke there can catch, from the other views: what Side reaches, drawn or as [extents]
 * show it, which is what the pane's guides show, then the ends of a drawn Top and Front along the directions they share
 * with this view.
 */
fun strokeSnapLines(camera: LockedCamera, outlines: Map<View, OutlineCurve>, extents: PartExtents?): List<PaneLine> = buildList {
    if (camera.view != View.SIDE && extents != null) addAll(paneGuides(camera, sideExtents(outlines[View.SIDE], extents, camera.drawingScale, camera.lengthFactor)).lines)
    val top = outlines[View.TOP]?.takeIf { camera.view != View.TOP }?.let(::outlineSamples)
    val front = outlines[View.FRONT]?.takeIf { camera.view != View.FRONT }?.let(::outlineSamples)
    val axes = camera.axes
    fun across(direction: Vec3, reaches: List<Float>, scale: Float = camera.drawingScale) =
        listOf(reaches.min(), reaches.max()).forEach { add(camera.lineAcross(direction, it * scale)) }
    if (top != null) {
        if (camera.view == View.SIDE) across(axes.along, top.map { it.x }, camera.alongScale)
        if (camera.view == View.FRONT) across(axes.side, top.map { 0f - it.y })
    }
    if (front != null) {
        if (camera.view == View.SIDE) across(axes.up, front.map { it.y })
        if (camera.view == View.TOP) across(axes.side, front.map { it.x })
    }
}

/** The lines in [camera]'s pane a stroke on the selected part can catch, from its other outlines and its built extents. */
internal fun AppState.strokeSnapLines(camera: LockedCamera): List<PaneLine> {
    val others = View.entries.filter { it != camera.view }.mapNotNull { other -> editedOutline(other)?.let { other to it } }.toMap()
    val extents = selectedPart.value?.let { id -> session.value?.assembly?.output?.value?.part(id)?.surface?.extents }
    return strokeSnapLines(camera, others, extents)
}

/** [line] in [camera]'s plane, where a stroke's samples lie. */
internal fun LockedCamera.guideInPlane(line: PaneLine): SnapGuide {
    val from = toPlane(line.start)
    val to = toPlane(line.end)
    return SnapGuide(Vec2(from.x, from.y), Vec2(to.x, to.y))
}
