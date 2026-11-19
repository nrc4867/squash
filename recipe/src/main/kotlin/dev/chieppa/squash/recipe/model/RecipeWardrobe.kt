package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.FIRST_LAYER
import dev.chieppa.squash.document.GarmentSlot
import dev.chieppa.squash.document.LooseEnd
import dev.chieppa.squash.document.Outfit
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

/** One garment: a shell over the parts it [covers], standing off them by its [fit], and what it does to the parts under it. */
@Serializable
@Doc("A garment: a shell over the parts it covers, standing off them by its fit, and what it does to the parts under it.")
data class RecipeGarment(
    val id: String,
    val name: String,
    @Doc("The wardrobe slot it goes in.")
    val slot: String? = null,
    @Doc("Counted out from the skin: under a waistband 1, a waistband 2, over both 3.")
    val layer: Int = FIRST_LAYER,
    @Doc("Its palette slot.")
    val color: String? = null,
    @Doc("How far it stands off the body, in body units; each cover's own ease when left out.")
    val fit: Float? = null,
    @Doc("A fit for one covered part (a pair by its left side), over the garment's.")
    val partFits: Map<String, Float> = emptyMap(),
    @Doc("Whether its edges are inked.")
    val inkHem: Boolean = true,
    @Doc("Covered parts whose join with the next covered part is inked.")
    val joins: List<String> = emptyList(),
    @Doc("The parts it covers, one cover each; a pair's cover names its left side and covers both.")
    val covers: List<RecipeCover> = emptyList(),
    @Doc("What it does to body parts under it, by part (a pair by its left side).")
    val changes: Map<String, RecipeChange> = emptyMap(),
    @Doc("Loose ends it hangs, as a scarf's tails.")
    val looseEnds: List<LooseEnd> = emptyList(),
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
