package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

/**
 * What building a hand or a foot again costs, its patches and its mesh, as a change to its shape or parameters builds it: the blended
 * `hand` 4 and 5 and `foot` 3, with a pitched shoe's foot, beside the lofted `hand` 3 and `foot` 2. A pose only skins them. The medians go to
 * the test's output.
 */
@Tag("slow")
class BlendedHandFootBenchTest {
    private val templates = TemplateFolder.shipped()

    private fun median(id: String, version: Int, params: Map<String, Float> = emptyMap()): Double {
        val template = templates.parts.single { it.id == TemplateId(id) && it.version == version }
        val times = (0 until WARM + RUNS).map {
            val started = System.nanoTime()
            val patches = PartBuilder.build(template, params)
            PartBuilder.mesh(id, template, patches)
            (System.nanoTime() - started) / 1e6
        }.drop(WARM).sorted()
        return times[times.size / 2].also { println("$id $version $params: median ${"%.2f".format(it)} ms, fastest ${"%.2f".format(times.first())} ms") }
    }

    @Test
    fun aBlendedHandBuildsUnderFiftyMilliseconds() {
        assertTrue(median("hand", 5) < BUDGET_MS)
        assertTrue(median("hand", 4) < BUDGET_MS)
        median("hand", 3)
        assertTrue(median("foot", 3) < BUDGET_MS)
        assertTrue(median("foot", 3, mapOf("toes" to 0f, "toeBox" to 1f, "pitch" to 24f)) < BUDGET_MS)
        median("foot", 2)
    }

    private companion object {
        const val WARM = 10
        const val RUNS = 21
        const val BUDGET_MS = 50.0
    }
}
