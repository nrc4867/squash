package dev.chieppa.squash.character.build

/** What a part's parameters say of how its drawn views shape it. See HLD: data-model, Part template and part instance. */
object ViewParams {
    /** 1 lets Top's midline move the part's middle toward its side along its length, as Side's moves it up; 0 or unset, Top sets width alone. */
    const val TOP_MIDDLE = "top.middle"

    /** Whether [params] let Top's midline move the part's middle. */
    fun topMovesMiddle(params: Map<String, Float>): Boolean = (params[TOP_MIDDLE] ?: 0f) != 0f
}
