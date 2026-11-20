package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.dress.DEFAULT_BRUSH_SIZE
import dev.chieppa.squash.character.shape.DEFAULT_OPENING_REACH
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.DEFAULT_CREASE_STRENGTH
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.Outfit
import dev.chieppa.squash.document.TuckKind
import dev.chieppa.squash.document.View
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A character's clothes: where garments go, the garments, and the outfits that pick them. */
@Serializable
@Doc("Clothes: slots (places on the body an outfit fills once), garments and outfits.")
data class RecipeWardrobe(
    @Doc("Places on the body, as top, bottom, feet, head; each outfit picks at most one garment a slot.")
    val slots: List<GarmentSlot> = emptyList(),
    val garments: List<RecipeGarment> = emptyList(),
    @Doc("Named sets of garments: picks map a slot to a garment in it.")
    val outfits: List<Outfit> = emptyList(),
    @Doc("The outfit the character is saved wearing; bare when left out.")
    val shown: String? = null,
)

/**
 * One garment: cloth over the parts its [areas] paint or its [covers] box, standing off them by its [fit], with [holes] round the parts
 * that come out through it, and what it does to the parts under it.
 */
@Serializable
@Doc(
    "A garment: cloth painted on the body as named areas of cells, as Dress paints it, or given as covers (a box of cells each), standing " +
        "off the body by its fit. With areas, the parts it covers, the parts that come out through it, the small parts tucked inside it and " +
        "the inked seams are all read from the painted cells, as Dress writes them.",
)
data class RecipeGarment(
    val id: String,
    val name: String,
    @Doc("The wardrobe slot it goes in.")
    val slot: String? = null,
    @Doc("Counted out from the skin: under a waistband 1, a waistband 2, over both 3.")
    val layer: Int = FIRST_LAYER,
    @Doc("Its palette slot.")
    val color: String? = null,
    @Doc("How far it stands off the body, in body units, 0 or more: about 0.005 skin-tight, 0.01 fitted, 0.02 to 0.03 loose on a person-sized body; each cover's own ease when left out.") @Range(min = 0.0)
    val fit: Float? = null,
    @Doc("A fit for one covered part (a pair by its left side), over the garment's.")
    val partFits: Map<String, Float> = emptyMap(),
    @Doc("Whether its edges are inked.")
    val inkHem: Boolean = true,
    @Doc("The weight of its silhouette and inked edges as a share of the skin's line weight, 0 to 2; thinner than skin when left out, so cloth reads as cloth and not as a second body.") @Range(min = 0.0, max = 2.0)
    val inkWeight: Float = CLOTH_INK_WEIGHT,
    @Doc("How far its hems, neck openings and cuffs roll out past the cloth, in body units, 0 to 0.03: a cuff or a rolled hem that reads as cloth with a thickness. Every hem stands a little off the skin whatever this is.") @Range(min = 0.0, max = 0.03)
    val hemRoll: Float = 0f,
    @Doc("How strongly it creases on the inner side of each bending joint it covers, with ink, a shade band and a small fold, 0 to 2, times the character's cloth creases.") @Range(min = 0.0, max = 2.0)
    val creases: Float = DEFAULT_CREASE_STRENGTH,
    @Doc("How strongly it wrinkles where a pose bunches it (a seat, a knee, an inner elbow, a waist), 0 to 2, times the character's cloth creases; tight cloth wrinkles a little less than loose.") @Range(min = 0.0, max = 2.0)
    val wrinkles: Float = DEFAULT_CREASE_STRENGTH,
    @Doc("How strongly it hangs in soft folds down a loose top from the chest and gathers above its hems and cuffs, 0 to 2, times the character's cloth creases; a looser fit folds more.") @Range(min = 0.0, max = 2.0)
    val drape: Float = DEFAULT_CREASE_STRENGTH,
    @Doc(
        "How far it stands off a covered part along and round it, by part (a pair by its left side), over fit and partFits: keys in order " +
            "along u, each [u, ease] or [u, front, back] in body units, read as one smooth curve with no steps and flat past the first and " +
            "last key; the sides take the mean of front and back. A tee tight across the chest and looser at the hem, a sleeve snug at the " +
            "shoulder and bloused at the forearm, jeans fitted at the hip and flared at the ankle.",
    )
    val eases: Map<String, List<List<Float>>> = emptyMap(),
    @Doc("How the cloth hangs over each upright part it covers (a torso, a leg): from its highest covered place down, straight or flaring out, rather than following the body in; left out, it follows the body.")
    val hang: RecipeHang? = null,
    @Doc("Dropped seams, by part (a sleeve by its left side): how far down the part, in its u, 0 to 0.5, the seam sits where its cloth meets the cloth of the part it grows from; above it the cloth stands off as that part's does, with a pinched, inked seam, as a dropped shoulder.")
    val drops: Map<String, Float> = emptyMap(),
    @Doc("Covered parts whose join with the next covered part is inked. With areas, written from where two areas meet at a part's root instead.")
    val joins: List<String> = emptyList(),
    @Doc(
        "The cloth as named areas painted on the body, in order: each area's strokes take cells from the areas before it, so areas never " +
            "share a cell. Where two areas meet the cloth is one piece with an inked seam (with inkHem). A part an area paints needs no cover.",
    )
    val areas: List<RecipeArea> = emptyList(),
    @Doc(
        "Boxes of cells, one a part: the simple form of cloth, painted into the first area. With areas, a cover on a part they paint only " +
            "carries its outlines and ease, its range written from the cells. A pair's cover names its left side and covers both.",
    )
    val covers: List<RecipeCover> = emptyList(),
    @Doc("Holes round a part's root: the part and every part beyond it come out through the cloth bare, or ringed by a cuff band.")
    val holes: List<RecipeHole> = emptyList(),
    @Doc(
        "Whether each small part inside the cloth (an ear, tail, horn, spike or fin it holds whole; the toes or claws at a foot's end it " +
            "reaches) is tucked under smooth cloth or shaped by it. Left out: toes, claws, tails, horns and spikes tucked, the rest shaped.",
    )
    val tucks: List<RecipeTuck> = emptyList(),
    @Doc(
        "What it does to body parts under it, by part (a pair by its left side). With areas, a part the cloth says something of takes what " +
            "the cells say (through where it comes out, hide where it is tucked); a change here holds on every other part.",
    )
    val changes: Map<String, RecipeChange> = emptyMap(),
    @Doc("Loose ends it hangs, as a scarf's tails.")
    val looseEnds: List<LooseEnd> = emptyList(),
    @Doc(
        "Regions printed on the cloth, as a chest symbol, a pocket or a stripe: filled shapes in a palette slot, the border inked with ink, " +
            "given as a part's regions are but naming the body part under the cloth with on, in that part's u and v (or face places on a " +
            "head, or anchors). They ride the cloth's fit and pose and show only where the cloth is painted, over its areas' colours.",
    )
    val regions: List<RecipeRegion> = emptyList(),
    @Doc(
        "Lines printed on the cloth, as stitching or a pocket's outline: given as a part's marks are but naming the body part under the " +
            "cloth with on; a line only (kind line), its weight along it from width (stops at 0 to 1 along, each a share of the line's " +
            "weight: 0.4 a thin stitch), its colour from slot. A line shows only where the cloth is painted and ends where it ends.",
    )
    val marks: List<RecipeMark> = emptyList(),
)

/** One named area of a garment's cloth: the cells its [paint] strokes lay, in its own palette slot [color]. */
@Serializable
@Doc("One named area of a garment's cloth, as a row of Dress's areas: the cells its strokes paint, in its own colour.")
data class RecipeArea(
    @Doc("Unique within the garment, as area-1.")
    val id: String,
    @Doc("Its name in Dress; blank when left out.")
    val name: String = "",
    @Doc("Its palette slot, as a red collar on a blue tee; the garment's color when left out.")
    val color: String? = null,
    @Doc("A ribbed band, as a cuff or a sweater's hem: the ease, in body units, the area pulls its cloth in to, eased in over two rows either side; the cloth stands off as the rest does when left out.") @Range(min = 0.0)
    val gather: Float? = null,
    @Doc("Cloth turned back over the garment at its hem, as a rolled cuff, a rolled jeans hem or a turtleneck's fold-over: how far, in body units, the area stands out over the cloth beside it, its far edge inked and its color the cloth's back face; lies flat when left out.") @Range(min = 0.0, max = 0.03)
    val fold: Float? = null,
    @Doc("The strokes that paint it, laid in order: a stroke paints cells into this area and takes them from every other; unpaint takes them from every area.")
    val paint: List<RecipeStroke> = emptyList(),
)

/** How a garment's cloth hangs over an upright part: down from its highest covered place, flaring by [flare], at most [limit] off the body past its ease. */
@Serializable
@Doc("How cloth hangs below the highest place it covers on an upright part, rather than following the body in where it narrows.")
data class RecipeHang(
    @Doc("How far past its ease the cloth may stand off the body where it hangs over a narrowing, in body units: about 0.01 to 0.04 on a person-sized body.") @Range(min = 0.0)
    val limit: Float,
    @Doc("How far out it falls for each unit it falls down, 0 to 0.5: 0 straight down, 0.1 a slight flare.") @Range(min = 0.0, max = 0.5)
    val flare: Float = 0f,
)

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
 * holds. A brush or line reaches the parts beyond its part too, never the parts it sits on.
 */
@Serializable
@Doc(
    "One Paint stroke on a part, as Dress lays it: the cells of the part's chart (64 rows along it in u, 32 columns round it in v) whose " +
        "middles the shape holds. A cell is 1/64 of u by 1/32 of v; the cloth's edge is a smooth curve over the cells' edges, and an area " +
        "under two cells wide drops out. brush: every cell within size of the path through points (or face), straight between them in u " +
        "and v; one place is a dab. line: every cell within size of the straight lines in the body between its places. A brush or line " +
        "reaches the parts beyond its part (from a sleeve onto the hand), never the parts it sits on. band: rows u[0] to u[1] the whole way " +
        "round. box: u[0] to u[1] along and v[0] to v[1] round. ellipse: the ellipse inside that box. lasso: inside the closed polygon " +
        "through points (or face), straight between them in u and v. opening: the cells round where the part on comes out (a neck, an " +
        "arm, an ear) within reach past its root ring, on every part but those beyond it, its front dropping depth below the ring in its " +
        "cut: unpaint one for a crew or V neckline, paint a wider one and unpaint a narrower for a collar band round it.",
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
)

/** A hole of a garment round the root of [part], with a cuff band when [cuff]. */
@Serializable
@Doc("A hole round a part's root, as Dress's Hole: the part and every part beyond it come out bare, the cloth's cells on them left out.")
data class RecipeHole(
    @Doc("The part that comes out (a pair by its left side, both sides).")
    val part: String,
    @Doc("Whether a cuff band of the garment's cloth rings the part's root, as a sleeve's or a turtleneck's.")
    val cuff: Boolean = false,
)

/** The Tuck choice for [kind] on [part]. */
@Serializable
@Doc("A Tuck choice: a small part inside the cloth tucked under smooth cloth (hidden) or shaped (the cloth follows it).")
data class RecipeTuck(
    @Doc("The part (a pair by its left side): the small part itself, or the foot or hand whose toes or claws it means.")
    val part: String,
    @Doc("toes, claws, fingers, ears, tail, horns, spikes or fins.")
    val kind: TuckKind,
    @Doc("Tucked under smooth cloth (true) or shaped (false).")
    val tucked: Boolean,
)

/** The garment over one part: the cells of its chart from [from] to [to] along it and [fromV] to [toV] round it, standing off by [ease]. */
@Serializable
@Doc("The garment over one part: the cells of the part's chart its box holds. u runs along the part, past 1 to run on past its end or below 0 before its start; v round it, a span under 1 leaving an opening.")
data class RecipeCover(
    val part: String,
    @Doc("Where along the part it starts.") val from: Float = 0f,
    @Doc("Where along the part it ends.") val to: Float = 1f,
    @Doc("Where round the part it starts; below 0 to cross v 0.") val fromV: Float = 0f,
    @Doc("Where round the part it ends.") val toV: Float = 1f,
    @Doc("How far it stands off the part where no outline takes it further, when the garment has no fit.") val ease: Float = DEFAULT_EASE,
    @Doc("Side and Top outlines along the part's own x where the cloth stands off further, as a skirt's flare.")
    val outlines: Map<View, RecipeOutline> = emptyMap(),
)

/** What a worn garment does to a body part under it. */
@Serializable
@Doc("What a worn garment does to a body part under it: hide, through, redraw or swap.")
sealed interface RecipeChange {
    /** The part is not built while the garment is worn. */
    @Serializable
    @SerialName("hide")
    @Doc("The part is not built while the garment is worn.")
    data object Hide : RecipeChange

    /** The part comes out through a hole, with a cuff unless [cuff] is false. */
    @Serializable
    @SerialName("through")
    @Doc("The part comes out through a hole: an armhole or neckline with cuff false, a collar or sleeve cuff with cuff true.")
    data class Through(
        val cuff: Boolean = true,
        @Doc("How far the cuff reaches past the hole, a share of the part's length.") val cuffSize: Float = BodyPartChange.DEFAULT_CUFF_SIZE,
        val inkCuff: Boolean = true,
    ) : RecipeChange

    /** The part keeps its template and takes [shape] over its own. */
    @Serializable
    @SerialName("redraw")
    @Doc("The part keeps its template and takes this shape over its own: outlines and parameters by name, regions by id.")
    data class Redraw(val shape: RecipeShape = RecipeShape()) : RecipeChange

    /** The part takes another template with [shape] while the garment is worn. */
    @Serializable
    @SerialName("swap")
    @Doc("The part takes another template with this shape while the garment is worn, as a closed shoe swaps a foot for a blob.")
    data class Swap(val template: String, val version: Int? = null, val shape: RecipeShape = RecipeShape()) : RecipeChange
}

/** A shape a garment lays over a part, written as a part's own shape is. */
@Serializable
@Doc("A part's shape and colour as a garment lays it over the part.")
data class RecipeShape(
    val size: Float? = null,
    val lean: Float? = null,
    val outward: Float? = null,
    val outlines: Map<View, RecipeOutline> = emptyMap(),
    val params: Map<String, Float> = emptyMap(),
    val color: String? = null,
    val areas: Map<String, String> = emptyMap(),
    val regions: List<RecipeRegion> = emptyList(),
)
