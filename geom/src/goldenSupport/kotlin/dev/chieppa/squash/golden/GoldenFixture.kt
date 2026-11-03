package dev.chieppa.squash.golden

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.fail

private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** Compares generated text against a saved fixture. Only an explicit update writes one; a missing fixture fails. */
object GoldenFixture {
    private fun regenerate(module: String) =
        "Regenerate with ./gradlew :$module:desktopTest -PupdateGoldens and review the diff."

    /** Fails unless [fixture] holds [text]. [module] names the Gradle module to regenerate it from. */
    fun check(
        fixture: Path,
        text: String,
        module: String,
        update: Boolean = System.getProperty(UPDATE_PROPERTY) != null,
    ) {
        if (update) {
            Files.createDirectories(fixture.parent)
            fixture.writeText(text)
            return
        }
        if (!fixture.exists()) fail("No golden fixture at $fixture. ${regenerate(module)}")
        assertEquals(fixture.readText().replace("\r\n", "\n"), text, "$fixture differs. ${regenerate(module)}")
    }
}
