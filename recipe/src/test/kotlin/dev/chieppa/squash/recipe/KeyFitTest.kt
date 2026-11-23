package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.recipe.build.convertToFigure
import dev.chieppa.squash.recipe.build.fittedKey
import dev.chieppa.squash.recipe.figure.P2
import dev.chieppa.squash.recipe.model.RecipeFigureStroke
import dev.chieppa.squash.recipe.model.RecipeKey
import dev.chieppa.squash.recipe.model.RecipeKeyOutline
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A key's outline fitted to width changes: a leg's outer side redrawn fuller, read on the body at rest, widens that leg there and leaves
 * the trunk as it is. Builds the body's field a few times, so integration.
 */
@Tag("integration")
class KeyFitTest {
    private val folder = TemplateFolder.shipped()

    private val drawn = RecipeJson.read(
        """
        {"format": 1, "id": "stand", "name": "Stand", "parts": [
          {"id": "trunk", "template": "tube", "rest": "up",
           "tube": {"length": 0.4, "closed": true, "profiles": {
             "front": {"through": [[0.04, 0.05], [0.2, 0.06], [0.36, 0.05]]},
             "back": {"through": [[0.04, 0.05], [0.2, 0.055], [0.36, 0.05]]},
             "left": {"through": [[0.04, 0.075], [0.2, 0.08], [0.36, 0.07]]}}},
           "sockets": [{"n": 1, "u": 0.1, "v": 0.75, "mirrored": true, "reach": 0.04}]},
          {"id": "legs", "template": "tube", "on": "trunk", "socket": "socket.1", "pair": {}, "rest": "down", "outward": 1, "blend": 0.04,
           "tube": {"length": 0.35, "profiles": {
             "front": {"through": [[0, 0.033], [0.3, 0.026]]}, "back": {"through": [[0, 0.033], [0.3, 0.026]]},
             "outer": {"through": [[0, 0.034], [0.15, 0.032], [0.3, 0.027]]}, "inner": {"through": [[0, 0.03], [0.3, 0.024]]}}}}
        ]}
        """.trimIndent(),
    )

    @Test
    fun aLegRedrawnFullerWidensThatLegAlone() {
        val traced = checkNotNull(convertToFigure(drawn, folder).recipe.figure)
        val rootHeight = traced.rootHeight
        val outer = traced.front.flatMap { stroke -> stroke.through.orEmpty().map { P2(it[0].toDouble(), it[1].toDouble()) } }
            .filter { it.y in 0.12..0.3 && it.x > 0.06 }
            .sortedByDescending { it.y }
        check(outer.size >= 3) { "the leg's outer side is traced: $outer" }
        val fuller = RecipeFigureStroke("outer-leg", through = outer.map { listOf((it.x + 0.006).toFloat(), it.y.toFloat()) })
        val key = RecipeKey("knee", listOf(BonePose(PartId("legs-r"), "base", lean = 10f, mode = JointMode.FREE)))
        val report = fittedKey(drawn, key, RecipeKeyOutline(front = listOf(fuller), rootHeight = rootHeight, rounds = 3, joins = false), "$.keys[0].outline", folder)
        assertTrue(report.means.last() < report.means.first(), "the rounds bring the body nearer the redrawn side: ${report.means}")
        val legs = report.changes.single { it.part == "legs" }
        assertTrue(legs.widths.getValue("outer").any { it[1] > 1.05f }, "the leg's outer side widens: ${legs.widths}")
        val trunk = report.changes.firstOrNull { it.part == "trunk" }
        assertTrue(trunk == null || trunk.widths.values.flatten().all { it[1] in 0.97f..1.03f }, "the trunk stays: ${trunk?.widths}")
    }
}
