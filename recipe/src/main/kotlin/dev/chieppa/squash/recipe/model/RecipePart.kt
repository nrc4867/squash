package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.PartBind
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
    @Doc("How far the smooth join with the part it sits on reaches, in body units. Left out, a part that starts open (a limb, a neck, a tail) rounds in by 1.4 times its root radius, held back where it runs close beside the part it sits on; any other takes its template's join or a quarter of its least extent. Off a template join it fills the crease where the two surfaces meet at an angle and leaves them as they are where they run alongside each other.")
    val blend: Float? = null,
    @Doc("For a part a garment owns, which bones it moves with: single rides the one bone it sits on, rigid, as a heel, buckle or button; follow takes the bones of the body under it, weighted as the body's skin is there, so a boot shaft, knee band or shell round a limb bends with the joint it crosses and keeps its stand-off from the body in a pose. Left out, a part that goes round the limb under it and crosses a joint there follows, and any other rides one bone.")
    val bind: PartBind? = null,
    @Doc("For a part a garment owns that follows the body, how rigid it keeps each stretch between joints, 0 to 1: 0 bends as the skin under it bends, more gathers the bend toward the joint, as a stiff boot shaft does, and 1 keeps each vertex on its heaviest bone alone. 0 when left out.")
    @Range(min = 0.0, max = 1.0)
    val stiff: Float? = null,
    @Doc("Scales the drawn part: its length and width together.")
    val size: Float? = null,
    @Doc("Degrees the part leans toward its own up (move.lean), less than 90 either way; with outward, a turn of the rest toward a corner.")
    val lean: Float? = null,
    @Doc("Degrees the part leans out from the body's middle plane, mirrored on the right (move.outward), less than 90 either way; an A-pose arm takes 45.")
    val outward: Float? = null,
    @Doc("Degrees the part turns about its own up, its tip swinging away from the body's middle plane, mirrored on the right (move.toeOut), after rest, lean, outward and roll: a foot's toe-out, the foot turning about the leg's line at the ankle with the leg and knee left facing forward. 0 when left out.")
    val toeOut: Float? = null,
    @Doc("Outlines by view. Side sets height (a profile), Top sets width, Front the cross-section from -1 to 1 where the template says.")
    val outlines: Map<View, RecipeOutline> = emptyMap(),
    @Doc("A tube part's widths as curves along its bone, in place of its Side and Top outlines: they are built into them as drawn.")
    val tube: RecipeTube? = null,
    @Doc("The direction the part rests in, in place of its template's (move.rest): a generic root resting up is a standing body.")
    val rest: RecipeRest? = null,
    @Doc("Degrees the part rolls about its own axis, turning its up toward its v 0.25 side, after rest, lean and outward (move.roll): a sheet resting out and rolled 90 faces forward.")
    val roll: Float? = null,
    @Doc("Whether Top's midline moves the part's middle sideways along its length, as Side's moves it up and down: a band or lens drawn curved in Top wraps round what it sits on (top.middle). Top sets width alone when left out.")
    val topMiddle: Boolean? = null,
    @Doc("A generic part's section, views and sampling (generic.*).")
    val generic: RecipeGeneric? = null,
    @Doc("Hand-made sockets on this part, for parts to sit on (socket.<n>.*).")
    val sockets: List<RecipeSocket> = emptyList(),
    @Doc("Hand-made joints on this part, each a bone poses name joint.<n> (joint.<n>.*).")
    val joints: List<RecipeJoint> = emptyList(),
    @Doc("Hand-made landmarks on this part (landmark.<n>.*).")
    val landmarks: List<RecipeLandmark> = emptyList(),
    @Doc("The template's own joints moved along the part, by name, to a u (move.joint.<name>).")
    val jointsAt: Map<String, Float> = emptyMap(),
    @Doc("Sockets this part offers moved, by name, to [u, v] or [u, v, sink] for both sides (move.socket.<name>.u, .v and .sink): the sink is how deep a part on it sits, times that part's own sink, 0 to 4, and is the socket's own when left out.")
    val socketsAt: Map<String, List<Float>> = emptyMap(),
    @Doc("Places the part's axis curves through after its socket point, as [along, up] or [along, up, side] in the part's own axes as fractions of its length (curve.<n>.*): along its rest direction, toward its top (Side's +y), toward its side at v 0.25. 2 to 24 places, which may turn back on themselves and leave the plane; the smooth curve through them is scaled to the part's length, so they set its shape and size its reach. Taken by every template that sweeps along its axis (templates.md says which); replaces a template's curve (tail-curve) or curves a straight part. Undrawn, thickness or girth and taper set its radius along it; drawn, its outlines give its width along the length and the curve its path. A bend tighter than the part is thick narrows it there, with a warning. An S with a hooked end: [[0.25, -0.12], [0.5, 0.08], [0.72, -0.05], [0.88, 0.1], [0.86, 0.22], [0.76, 0.24]].")
    val curve: List<List<Float>> = emptyList(),
    @Doc("A spiral the axis coils in, in place of curve places (curve.spiral.*).")
    val spiral: RecipeSpiral? = null,
    @Doc("Whether the join where this part sits on its parent is inked as a line round it (join.ink); inked when left out.")
    val inkJoin: Boolean? = null,
    @Doc("A preset of its template's (templates.md lists them): its parameter values, which params and the fields above override, as a bob for a hair volume.")
    val preset: String? = null,
    @Doc("Template parameters by name, as toes, merge or claw, and any dotted part parameter the fields above do not name.")
    val params: Map<String, Float> = emptyMap(),
    @Doc("The palette slot the whole part is coloured.")
    val color: String? = null,
    @Doc("Palette slots its copies pick among besides color, where it sits on a socket with spread or path: two or three shades of a fleece.")
    val shades: List<String> = emptyList(),
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
    @Doc("How deep a part sits in here, as a multiple of that part's own sink, 0 or more: 0 seats it on the surface; once when left out.")
    val sink: Float? = null,
    @Doc("How far a part on it stands off the surface along its normal, in body units, before it sinks: a pocket or button on a shirt of fit 0.012 takes a little more than 0.012 with sink 0. 0 when left out.")
    val outset: Float? = null,
    @Doc("How far from the part's axis, in body units, the root of a part on it sits, toward v at u: the part's root lies there however wide this part is drawn, in place of on the surface sunk by sink. A part on a figure takes it from the fit when left out.")
    val reach: Float? = null,
    @Doc("Makes it a row of this many sockets (2 to 16), evenly from u to `to` along the part and from v to toV round it, named socket.<n>.0, socket.<n>.1 and on, a part on each. Many of one part over an area or along a line take spread or path instead.")
    val count: Int? = null,
    @Doc("A row's last socket's u; the row's u when left out.")
    val to: Float? = null,
    @Doc("A row's last socket's v; the row's v when left out. A whole turn from v (v 0, toV 1) closes the row round the part, its sockets evenly a turn's share apart, as studs round a belt.")
    val toV: Float? = null,
    @Doc("The size a part placed on a row's first socket starts at, when the part gives no size of its own; with spread or path, each copy's size, times the part's own.")
    val size: Float? = null,
    @Doc("The size a part placed on a row's last socket starts at, as a share of the first's; with spread, the share of their size copies fall to at the area's edges.")
    val falloff: Float? = null,
    @Doc("The digit patch its u and v lie on, so it rides that digit: fingers:2 on a hand, fingers:thumb, toes:0 on a foot or paw. The part's first run reaching u when left out, which on a hand past the palm is fingers:0.")
    val patch: String? = null,
    @Doc("Makes it one socket showing many copies of the one part on it, scattered over the area from u to `to` along the part and v to toV round it: v 0 and toV 1 runs all round it.")
    val spread: RecipeSpread? = null,
    @Doc("Makes it one socket showing copies of the one part on it repeated along a path from (u, v) through its via places to (to, toV), or between two sockets.")
    val path: RecipePath? = null,
)

/** Copies scattered over an area of a part. */
@Serializable
@Doc("Copies of the part on a socket scattered over its area, each an instance of the one part: edit the part and every copy changes.")
data class RecipeSpread(
    @Doc("How many copies, 1 to 1000; fewer show where spacing leaves no room for more.")
    val count: Int,
    @Doc("Picks the places and jitter; another seed, another scatter.")
    val seed: Int = 0,
    @Doc("The least distance between two copies, in body units, so they do not pile up. None when left out.")
    val spacing: Float? = null,
    @Doc("How far each copy's size varies either way, as a share of it: 0.2 is 80% to 120%.")
    val sizeJitter: Float? = null,
    @Doc("The most degrees a copy leans from the surface's normal, each a random way.")
    val leanJitter: Float? = null,
    @Doc("The most degrees a copy turns about itself either way.")
    val turnJitter: Float? = null,
    @Doc("The outer share of the area over which copies' size falls to the socket's falloff, 0.35 when left out.")
    val edge: Float? = null,
    @Doc("Degrees each copy tilts from the surface's normal toward the body's down along the surface, its up turned to the normal, so hair clumps lie down over a head; 60 to 80 for a fall of hair, none when left out.")
    val droop: Float? = null,
    @Doc("Lays the copies in this many rows evenly along the area, count of them evenly round each, in place of a scatter: plates, scales, shingles. With outward near 90 each lies back along the part toward its end, over the root of the next row.")
    val rows: Int? = null,
    @Doc("How far round every other row is shifted, as a share of the gap between two copies in a row, where the copies lie in rows; 0.5 when left out, as bricks lie.")
    val stagger: Float? = null,
)

/** Copies repeated along a path on a part. */
@Serializable
@Doc("Copies of the part on a socket repeated along a path on its part, its axis along the surface's normal and its up along the path: beads, studs, a row of buttons, chain links.")
data class RecipePath(
    @Doc("How many copies, evenly from end to end; as many as pitch fits when left out.")
    val count: Int? = null,
    @Doc("The distance between copies, in body units; a ring part's links its inside length when neither count nor pitch is given, so each passes through the next.")
    val pitch: Float? = null,
    @Doc("Places [u, v] on the part the path runs through between its ends.")
    val via: List<List<Float>> = emptyList(),
    @Doc("Two sockets this part offers, by name, the path's ends in place of (u, v) and (to, toV): a hand-made socket.<n> listed here, or one of its template's.")
    val between: List<String>? = null,
    @Doc("Degrees each copy rolls about the path more than the one before it: 90 turns every other chain link.")
    val roll: Float? = null,
    @Doc("How far the path droops at its middle, in body units, as a chain hangs between its two ends; it lies on the part where the droop would take it inside.")
    val sag: Float? = null,
)
