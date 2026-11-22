package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.field.smoothstep

/** Above this cosine between two posed members' gradients, about 4 degrees, their join does not blend. */
private const val ALONGSIDE_COS = 0.998f

/** At or below this cosine, about 20 degrees, two posed members meet at a crease and their join blends by its whole blend. */
private const val CREASE_COS = 0.94f

/** Below this cosine, about 120 degrees, two posed members' surfaces face each other, and their join starts to give up its blend. */
private const val FACING_COS = -0.5f

/** At or below this cosine, about 145 degrees, two posed members' surfaces meet as a contact, by their plain union. */
private const val CONTACT_COS = -0.8f

/**
 * The share of its blend a member's smooth union with its parent takes in a pose, from the cosine [cos] of the angle between the two
 * members' gradients there: the gradient-based operator (Gourmel et al., "A gradient-based implicit blend", 2013, as the rest field's
 * `angleShare`), none where the surfaces run alongside each other and all of it where they meet at a crease, which a pose opens at
 * shallower angles than the rest field's joins meet at, so it takes the whole blend from about 20 degrees; with its contact case, falling
 * away again where the two surfaces face each other, so a limb laid against what it joins meets it by their plain union and does not web.
 */
fun posedBlendShare(cos: Float): Float = smoothstep(ALONGSIDE_COS, CREASE_COS, cos) * smoothstep(CONTACT_COS, FACING_COS, cos)
