package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.MarkFlight
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkVersion
import dev.chieppa.squash.document.WidthStop
import kotlinx.serialization.Serializable

/**
 * An area of colour on a part. Its border is given one way: [border] as u, v on the part's [patch], [face] as places on a head's face
 * seen from straight ahead, or [anchors] that may run across parts. The border is a smooth closed curve through its places but at
 * [corners]; regions through the same places meet along one edge.
 */
@Serializable
@Doc(
    "A painted region. Give its border one way: border ([u, v] on the part), face ([x, y] on a head's face seen from ahead) or anchors. " +
        "The border is a smooth closed curve through its places, so a few places make a round shape; corners makes some sharp, and curved: false runs it straight between them. " +
        "Two regions whose borders pass through the same places meet along one shared edge with no gap and no overlap, sharp where " +
        "the borders part: draw a two-tone face as a light region and a dark one sharing the places of their boundary.",
)
data class RecipeRegion(
    val id: String,
    @Doc("The palette slot it is painted in.")
    val slot: String,
    @Doc("Whether its border is inked as a line.")
    val ink: Boolean = false,
    @Doc("The patch its border's u, v lie on: body, or a patch the template names, as muzzle.")
    val patch: String = BODY_PATCH,
    @Doc("The border as [u, v] on the part: u along it from its socket end, v round it.")
    val border: List<List<Float>>? = null,
    @Doc("On a head: the border as [x, y] on the face seen from straight ahead, x to the character's left (negative for the right), y up, in the head's drawn units from its socket.")
    val face: List<List<Float>>? = null,
    @Doc("The border as anchors, each naming its part; for a region that runs across parts.")
    val anchors: List<Anchor>? = null,
    @Doc("Indices into the border's places that are sharp corners, as a chin's point.")
    val corners: List<Int> = emptyList(),
    @Doc("Whether the border is the smooth curve through its places on a part joined into the body's one surface, as a head on a neck; false runs it straight between them there.")
    val curved: Boolean = true,
)

/** A line or a fur stroke on a part, its places given as a region's border is. */
@Serializable
@Doc("A mark: an ink line through its places, or a fur stroke from its one root. Give its places one way: points, face or anchors.")
data class RecipeMark(
    val id: String,
    @Doc("line, fur or floating.")
    val kind: MarkKind = MarkKind.LINE,
    @Doc("The patch points lie on.")
    val patch: String = BODY_PATCH,
    @Doc("Its places as [u, v] on the part.")
    val points: List<List<Float>>? = null,
    @Doc("On a head: its places as [x, y] on the face seen from straight ahead.")
    val face: List<List<Float>>? = null,
    @Doc("Its places as anchors, each naming its part.")
    val anchors: List<Anchor>? = null,
    @Doc("How its weight runs along it: stops at 0 to 1 along, each a share of the line's weight.")
    val width: List<WidthStop> = emptyList(),
    @Doc("Whether it is inked; a mark a group fills may be left un-inked.")
    val ink: Boolean = true,
    @Doc("Its colour; the ink's when left out.")
    val slot: String? = null,
    @Doc("On a part in the middle of the body: also shown mirrored on the other side.")
    val mirrored: Boolean = false,
    @Doc("Whether it sits where the light and camera put it rather than on the surface.")
    val cameraLocked: Boolean = false,
    @Doc("Its shape in named camera bins (front, three-quarter, side), replacing its places there.")
    val versions: Map<String, MarkVersion> = emptyMap(),
    @Doc("For a floating mark: the places it passes through off the surface after its root, each {along, across, out} in body units from the root: along the way the part's u grows, across the way its v grows, out along the surface's normal.")
    val flight: List<MarkFlight> = emptyList(),
)

/** How a part's fur strokes show, and patches of stroke roots. */
@Serializable
@Doc("Fur on a part: how its strokes show and patches of stroke roots.")
data class RecipeFur(
    @Doc("Pixels between strokes on screen.")
    val spacing: Float? = null,
    @Doc("Pixels a stroke reaches.")
    val length: Float? = null,
    @Doc("Patches of stroke roots laid in staggered rows.")
    val patches: List<RecipeFurPatch> = emptyList(),
)

/**
 * Stroke roots in [rows] from [u]'s first value to its second, each row [columns] roots spaced round [v]'s range, every other row
 * staggered by half a step; each root a fur mark named `<name>-<row>-<column>`.
 */
@Serializable
@Doc("A patch of fur stroke roots in staggered rows, each root a fur mark named <name>-<row>-<column>.")
data class RecipeFurPatch(
    val name: String,
    @Doc("How many rows along the part.") val rows: Int,
    @Doc("How many roots round it in each row.") val columns: Int,
    @Doc("[from, to] along the part.") val u: List<Float>,
    @Doc("[from, to] round the part; [0, 1] all round.") val v: List<Float>,
)

/** A heavier line along a stretch of the part. */
@Serializable
@Doc("Heavier lines within radius of a stretch of the part.")
data class RecipeLineWeight(
    val id: String,
    @Doc("The stretch as [u, v] on the part.") val points: List<List<Float>>,
    @Doc("How far it reaches, a share of the part's length.") val radius: Float,
    @Doc("How much heavier: a line's weight times 1 plus this.") val amount: Float,
    @Doc("The patch points lie on.") val patch: String = BODY_PATCH,
)
