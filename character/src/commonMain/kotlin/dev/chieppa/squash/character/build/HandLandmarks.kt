package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.LandmarkSpec
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SetParameters

private const val PREFIX = "landmark."

/**
 * Landmarks a person places on a part with the Move tool, held in that part's parameters as `landmark.<n>.u` and `.v`, so pairs,
 * overrides, undo and saving treat them like any other. Landmark `<n>` is named `landmark.<n>`; a part's parameters hold only
 * numbers, so the name is its number. Face mark groups anchor to them. See HLD: data-model, Joints and landmarks by hand.
 */
object HandLandmarks {
    fun name(id: Int) = "$PREFIX$id"

    fun u(id: Int) = "${name(id)}.u"

    fun v(id: Int) = "${name(id)}.v"

    fun isHandLandmark(name: String) = name.startsWith(PREFIX)

    fun idOf(landmark: String): Int? = if (landmark.startsWith(PREFIX)) landmark.removePrefix(PREFIX).toIntOrNull() else null

    fun ids(params: Map<String, Float>): List<Int> =
        params.keys.filter(::isHandLandmark).mapNotNull { it.removePrefix(PREFIX).substringBefore('.').toIntOrNull() }.distinct().sorted()

    fun nextId(params: Map<String, Float>): Int = (ids(params).maxOrNull() ?: 0) + 1

    /** The hand-made landmarks [params] hold. */
    fun specs(params: Map<String, Float>): List<LandmarkSpec> = ids(params).mapNotNull { id ->
        LandmarkSpec(name(id), params[u(id)] ?: return@mapNotNull null, params[v(id)] ?: return@mapNotNull null)
    }

    /** The command that places landmark [id] on [part] at [u] and [v]: one undo step. */
    fun add(part: PartId, id: Int, u: Float, v: Float, thisSide: Boolean = false): SetParameters =
        SetParameters(part, mapOf(u(id) to u, v(id) to v), thisSide)

    /** The command that clears landmark [id] from [part]: one undo step. */
    fun delete(part: PartId, id: Int, thisSide: Boolean = false): SetParameters =
        SetParameters(part, mapOf(u(id) to null, v(id) to null), thisSide)
}
