package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedPoint
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.geom.WidthProfile
import dev.chieppa.squash.geom.WidthSample
import dev.chieppa.squash.geom.dragProfile
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.screens.sheet.markProfile

private val EVEN = requireNotNull(dragProfile(listOf(WidthSample(0f, 1f), WidthSample(1f, 1f)), fromPlane = false))

/** This mark's places in [bin]. */
fun ResolvedMark.pointsIn(bin: CameraBin): List<ResolvedPoint> = versions[bin]?.points ?: points

/** This mark's width in [bin]. */
fun ResolvedMark.widthIn(bin: CameraBin): MarkWidth = versions[bin]?.width ?: width

/** A ribbon along [points] in body space at [color] under [rule], or null when they do not span two places. */
fun ribbonThrough(points: List<ResolvedPoint>, profile: WidthProfile, rule: LineRule, color: Color): RibbonDraw? =
    StrokeRibbon.along(points.map { RibbonPoint(it.position, it.normal) }, profile)?.let { RibbonDraw(it, rule, color) }

/** A ribbon of one weight along [points]. */
fun evenRibbon(points: List<ResolvedPoint>, rule: LineRule, color: Color): RibbonDraw? = ribbonThrough(points, EVEN, rule, color)

/** This mark as a ribbon in [bin], or null when it has none. */
fun ResolvedMark.ribbonIn(bin: CameraBin, rule: LineRule, color: Color): RibbonDraw? =
    ribbonThrough(pointsIn(bin), markProfile(widthIn(bin)), rule, color)
