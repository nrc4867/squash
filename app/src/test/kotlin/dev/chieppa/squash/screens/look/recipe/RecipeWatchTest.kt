package dev.chieppa.squash.screens.look.recipe

import dev.chieppa.squash.recipe.RecipeException
import kotlin.test.Test
import kotlin.test.assertEquals

class RecipeWatchTest {
    /** A watch over [texts], one a change, each read once, stopping after the last. */
    private fun watched(texts: List<String?>, build: (String) -> Int = { 0 }): Pair<RecipeWatch, List<String>> {
        val built = mutableListOf<String>()
        var at = 0
        val watch = RecipeWatch(
            read = { texts[at] ?: throw RecipeException("$", "no recipe file") },
            build = { built += it; build(it) },
            changed = { ++at < texts.size },
            name = "test.recipe.json",
        )
        watch.run()
        return watch to built
    }

    @Test
    fun `builds once a change and not again for the same text`() {
        val (watch, built) = watched(listOf("a", "a", "b", "b", "a"))
        assertEquals(listOf("a", "b", "a"), built)
        assertEquals(3, watch.builds)
    }

    @Test
    fun `a failed build or a missing file keeps watching`() {
        val (_, built) = watched(listOf("a", null, "bad", "c")) { if (it == "bad") throw RecipeException("$.parts", "wrong") else 0 }
        assertEquals(listOf("a", "bad", "c"), built)
    }

    @Test
    fun `a build that throws something else keeps watching`() {
        val (_, built) = watched(listOf("a", "b")) { if (it == "a") error("no device") else 0 }
        assertEquals(listOf("a", "b"), built)
    }
}
