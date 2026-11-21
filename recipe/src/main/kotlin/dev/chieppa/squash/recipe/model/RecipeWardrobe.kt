package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.CLOTH_INK_WEIGHT
import dev.chieppa.squash.document.DEFAULT_CREASE_STRENGTH
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.DEFAULT_NET_ANGLE
import dev.chieppa.squash.document.DEFAULT_NET_HOLE
import dev.chieppa.squash.document.DEFAULT_NET_THREAD
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.NetPattern
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
    @Doc("How strongly it gathers in short folds just above its hems and cuffs, 0 to 2, times the character's cloth creases.") @Range(min = 0.0, max = 2.0)
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

/** One named area of a garment's cloth: the cells its [paint] strokes lay, in its own palette slot [color], sheer at [opacity] or a [net]. */
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
    @Doc("Sheer cloth, as stockings, tights or a chiffon layer: how much of the skin under it the cloth hides where it faces the viewer, 0 to 1, times its slot's own opacity; it hides more as it turns away, so it darkens toward the silhouette. The skin's colour, shading and ink show through, tinted by the cloth. Whole when left out.") @Range(min = 0.0, max = 1.0)
    val opacity: Float? = null,
    @Doc("A net of thin threads whose holes show the skin, as fishnets or a lace-style mesh, finer than a cell; plain cloth when left out.")
    val net: RecipeNet? = null,
    @Doc("The strokes that paint it, laid in order: a stroke paints cells into this area and takes them from every other; unpaint takes them from every area.")
    val paint: List<RecipeStroke> = emptyList(),
)

/** An area knitted as a net: two sets of threads [thread] wide, [hole] apart, crossing as [pattern] at [angle] degrees. */
@Serializable
@Doc("A net of threads laid on the body under the cloth, stretching with it in every pose; too fine to draw, it reads as an even tone.")
data class RecipeNet(
    @Doc("How the two sets of threads cross: diamond, both slanting from the part's length by angle, one each way, as fishnets; square, one along the part turned by angle and one square to it.")
    val pattern: NetPattern = NetPattern.DIAMOND,
    @Doc("The spacing between threads, in body units: about 0.02 to 0.04 for fishnets on a person-sized body.") @Range(min = 0.004, max = 0.2)
    val hole: Float = DEFAULT_NET_HOLE,
    @Doc("How wide a thread is, in body units, under the hole.") @Range(min = 0.0005)
    val thread: Float = DEFAULT_NET_THREAD,
    @Doc("In degrees, 0 to 90: a diamond net's threads' slant from the part's length, 45 for square diamonds, less for taller ones; a square net's turn from along and round.") @Range(min = 0.0, max = 90.0)
    val angle: Float = DEFAULT_NET_ANGLE,
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
