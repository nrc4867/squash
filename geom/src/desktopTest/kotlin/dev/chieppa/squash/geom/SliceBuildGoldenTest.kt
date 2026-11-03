package dev.chieppa.squash.geom

import dev.chieppa.squash.golden.GoldenFixture
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Vertex positions of a fixed tube against a saved fixture. */
class SliceBuildGoldenTest {
    @Test
    fun aTaperedRoundTubeMatchesItsFixture() {
        val patch = SliceBuilder.tube(
            TubeSpec(
                name = "body",
                axis = AxisSpline(listOf(Vec3.ZERO, Vec3(0.2f, -0.5f, 0f), Vec3(0f, -1f, 0f))),
                uStart = 0f,
                uEnd = 1f,
                rings = 6,
                segments = 8,
                section = SectionProfile.EGG.spline(8),
                radius = 0.3f,
                taper = 0.7f,
                startClosure = EndClosure.OPEN,
                endClosure = EndClosure.ROUND,
                jointUs = listOf(0.5f),
            ),
        )
        val text = (0 until patch.nu * patch.nv).joinToString("") { i ->
            val p = patch.point(i)
            "%.4f %.4f %.4f\n".format(Locale.ROOT, p.x, p.y, p.z)
        }
        GoldenFixture.check(Path.of("src/desktopTest/golden/tapered-tube.txt"), text, "geom")
    }

    @Test
    fun aMissingFixtureFailsAndSaysHowToRegenerate() {
        val fixture = Files.createTempDirectory("golden").resolve("missing.txt")
        val failure = assertFailsWith<AssertionError> { GoldenFixture.check(fixture, "text", "geom", update = false) }
        assertTrue("-PupdateGoldens" in failure.message.orEmpty())
        assertFalse(Files.exists(fixture))
    }

    @Test
    fun onlyAnExplicitUpdateWritesAFixture() {
        val fixture = Files.createTempDirectory("golden").resolve("written.txt")
        GoldenFixture.check(fixture, "text", "geom", update = true)
        assertEquals("text", fixture.readText())
        GoldenFixture.check(fixture, "text", "geom", update = false)
        assertFailsWith<AssertionError> { GoldenFixture.check(fixture, "other", "geom", update = false) }
    }
}
