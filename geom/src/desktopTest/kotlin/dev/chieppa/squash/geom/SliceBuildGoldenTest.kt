package dev.chieppa.squash.geom

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Vertex positions of a fixed tube against a saved fixture. A missing fixture is written, then reviewed in the diff. */
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
        val fixture = Path.of("src/desktopTest/golden/tapered-tube.txt")
        if (!fixture.exists()) {
            Files.createDirectories(fixture.parent)
            fixture.writeText(text)
        }
        assertEquals(fixture.readText().replace("\r\n", "\n"), text)
    }
}
