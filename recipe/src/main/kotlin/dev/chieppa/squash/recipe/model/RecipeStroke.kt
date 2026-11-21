package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.dress.DEFAULT_BRUSH_SIZE
import dev.chieppa.squash.character.shape.DEFAULT_OPENING_REACH
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The shape a stroke lays on a part's chart. */
@Serializable
enum class RecipeStrokeShape {
    @SerialName("brush") BRUSH,
    @SerialName("line") LINE,
    @SerialName("band") BAND,
    @SerialName("box") BOX,
    @SerialName("ellipse") ELLIPSE,
    @SerialName("lasso") LASSO,
    @SerialName("opening") OPENING,
}

/** How an opening's front drops below its ring. */
@Serializable
enum class RecipeOpeningCut {
    @SerialName("round") ROUND,
    @SerialName("v") V,
}

/**
 * One Paint stroke on the part [on]'s chart of 64 rows along it (u) by 32 columns round it (v): the cells whose middles its [shape]
 * holds. A brush or line reaches the parts beyond its part too, never the parts it sits on. An [exact] stroke is kept as a vector shape too.
 */
@Serializable
@Doc(
    "One Paint stroke on a part, as Dress lays it: the cells of the part's chart (64 rows along it in u, 32 columns round it in v) whose " +
        "middles the shape holds. A cell is 1/64 of u by 1/32 of v; the cloth's edge is a smooth curve over the cells' edges, and an area " +
        "under two cells wide drops out, unless its strokes are exact. brush: every cell within size of the path through points (or face), " +
        "straight between them in u and v; one place is a dab. line: every cell within size of the straight lines in the body between its " +
        "places. A brush or line reaches the parts beyond its part (from a sleeve onto the hand), never the parts it sits on. band: rows " +
        "u[0] to u[1] the whole way round. box: u[0] to u[1] along and v[0] to v[1] round. ellipse: the ellipse inside that box. lasso: " +
        "inside the closed polygon through points (or face), straight between them in u and v, or a smooth curve through them when " +
        "curved. opening: the cells round where the part on comes out (a neck, an arm, an ear) within reach past its root ring, on every " +
        "part but those beyond it, its front dropping depth below the ring in its cut: unpaint one for a crew or V neckline, paint a wider " +
        "one and unpaint a narrower for a collar band round it.",
)
data class RecipeStroke(
    @Doc("The part painted on (a pair by either side: both sides take it, mirrored).")
    val on: String,
    @Doc("brush, line, band, box, ellipse, lasso or opening.")
    val shape: RecipeStrokeShape = RecipeStrokeShape.BRUSH,
    @Doc("Takes the cells from every area instead of painting them, as Alt in Dress.")
    val unpaint: Boolean = false,
    @Doc("band, box, ellipse: [from, to] along the part, 0 at its root to 1 at its far end.")
    val u: List<Float>? = null,
    @Doc("box, ellipse: [from, to] round the part; from may lie below 0 to cross v 0. The whole way round when left out.")
    val v: List<Float>? = null,
    @Doc("brush, line, lasso: the places as [u, v] on the part.")
    val points: List<List<Float>>? = null,
    @Doc("brush, line, lasso on a head: the places as [x, y] on its face seen from straight ahead, as a region's face.")
    val face: List<List<Float>>? = null,
    @Doc("brush, line: how far from the path a cell's middle may lie and be painted, in body units, as Dress's Size.")
    val size: Float = DEFAULT_BRUSH_SIZE,
    @Doc("On a part in the body's middle (a torso, a head): also painted mirrored across the middle, at -v, as Dress's Mirror.")
    val mirrored: Boolean = false,
    @Doc("Clean edges, 0 to 1: up to four rounds of smoothing the area's edge within two cells of the stroke, each cell taking what most of it and its neighbours are.")
    val clean: Float = 0f,
    @Doc("opening: round, a crew or scoop neck's curve, or v, two straight sides to a point.")
    val cut: RecipeOpeningCut = RecipeOpeningCut.ROUND,
    @Doc("opening: how far past the root ring of the part on it reaches all round, in body units.") @Range(min = 0.0)
    val reach: Float = DEFAULT_OPENING_REACH,
    @Doc("opening: how far its front drops below the ring at its middle, in body units; 0 is a plain ring.") @Range(min = 0.0)
    val depth: Float = 0f,
    @Doc(
        "band, box, ellipse, lasso: the stroke is kept as a vector shape, and where every stroke on its part is exact the cloth's edge " +
            "there is the shapes' outline itself rather than a smooth curve over the cells, however narrow: a tie, a collar's points, a " +
            "strap, a bikini's cups, cut crisp and with no area dropping out. It paints every cell within half a cell of it and unpaints " +
            "those more than half a cell inside it; a box or band reaching within half a row of an end of its part runs on over that end. " +
            "A stroke on a pair names the side the pair is kept on; clean is 0.",
    )
    val exact: Boolean = false,
    @Doc("An exact lasso: one smooth curve through its places rather than straight between them, turning sharply at corners.")
    val curved: Boolean = false,
    @Doc("An exact lasso with curved: the indices of the places where the curve turns sharply, as a tie's tip or a collar's point.")
    val corners: List<Int> = emptyList(),
)
