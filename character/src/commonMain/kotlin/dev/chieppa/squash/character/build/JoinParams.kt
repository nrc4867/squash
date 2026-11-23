package dev.chieppa.squash.character.build

private const val PREFIX = "join."

/** What a part's parameters say of the join where it sits on its parent. No part's build reads them. See HLD: look, Where parts meet. */
object JoinParams {
    /** 0 leaves the join unlined; 1 or unset inks it as a line round the part. */
    const val INK = "${PREFIX}ink"

    /** On a head, its character's neck style (see [NeckJoin]); unset on every other part. */
    const val NECK = "${PREFIX}neck"

    fun isJoin(name: String) = name.startsWith(PREFIX)
}
