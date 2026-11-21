package dev.chieppa.squash.character.mouth

/** Places along each lip of a shaped mouth. */
internal const val SAMPLES = 25

/** How far `wide` 1 draws the corners out, a share of the mouth's half width. */
internal const val WIDE_REACH = 0.25f

/** How far `round` 1 draws the corners in, a share of the mouth's half width. */
internal const val ROUND_NARROW = 0.5f

/** How far `smile` 1 lifts the corners, a share of the mouth's width. */
internal const val SMILE_LIFT = 0.16f

/** How far `upperLip` 1 raises the upper lip's middle, a share of the mouth's width. */
internal const val UPPER_RAISE = 0.1f

/** How far `round` 1 arches the upper lip up, a share of the lower lip's drop under it. */
internal const val ROUND_ARCH = 0.35f

/** How much of the drop `lowerLip` 1 takes back as the lower lip tucks up under the teeth. */
internal const val TUCK = 0.7f

/** Past how hard a press the lips show a crease under them. */
internal const val CREASE_FROM = 0.3f

/** How far under the lip line a full press's crease sits at its middle, a share of the mouth's width. */
internal const val CREASE_GAP = 0.035f

/** How far the crease reaches either side of the middle, a share of the mouth's half width. */
internal const val CREASE_REACH = 0.5f

/** Below this a jaw has not opened. */
internal const val LEAST_OPEN = 0.01f

/** How far below the lip line a tooth's top may sit and still be an upper tooth, a share of the mouth's width. */
internal const val UPPER_TOOTH_REACH = 0.03f

/** The tongue control at which the tongue's tip is up at the upper teeth; past it the tongue comes out. */
internal const val TONGUE_UP = 0.5f

/** How far under the upper lip the lifted tongue's top stays, a share of its height. */
internal const val TONGUE_TIP_ROOM = 0.1f

/** How far up behind the upper teeth the lifted tongue's tip tucks, a share of its height. */
internal const val TONGUE_BEHIND_TEETH = 0.15f

/** How near the middle the upper teeth's lower edge is read, a share of the mouth's half width. */
internal const val TEETH_MIDDLE = 0.3f

/** How much of the tongue is still above the lower lip when it is fully out. */
internal const val TONGUE_OUT_ABOVE = 0.3f

/** A tongue no mark draws: its width and height, shares of the mouth's width, how much of it sits below the lower lip at rest, and its places. */
internal const val PLAIN_TONGUE_WIDTH = 0.35f
internal const val PLAIN_TONGUE_HEIGHT = 0.2f
internal const val PLAIN_TONGUE_SUNK = 0.4f
internal const val PLAIN_TONGUE_STEPS = 16

/** Past how far a tuck the upper teeth rest over the lower lip, and how far down its band they reach, a share of it. */
internal const val TUCK_UNDER = 0.3f
internal const val TUCK_REST = 0.4f

/** How much a full press and a full tuck thin a lip's band, and a full pucker thickens it. */
internal const val PRESS_THIN = 0.3f
internal const val TUCK_THIN = 0.6f
internal const val ROUND_FULL = 0.4f

/** How far the room below the upper lip, or above the lower, reaches, in mouth widths: past anything a tongue or tooth reaches. */
internal const val BELOW = 10f

/** How far past its lip a tooth over the lips is seated, a share of the mouth's width, so the lip's ink breaks cleanly under it. */
internal const val OVER_SEAT = 0.012f
