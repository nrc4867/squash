package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fewest points a hair outline's view holds. */
const val LEAST_OUTLINE_POINTS = 3

/** The most points a hair outline's view holds. */
const val MOST_OUTLINE_POINTS = 600

/**
 * Which way a hair outline's view looks at the head, by how far round from straight ahead toward the character's left its camera stands,
 * [turn] degrees: the front, a three-quarter view, the side with the camera on the character's left, and the back.
 */
@Serializable
enum class OutlineView(val turn: Float) {
    @SerialName("front") FRONT(0f),
    @SerialName("three-quarter") THREE_QUARTER(45f),
    @SerialName("side") SIDE(90f),
    @SerialName("back") BACK(180f),
}

/**
 * The hair's outline in one [view]: a closed curve of [points] round the region the hair covers as that view sees it, each point x to the
 * view's right and y up, in the head's drawn units from its origin (the frame face places use, seen from the view's camera). [tips] are
 * the points of its spikes and flicks and [valleys] the notches between them, by index into [points]; each is found from the curve's
 * shape when left out.
 */
@Serializable
@Doc("The hair's outline in one view: a closed curve round the hair's region, with its tips and valleys.")
data class HairOutlineView(
    @Doc("Which view: front, three-quarter (the camera 45 degrees round toward the character's left), side (the camera on the character's left) or back.")
    val view: OutlineView,
    @Doc("The closed curve round the region the hair covers, each point x to the view's right and y up, in the head's drawn units from its origin: the frame face places use, seen from the view's camera.")
    val points: List<CurvePoint>,
    @Doc("The points of its spikes and flicks, by index into points; found from the curve's shape when left out.")
    val tips: List<Int>? = null,
    @Doc("The notches between them, by index into points; found from the curve's shape when left out.")
    val valleys: List<Int>? = null,
) {
    init {
        require(points.size in LEAST_OUTLINE_POINTS..MOST_OUTLINE_POINTS) { "A hair outline holds $LEAST_OUTLINE_POINTS to $MOST_OUTLINE_POINTS points, not ${points.size}" }
        require(tips.orEmpty().all { it in points.indices } && valleys.orEmpty().all { it in points.indices }) { "A hair outline's tips and valleys name its points by index" }
    }
}

/**
 * The hair outline of head [on], drawn in one or more [views], the front among them. With [fit], refitting places the outer locks so that
 * seen from each view their tips land on the outline's tips and their edges follow it, and sizes the inner mass to the region it covers.
 */
@Serializable
@Doc("The hair's drawn outline on a head, per view, which its outer locks are fitted to.")
data class HairOutline(
    val on: PartId,
    val views: List<HairOutlineView>,
    @Doc("Whether the hair is fitted to the outline: the outer locks' roots, lengths, rise, sweep, fall and widths, and the inner mass's size.")
    val fit: Boolean = true,
) {
    init {
        require(views.any { it.view == OutlineView.FRONT }) { "A hair outline holds its front view" }
        require(views.map { it.view }.toSet().size == views.size) { "A hair outline holds each view once" }
    }

    /** Its view [view], or null. */
    fun view(view: OutlineView): HairOutlineView? = views.firstOrNull { it.view == view }
}

/** The hair outline on [head], or null. */
fun Character.hairOutlineOn(head: PartId): HairOutline? = hairOutlines.firstOrNull { it.on == head }
