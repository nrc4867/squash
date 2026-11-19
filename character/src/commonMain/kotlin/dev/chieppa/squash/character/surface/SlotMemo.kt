package dev.chieppa.squash.character.surface

/**
 * Values kept by slot from one build to the next: each value kept since [begin] takes the place of the last build's in its slot once
 * [settle] is called, so a build stopped part way leaves the last one's in place. Called from one thread.
 */
internal class SlotMemo<V : Any> {
    private val kept = HashMap<Any, V>()
    private val made = HashMap<Any, V>()

    /** What the last settled build kept for [slot]. */
    fun last(slot: Any): V? = kept[slot]

    /** Keeps [value] for [slot] once [settle] is called. */
    fun keep(slot: Any, value: V) {
        made[slot] = value
    }

    /** Forgets what was kept since the last [settle]. */
    fun begin() {
        made.clear()
    }

    /** Keeps what was kept since the last call in place of what was there before in the same slots. */
    fun settle() {
        kept.putAll(made)
        made.clear()
    }
}
