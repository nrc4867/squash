package dev.chieppa.squash.screens.twin

import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.document.OutfitId
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

private const val SAVED_FILE = "../examples/$TWIN_DRESSED_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** The saved dressed twin stays what her commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class TwinDressedFixtureTest {
    private fun wearing(outfit: OutfitId) = ShowOutfit(outfit).apply(twinDressedDocument()).document.character

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(twinDressedDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(twinDressedDocument(), savedTwinDressed())
        assertEquals(twinDressedDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Tag("integration")
    @Test
    fun theOutfitsAreTheReferencesFourByItsNames() {
        assertEquals(listOf("beach", "lawyer", "dress", "goth"), twinDressedDocument().character.wardrobe.outfits.map { it.id.value })
        for ((id, _, picks) in TWIN_OUTFITS) assertEquals(picks.values.toSet(), wearing(id).worn.map { it.id }.toSet(), id.value)
    }

    @Test
    fun theSkirtHangsBelowHerOwnTorso() {
        val dressed = wearing(DRESS)
        assertNull(dressed.wornPart(assertNotNull(dressed.part(TORSO))).changedBy, "the torso under the skirt is her own")
        assertTrue(assertNotNull(dressed.wardrobe.garments.first { it.id == SKIRT }.branch(TORSO)).from < 0f, "the skirt runs on below the torso's start")
    }

    @Tag("integration")
    @Test
    fun eachOutfitBuildsThePartsItsGarmentsOwnAndNoOthers() {
        val owned = mapOf(
            BEACH to setOf(SHADES_L, SHADES_R, TEMPLE_L, TEMPLE_R, CUP_L, CUP_R, HALTER_L, HALTER_R, SANDAL_SOLE_L, SANDAL_SOLE_R),
            LAWYER to setOf(LAPEL_L, LAPEL_R, BLAZER_BUTTON, HEEL_L, HEEL_R),
            DRESS to emptySet(),
            GOTH to setOf(BUCKLE, PENDANT, PLATFORM_L, PLATFORM_R),
        )
        for ((outfit, parts) in owned) assertEquals(parts, assemblyParts(wearing(outfit)).filter { it.owner != null }.map { it.id }.toSet(), outfit.value)
    }
}
