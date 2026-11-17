package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.View
import kotlinx.serialization.Serializable

/**
 * One part, or a mirrored pair of parts when it has a [pair]: its template, where it sits, its shape and what is drawn on it. A pair is
 * described once, as its left side; the right side mirrors it.
 */
@Serializable
@Doc("A part, or with pair a mirrored pair described once as its left side.")
data class RecipePart(
    @Doc("The part's id; for a pair, the pair's id, its sides' ids under pair.")
    val id: String,
    @Doc("The template it is built from: torso, head, head-round, leg, arm, paw, hand, foot, tail, ear, tube, blob, sheet and so on.")
    val template: String,
    @Doc("The template's version; the newest shipped when left out.")
    val version: Int? = null,
    @Doc("The part it sits on; a pair on a pair sits each side on its own side. Left out for the root part.")
    val on: String? = null,
    @Doc("The socket on that part: one of its template's or plan's sockets, or a hand-made socket.<n> it declares under sockets.")
    val socket: String? = null,
    @Doc("Makes this a mirrored pair: its left and right parts' ids.")
    val pair: RecipePair? = null,
    @Doc("What the part is to the body; its template's role, or guessed from where it sits, when left out.")
    val role: Role? = null,
    @Doc("The garment that owns this part: it is built only while that garment is worn, as a heel, lens or buckle is.")
    val garment: String? = null,
    @Doc("How far the smooth join with the part it sits on reaches, in body units; a quarter of its least extent when left out.")
    val blend: Float? = null,
    @Doc("Scales the drawn part: its length and width together.")
    val size: Float? = null,
    @Doc("Degrees the part leans toward its own up (move.lean).")
    val lean: Float? = null,
    @Doc("Degrees the part leans out from the body's middle plane, mirrored on the right (move.outward); an A-pose arm takes 45.")
    val outward: Float? = null,
    @Doc("Outlines by view. Side sets height (a profile), Top sets width, Front the cross-section from -1 to 1 where the template says.")
    val outlines: Map<View, RecipeOutline> = emptyMap(),
    @Doc("Hand-made sockets on this part, for parts to sit on.")
    val sockets: List<RecipeSocket> = emptyList(),
    @Doc("Template parameters and other part parameters by name, as toes, merge, claw, generic.rings or joint.<n>.u.")
    val params: Map<String, Float> = emptyMap(),
    @Doc("The palette slot the whole part is coloured.")
    val color: String? = null,
    @Doc("Areas the template names, as a nail or a pad, each filled with a palette slot.")
    val areas: Map<String, String> = emptyMap(),
    @Doc("Painted regions on the part, each in a palette slot: a muzzle, a chest patch, a two-tone face.")
    val regions: List<RecipeRegion> = emptyList(),
    @Doc("Ink lines and fur strokes on the part.")
    val marks: List<RecipeMark> = emptyList(),
    @Doc("Mark groups, as a mouth: roles in draw order, each naming marks above.")
    val groups: List<MarkGroup> = emptyList(),
    @Doc("How the part's fur strokes show, and patches of fur stroke roots laid in rows.")
    val fur: RecipeFur? = null,
    @Doc("Heavier lines along a stretch of the part.")
    val lineWeights: List<RecipeLineWeight> = emptyList(),
)

/** The two sides of a pair. */
@Serializable
@Doc("The ids of a pair's two sides; <id>-l and <id>-r when left out.")
data class RecipePair(
    val left: String? = null,
    val right: String? = null,
)

/** A socket placed by hand on a part: where along it, where round it, how it points and whether it has a mirrored twin. */
@Serializable
@Doc("A hand-made socket, named socket.<n>.")
data class RecipeSocket(
    @Doc("Its number: the socket is named socket.<n>.")
    val n: Int,
    @Doc("Where along the part, 0 at its socket end to 1 at its far end.")
    val u: Float,
    @Doc("Where round the part, as the left side reads it: on an upright part 0 faces forward, 0.25 her right, 0.5 back, 0.75 her left.")
    val v: Float,
    @Doc("Degrees its outward direction turns from the surface's normal toward the part's end.")
    val outward: Float = 0f,
    @Doc("Whether it has a mirrored twin across the body's middle, for a pair to sit on.")
    val mirrored: Boolean = false,
    @Doc("How deep a part sits in here, as a multiple of that part's own sink: 0 seats it on the surface; once when left out.")
    val sink: Float? = null,
)
