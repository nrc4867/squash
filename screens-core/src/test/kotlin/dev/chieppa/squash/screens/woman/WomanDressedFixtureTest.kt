package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.HandSockets
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.model.SocketSpec
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.ShowOutfit
import dev.chieppa.squash.document.worn
import dev.chieppa.squash.document.wornPart
import dev.chieppa.squash.screens.fennec.fennecText
import dev.chieppa.squash.screens.shell.SquashFormat
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SAVED_FILE = "../examples/$WOMAN_DRESSED_RESOURCE"
private const val UPDATE_PROPERTY = "squash.updateGoldens"

/** Where round her head its back lies, her face at v = 0, and how far either way of it a hair part's socket may lie: behind the ears. */
private const val HEAD_BACK_V = 0.5f
private const val BEHIND_EARS = 0.25f

/** The saved dressed woman stays what her commands make. Regenerate with ./gradlew :screens-core:test -PupdateGoldens and review the diff. */
class WomanDressedFixtureTest {
    private fun wearing(outfit: dev.chieppa.squash.document.OutfitId) = ShowOutfit(outfit).apply(womanDressedDocument()).document.character

    @Test
    fun theSavedDocumentIsWhatTheCommandsMake() {
        val file = File(SAVED_FILE)
        val text = fennecText(womanDressedDocument())
        if (System.getProperty(UPDATE_PROPERTY) != null) {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertEquals(text, file.readText().replace("\r\n", "\n"))
        assertEquals(womanDressedDocument(), savedWomanDressed())
        assertEquals(womanDressedDocument(), SquashFormat().decode(file.readBytes()))
    }

    @Test
    fun everyOutfitWearsWhatItPicksAndSharesGarments() {
        for ((id, _, picks) in OUTFITS) assertEquals(picks.values.toSet(), wearing(id).worn.map { it.id }.toSet(), id.value)
        val shared = OUTFITS.flatMap { it.third.values }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(setOf(SHORTS, PUMPS, BOOTS, DARK_TOP), shared)
        assertTrue(wearing(BARE).worn.isEmpty())
    }

    @Test
    fun theBlazerGoesOverTheBlouseAndTheTrousersWaistband() {
        assertEquals(listOf(BLOUSE, TROUSERS, BLAZER), wearing(OFFICE).worn.map { it.id }.filter { it != PUMPS })
    }

    @Test
    fun theSkirtHangsBelowHerOwnTorsoAndTheShoesSwapTheFoot() {
        val dressed = wearing(DRESS)
        val torso = dressed.wornPart(assertNotNull(dressed.part(TORSO)))
        assertNull(torso.changedBy, "the torso under the skirt is her own")
        val skirt = assertNotNull(dressed.wardrobe.garments.first { it.id == SKIRT }.branch(TORSO))
        assertTrue(skirt.from < 0f, "the skirt runs on below the torso's start")
        val foot = dressed.wornPart(assertNotNull(dressed.part(FOOT_R)))
        assertEquals(PUMPS, foot.changedBy, "the right foot is swapped too")
        assertIs<BodyPartChange.Swap>(dressed.wardrobe.garments.first { it.id == PUMPS }.changes[FOOT_L])
    }

    @Test
    fun eachOutfitBuildsThePartsItsGarmentsOwnAndNoOthers() {
        val owned = mapOf(
            BEACH to setOf(SANDAL_SOLE_L, SANDAL_SOLE_R, SHADES_L, SHADES_R, TEMPLE_L, TEMPLE_R),
            OFFICE to setOf(LAPEL_L, LAPEL_R, BLAZER_BUTTON, HEEL_L, HEEL_R),
            GOTH to setOf(BUCKLE, PENDANT),
            BARE to emptySet(),
        )
        for ((outfit, parts) in owned) {
            val built = assemblyParts(wearing(outfit)).filter { it.owner != null }.map { it.id }.toSet()
            assertEquals(parts, built, outfit.value)
        }
    }

    @Test
    fun inEveryOutfitEveryHairPartSitsOnTheBackOfHerHeadWhereTheUndressedWomansDoes() {
        val bare = assemblyParts(womanDocument().character)
        val undressed = socketsOfHairOn(bare)
        assertEquals(bare.count { it.role == Role.HAIR && it.socket?.parent == HEAD }, undressed.size)
        for ((part, socket) in undressed) assertTrue(abs(socket.v - HEAD_BACK_V) < BEHIND_EARS, "$part sits at v ${socket.v}")
        for (outfit in OUTFITS.map { it.first } + BARE) assertEquals(undressed, socketsOfHairOn(assemblyParts(wearing(outfit))), outfit.value)
    }

    /** Each hair part on the head among [parts], with the head's socket it sits on as placing reads it. */
    private fun socketsOfHairOn(parts: List<AssemblyPart>): Map<PartId, SocketSpec> {
        val head = assertNotNull(parts.firstOrNull { it.id == HEAD })
        val sockets = HandSockets.specs(head.placement + head.wornSockets).associateBy { it.name }
        return parts.filter { it.role == Role.HAIR && it.socket?.parent == HEAD }.associate { it.id to assertNotNull(sockets[it.socket!!.socket], "${it.id}'s socket") }
    }
}
