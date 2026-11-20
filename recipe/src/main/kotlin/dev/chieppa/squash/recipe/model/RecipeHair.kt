package dev.chieppa.squash.recipe.model

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.hair.HairPreset
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.document.LockOverride
import dev.chieppa.squash.document.LockPhysics
import dev.chieppa.squash.document.LockSection
import dev.chieppa.squash.document.LockTip
import kotlinx.serialization.Serializable

/** Hair made of locks on one head: a preset's groups, groups of the recipe's own or changes to a preset's, and the body's collision shapes. */
@Serializable
@Doc("Hair made of locks on a head: each lock a separate swept part, in groups (a fringe, the sides, the back, the crown, strays) over an inner mass. A preset gives a whole hairstyle; groups add to it or change its groups by id.")
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
    @Doc("Groups of locks: a group whose id a preset gives changes only the fields it sets; any other id adds a group.")
    val groups: List<RecipeLockGroup> = emptyList(),
    @Doc("Whether the body's collision shapes (a sphere in the head, a capsule along the neck, a sphere at each shoulder) are set up from the built body, for a solver to keep hair out of.")
    val colliders: Boolean = true,
)

/** A group of locks, every field but its id optional: what a preset's group of that id or a new group's defaults give fills the rest. */
@Serializable
@Doc("A group of locks. Every field but id is optional: a preset's group of the same id keeps what this leaves out; a new group needs its kind.")
data class RecipeLockGroup(
    @Doc("The group's id; a preset's groups are back, left, right, crown, fringe, strays and tail.")
    val id: String,
    @Doc("Where it roots: fringe, left, right, back, crown or strays.")
    val kind: LockGroupKind? = null,
    @Doc("How many locks.") @Range(min = 1.0, max = 48.0)
    val count: Int? = null,
    @Doc("How far round the head its roots reach, a share of its kind's own reach.") @Range(min = 0.0, max = 2.0)
    val spread: Float? = null,
    @Doc("Degrees its roots turn round the head, positive toward the character's right.") @Range(min = -180.0, max = 180.0)
    val around: Float? = null,
    @Doc("How far up the head its roots move, -1 to 1.") @Range(min = -1.0, max = 1.0)
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
    @Doc("ribbon, a flattened lock cupped round the head, or clump, a rounded one.")
    val section: LockSection? = null,
    @Doc("pointed, round, forked (2 or 3 points) or flicked (turning out at its end).")
    val tip: LockTip? = null,
    @Doc("How many points a forked tip ends in.") @Range(min = 2.0, max = 3.0)
    val tipPoints: Int? = null,
    @Doc("Degrees each lock rises off the head at its root: 0 lies flat, 90 stands straight out.") @Range(min = -30.0, max = 90.0)
    val rise: Float? = null,
    @Doc("How far the locks sweep sideways as they grow, -1 to 1: positive toward the character's right.") @Range(min = -1.0, max = 1.0)
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
    @Doc("Single locks set by hand, by index: any field given replaces the group's for that lock, guide gives it a curve of its own, hidden leaves it out.")
    val locks: List<LockOverride>? = null,
    @Doc("Leaves out a preset's group of this id.")
    val removed: Boolean = false,
)
