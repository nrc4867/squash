package dev.chieppa.squash.character.hair.fit

import dev.chieppa.squash.document.OutlineView
import dev.chieppa.squash.geom.Vec2

/** A tip of the outline no lock reached: in which [view], [at] where in its flat frame, and [why]. */
data class UnreachedTip(val view: OutlineView, val at: Vec2, val why: Why) {
    enum class Why {
        /** No free lock's root faces the view. */
        NO_LOCK_FACES,

        /** Every lock facing the view went to another tip. */
        TOO_FEW_LOCKS,

        /** Only locks that cannot serve it were left: fringe locks for a tip off the face, or locks facing away for a tip over it. */
        NO_LOCK_SERVES,

        /** The lock sent to it ends short of it at the longest a fit may grow it. */
        OUT_OF_REACH,

        /** The lock sent to it was held short of it to stay inside the outlines. */
        KEPT_INSIDE,
    }
}
