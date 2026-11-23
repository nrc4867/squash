package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.PartChange
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.recipe.figure.Silhouette

/**
 * A body posed as the app poses it, for fitting a key's outline to what the key's pose shows: [body] in [pose] with [changes] read over
 * its parts at full weight, as the silhouette it casts, standing on a ground [rootHeight] below its root's origin. Supplied by a caller
 * that can pose a character; without one a key is fitted on the body at rest.
 */
fun interface KeyBodies {
    fun silhouette(body: SquashDocument, pose: Pose, changes: List<PartChange>, rootHeight: Double): Silhouette
}
