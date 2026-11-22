package dev.chieppa.squash.recipe

import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.recipe.build.convertToFigure
import dev.chieppa.squash.recipe.build.fittedFigure
import dev.chieppa.squash.recipe.figure.readAt
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A body of tubes traced into a figure outline from its own silhouette, then built from that outline alone: the fit gives back the widths,
 * the roots and a body that lies on the outline. Builds the body's field a few times, so integration.
 */
@Tag("integration")
class FigureRoundTripTest {
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
          {"id": "legs", "template": "tube", "on": "trunk", "socket": "socket.1", "pair": {}, "rest": "down", "outward": 1,
           "tube": {"length": 0.35, "profiles": {
             "front": {"through": [[0, 0.033], [0.3, 0.026]]}, "back": {"through": [[0, 0.033], [0.3, 0.026]]},
             "outer": {"through": [[0, 0.034], [0.15, 0.032], [0.3, 0.027]]}, "inner": {"through": [[0, 0.03], [0.3, 0.024]]}}}}
        ]}
        """.trimIndent(),
    )

    @Test
    fun aBodyTracedIntoAFigureIsBuiltBackFromItAlone() {
        val converted = convertToFigure(drawn, folder)
        val figure = checkNotNull(converted.recipe.figure)
        assertTrue(figure.front.isNotEmpty() && figure.side.isNotEmpty(), "both views are traced")
        assertTrue(converted.recipe.parts.all { it.tube?.profiles.isNullOrEmpty() }, "the tubes give up their profiles")
        assertEquals(null, converted.recipe.parts.first().sockets.single().reach, "and the socket its reach, for the fit to place")
        val report = fittedFigure(converted.recipe, figure.copy(rounds = 1), folder)
        val trunk = report.fit.parts.getValue("trunk").radii
        val legs = report.fit.parts.getValue("legs").radii
        assertEquals(0.08, readAt(trunk.getValue("left"), 0.2), 0.004, "the trunk's width")
        assertEquals(0.06, readAt(trunk.getValue("front"), 0.2), 0.004, "the trunk's front")
        assertEquals(0.032, readAt(legs.getValue("outer"), 0.15), 0.004, "the leg's outer side")
        assertEquals(0.04, report.fit.parts.getValue("legs").reach!!, 0.006, "the leg's root lies where it did")
        assertTrue(report.measure.mean < 0.002, "the body built from the figure lies on it: mean miss ${report.measure.mean}")
        assertTrue(abs(report.means.first() - report.measure.mean) >= 0.0 && report.roundTimes.size == 2, "one plain build and one correction round")
    }
}
