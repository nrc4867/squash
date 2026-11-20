package dev.chieppa.squash.character.build

/**
 * What was derived from each placed part by the last finished run, kept by the part's identity, so a run over an assembly whose other
 * parts are the same objects as before derives again only for the parts that changed. Each run holds what it derives on its own and
 * replaces what is kept only when it finishes, so a stale run cut short, or one running beside a newer one, never mixes into it.
 */
class PlacedMemo<K : Any, V> {
    private var last: Map<K, V> = emptyMap()

    /** A run over the parts: [Run.of] each, then [Run.finish]. */
    fun run(): Run = Run(last)

    inner class Run internal constructor(private val before: Map<K, V>) {
        private val made = HashMap<K, V>()

        /** What [key] gave the last finished run, or what [make] gives now. */
        fun of(key: K, make: () -> V): V {
            @Suppress("UNCHECKED_CAST")
            val held = if (key in before) before[key] as V else make()
            made[key] = held
            return held
        }

        /** Keeps what this run asked for, in place of what was kept. */
        fun finish() {
            last = made
        }
    }
}
