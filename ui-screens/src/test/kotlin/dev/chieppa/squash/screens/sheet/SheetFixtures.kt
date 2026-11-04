package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.surfaceOf
import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect

/** A part of [template] with nothing drawn, sitting at [origin]. */
internal fun sheetPartOf(template: PartTemplate, origin: Vec3 = Vec3.ZERO, side: Side? = null, id: String = "p") =
    SheetPart(PlacedPart(PartId(id), template, side, surfaceOf(template, PartData(), side == Side.RIGHT), origin))

/** The locked panes' cameras on [part] in [rects], as the panes make them. */
internal fun camerasOn(part: SheetPart, fit: SheetFit, rects: List<PixelRect>): Map<View, LockedCamera> =
    SheetPane.entries.mapNotNull { pane ->
        val body = pane.body ?: return@mapNotNull null
        val view = part.axes.framing.getValue(body)
        view to LockedCamera(view, part.axes, fit, rects[pane.ordinal].width, rects[pane.ordinal].height, part.origin)
    }.toMap()
