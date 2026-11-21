package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.document.PartingKind
import dev.chieppa.squash.document.Whorl
import kotlinx.serialization.Serializable

/** Hair made of locks on one head: a preset's groups, groups of the recipe's own or changes to a preset's, and the body's collision shapes. */
@Serializable
@Doc("Hair made of locks on a head: each lock a separate swept part, in groups (a fringe, the sides, the back, the crown, strays, the top) over an inner mass. A preset gives a whole hairstyle; groups add to it or change its groups by id. docs/reference/hair.md lists every field's range and units and each preset's groups.")
data class RecipeHair(
    @Doc("The head the locks grow on, by part id.")
    val on: String,
    @Doc("The inner mass the roots sit on, a hair-volume part on the head, by id; it hides the scalp and lies inside the locks. The head itself when left out.")
    val mass: String? = null,
    @Doc("A whole hairstyle: shortSpiky, bob, longStraight, ponytail (tied with a band), sideSwept or curly (round curled locks).")
    val preset: HairPreset? = null,
    @Doc("How far the head reaches from its middle, in body units, which a preset's sizes are shares of; read off the built head when left out.") @Range(min = 0.01, max = 2.0)
    val reach: Float? = null,
    @Doc("The locks' palette slot; the mass's base colour, else the head's, when left out.")
    val color: String? = null,
    @Doc("The palette slot of a ponytail's band; the locks' colour when left out.")
    val band: String? = null,
    @Doc("Groups of locks: a group whose id a preset gives changes only the fields it sets; any other id adds a group, which needs its kind. Left out or empty, the preset's groups stay as they are.")
    val groups: List<RecipeLockGroup> = emptyList(),
    @Doc("Whether the body's collision shapes (a sphere in the head, a capsule along the neck, a sphere at each shoulder) are set up from the built body, for a solver to keep hair out of.")
    val colliders: Boolean = true,
    @Doc("Which way the hair grows over the scalp: a whorl, a parting, an overall sweep and cowlicks. Each field given replaces the preset's; the preset's flow, else a crown whorl with no parting, when left out.")
    val flow: RecipeScalpFlow? = null,
    @Doc("An inner mass fitted to the head as built, as the app's preset menu fits one, in place of a mass part of the recipe's own: give this or mass, not both.")
    val fitMass: RecipeMassFit? = null,
    @Doc("The hair's drawn outline per view, which with fit the outer locks are fitted to: kit sheet hair traces one from a sheet.")
    val outline: RecipeHairOutline? = null,
)

/** A head's hair outline in a recipe: its views, each a closed curve with its tips and valleys, and whether the locks are fitted to it. */
@Serializable
@Doc("The hair's drawn outline: a closed curve per view round the region the hair covers, its tips and valleys, and whether the hair is fitted to it.")
data class RecipeHairOutline(
    @Doc("One outline a view, the front among them; side, three-quarter and back are optional.")
    val views: List<RecipeOutlineView>,
    @Doc("Whether the build fits the hair to the outline: the outer locks' roots, lengths, rise, sweep, fall and widths so their tips land on its tips and their edges follow it, the inner mass's crown and width to the region it covers, and every other lock kept inside it. The fitted values are the locks' overrides and the mass's parameters.")
    val fit: Boolean = true,
)

/** One view of a hair outline in a recipe. */
@Serializable
@Doc("The hair's outline in one view.")
data class RecipeOutlineView(
    @Doc("front, three-quarter (the camera 45 degrees round toward the character's left), side (the camera on the character's left) or back.")
    val view: OutlineView,
    @Doc("The closed curve round the region the hair covers as the view sees it, each [x, y]: x to the view's right and y up, in the head's drawn units from its origin, the frame face places use seen from the view's camera. 3 to 600 points.")
    val points: List<List<Float>>,
    @Doc("The points of its spikes and flicks, by index into points; found from the curve's shape when left out (a turn of 50 degrees or more out of the region within 2% of its length round).")
    val tips: List<Int>? = null,
    @Doc("The notches between them, by index into points; found from the curve's shape when left out (a turn of 35 degrees or more into the region).")
    val valleys: List<Int>? = null,
)

/** An inner mass fitted to the head as built. */
@Serializable
@Doc("An inner mass fitted to the head as built: a hair-volume cap with no strands, sized by the head's box, its top following the skull's dome, its front above the brow and its hem cut into points.")
data class RecipeMassFit(
    @Doc("Its part id; the head's id with -hair when left out.")
    val id: String? = null,
    @Doc("Its palette slot; the palette's hair slot, else the head's colour, when left out.")
    val color: String? = null,
    @Doc("hair-volume parameters over the fitted ones (templates.md, hair-volume): width, back, sides, fringe, rise, crown, locks, lockDepth, lockJag and the rest. Most heads need none.")
    val params: Map<String, Float> = emptyMap(),
)

/** The flow over a head's scalp, every field optional: what the preset's flow or the default flow gives fills the rest. */
@Serializable
@Doc("Which way hair grows over the scalp. Every lock root takes its direction from it unless its group's flow is under 1. Each field given replaces the preset's.")
data class RecipeScalpFlow(
    @Doc("The crown point hair spirals out from: around (degrees from the front toward the character's right, 180 the back), up (degrees from level, 90 the top), turn (-1 to 1, positive clockwise seen from above), strength (0 to 2) and radius (degrees across the head).")
    val whorl: Whorl? = null,
    @Doc("A line where hair splits and lies away to each side, the scalp showing along it as a thin line.")
    val part: RecipeParting? = null,
    @Doc("An overall comb toward one side, -1 to 1: positive toward the character's right; strongest over the top.") @Range(min = -1.0, max = 1.0)
    val sweep: Float? = null,
    @Doc("An overall comb back over the top, -1 to 1: positive toward the back, negative forward.") @Range(min = -1.0, max = 1.0)
    val sweepBack: Float? = null,
    @Doc("Small local swirls or flips, at most 8: around and up place each as a whorl's; radius in degrees; strength 0 to 2; turn 0 flips the hair back against the flow, 1 or -1 swirls it; lift in degrees raises the locks over it.")
    val cowlicks: List<Cowlick>? = null,
)

/** A parting in a recipe, its colour a palette slot by name. */
@Serializable
@Doc("A parting: none, centre or side; where across the top; how far back; how clean; the scalp's colour along it.")
data class RecipeParting(
    @Doc("none, centre or side.")
    val kind: PartingKind = PartingKind.NONE,
    @Doc("How far across the top it runs, -1 to 1 of the most a part sits off the middle (about over the outer corner of the eye): positive toward the character's right.") @Range(min = -1.0, max = 1.0)
    val at: Float = 0f,
    @Doc("How far back from the hairline it runs: 1 reaches the crown.") @Range(min = 0.2, max = 1.5)
    val length: Float = 1f,
    @Doc("How clean the split is, 0 a soft, wandering split to 1 a sharp, straight line.") @Range(min = 0.0, max = 1.0)
    val clean: Float = 0.8f,
    @Doc("The palette slot of the scalp along it; when left out, a scalp tone: the head's colour darkened toward the hair's.")
    val color: String? = null,
)

/** A group of locks, every field but its id optional: what a preset's group of that id or a new group's defaults give fills the rest. */
@Serializable
@Doc("A group of locks. Every field but id is optional: a preset's group of the same id keeps what this leaves out; a new group needs its kind.")
data class RecipeLockGroup(
    @Doc("The group's id; a preset's groups are back, left, right, crown, top, fringe, strays and tail, which of them each preset gives in docs/reference/hair.md.")
    val id: String,
    @Doc("Where it roots: fringe, left, right, back, crown, strays or top. Needed for a group the preset does not give.")
    val kind: LockGroupKind? = null,
    @Doc("How many locks.") @Range(min = 1.0, max = 48.0)
    val count: Int? = null,
    @Doc("How far round the head its roots reach, a share of its kind's own reach (a fringe's about 96 degrees across the front, a side's about 66, the back's about 116): 1 the kind's own, 0 all at its middle.") @Range(min = 0.0, max = 2.0)
    val spread: Float? = null,
    @Doc("Degrees its roots turn round the head, positive toward the character's right.") @Range(min = -180.0, max = 180.0)
    val around: Float? = null,
    @Doc("How far up the head its roots move, -1 to 1 of about 20 degrees (15 for a crown, strays and the top, whose height moves it forward).") @Range(min = -1.0, max = 1.0)
    val height: Float? = null,
    @Doc("Each lock's length, in body units.") @Range(min = 0.005, max = 3.0)
    val length: Float? = null,
    @Doc("How far lengths vary either way, a share of the length.") @Range(min = 0.0, max = 1.0)
    val lengthJitter: Float? = null,
    @Doc("Each lock's width at its root, in body units.") @Range(min = 0.002, max = 0.5)
    val width: Float? = null,
    @Doc("Each lock's thickness at its root, in body units.") @Range(min = 0.002, max = 0.5)
    val thickness: Float? = null,
    @Doc("What share of its root width a lock keeps toward its tip.") @Range(min = 0.0, max = 1.0)
    val taper: Float? = null,
    @Doc("ribbon, a flattened lock cupped round the head (smooth, straight or wavy hair, a bob, long hair), or clump, a rounded one (spiky, short or curly hair, tufts).")
    val section: LockSection? = null,
    @Doc("pointed (spikes, ragged ends), round (a bob's blunt ends, curls), forked into 2 or 3 points (a choppy, layered fringe) or flicked, turning out 50 degrees at its end (a flicked fringe or hem).")
    val tip: LockTip? = null,
    @Doc("How many points a forked tip ends in.") @Range(min = 2.0, max = 3.0)
    val tipPoints: Int? = null,
    @Doc("Degrees each lock's outer part lifts off the head, past about half its length; it lies along the head from its root: 0 lies flat all along, 90 stands its end straight out. Over about 30 short locks read as spikes. A tied lock leaves its tie at this angle.") @Range(min = -30.0, max = 90.0)
    val rise: Float? = null,
    @Doc("How far the locks sweep sideways as they grow, over the way the scalp's flow grows them, -1 to 1: 1 turns a lock 70 degrees round over its length toward the character's right.") @Range(min = -1.0, max = 1.0)
    val sweep: Float? = null,
    @Doc("How far the locks fall toward the ground along their length, 0 none to 1 hanging.") @Range(min = 0.0, max = 1.0)
    val fall: Float? = null,
    @Doc("How many turns each lock curls toward its underside; negative the other way.") @Range(min = -3.0, max = 3.0)
    val curl: Float? = null,
    @Doc("How far each lock waves sideways, a share of its width.") @Range(min = 0.0, max = 2.0)
    val wave: Float? = null,
    @Doc("How far each lock turns about itself over its length, in degrees.") @Range(min = -360.0, max = 360.0)
    val twist: Float? = null,
    @Doc("Which picks the jitter: another seed, another layout with the same look.")
    val seed: Int? = null,
    @Doc("Its layer: locks of a higher layer lie over those of a lower one.") @Range(min = 0.0, max = 8.0)
    val layer: Int? = null,
    @Doc("How far its roots gather toward one point, 0 spread to 1 all at one tie.") @Range(min = 0.0, max = 1.0)
    val tie: Float? = null,
    @Doc("The palette slot of a band round the tie.")
    val band: String? = null,
    @Doc("Its palette slot, over the hair's.")
    val color: String? = null,
    @Doc("How its locks move once physics drives them: stiffness, damping, weight, gravity, wind, angleLimit, neighbours.")
    val physics: LockPhysics? = null,
    @Doc("How far its locks grow the way the scalp's flow says, 0 its kind's own way (down the head; a crown's swirling out from its middle) to 1 the flow's.") @Range(min = 0.0, max = 1.0)
    val flow: Float? = null,
    @Doc("Single locks set by hand, by index: any field given replaces the group's for that lock, guide gives it a curve of its own, hidden leaves it out.")
    val locks: List<LockOverride>? = null,
    @Doc("Leaves out a preset's group of this id.")
    val removed: Boolean = false,
)
