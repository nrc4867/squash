package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.geom.Vec2

/** One closed mark of a mouth role in the mouth's flat frame, and whether it draws [over] the lips. */
class FlatMark(val id: MarkId, val places: List<Vec2>, val over: Boolean = false)

/**
 * A mouth as drawn, in its flat frame (x across the face, y down it, in body units): the [lip] line's places in order across, the
 * [inner] role's closed shape, the fully open mouth, if one is drawn, and the [teeth] and [tongue] marks. A tongue is drawn where it
 * sits with the mouth fully open; teeth that reach the lip line sit as at rest, those below it as fully open. With [lipsFilled] each lip
 * shows a filled band [lipUpper] and [lipLower] of the mouth's width thick at its middle.
 */
class MouthDrawing(
    val lip: List<Vec2>,
    val inner: List<Vec2>? = null,
    val teeth: List<FlatMark> = emptyList(),
    val tongue: List<FlatMark> = emptyList(),
    val tongueFilled: Boolean = false,
    val lipsFilled: Boolean = false,
    val lipUpper: Float = 0f,
    val lipLower: Float = 0f,
)

/**
 * A shaped closed piece of a role, its [loop] in the flat frame, whether it draws over the lips rather than inside the opening, and whether
 * it breaks the upper lip's ink too, as a fang over a shut mouth does, or only the lower's, as upper teeth resting on a tucked lower lip do.
 */
class FlatPiece(val id: MarkId, val loop: List<Vec2>, val over: Boolean, val breaksUpper: Boolean = over)

/**
 * A mouth as its controls shape it, in its flat frame: the inked pieces of its [upper] and [lower] lips, the [crease] under pressed lips,
 * the [opening] between the lips while they part, sharp at [openingCorners], its [teeth] and [tongue] as they show, and its [lips]' filled
 * bands, the upper then the lower. [lipMoved] says the upper lip left its drawn line.
 */
class FlatMouth(
    val upper: List<List<Vec2>>,
    val lower: List<List<Vec2>>,
    val crease: List<Vec2>?,
    val opening: List<Vec2>?,
    val teeth: List<FlatPiece>,
    val tongue: List<FlatPiece>,
    val lipMoved: Boolean,
    val openingCorners: Set<Int> = emptySet(),
    val lips: List<List<Vec2>> = emptyList(),
) {
    /** Whether the mouth shows nothing beyond its drawn lip line. */
    val restsAsDrawn: Boolean get() = !lipMoved && lower.isEmpty() && crease == null && opening == null && teeth.isEmpty() && tongue.isEmpty() && lips.isEmpty()
}
