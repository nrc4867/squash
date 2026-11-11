package dev.chieppa.squash.document

import dev.chieppa.squash.document.WinterFennec.BARE
import dev.chieppa.squash.document.WinterFennec.HAT
import dev.chieppa.squash.document.WinterFennec.HEAD
import dev.chieppa.squash.document.WinterFennec.WINTER
import dev.chieppa.stretch.test.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedPartTest {
    private val brim = PartId("brim")
    private val pom = PartId("pom")
    private val brimOnHead = PartInstance(brim, WinterFennec.ref("sheet"), SocketRef(HEAD, "socket.1"), role = Role.OTHER, garment = HAT)

    private fun withBrim(): SquashDocument = AddParts(listOf(brimOnHead)).apply(WinterFennec.dressed()).document

    @Test
    fun anOwnedPartBuildsOnlyWhileItsGarmentIsWorn() {
        val dressed = withBrim()
        assertTrue(dressed.character.isWorn(dressed.character.part(brim)!!))
        val bare = ShowOutfit(BARE).apply(dressed).document
        assertFalse(bare.character.isWorn(bare.character.part(brim)!!))
        assertTrue(bare.character.isWorn(bare.character.part(HEAD)!!))
    }

    @Test
    fun removingAGarmentRemovesThePartsItOwnsInOneStep() {
        val start = withBrim()
        scenario(start) {
            run(RemoveGarment(HAT))
            expect { assertEquals(null, it.character.part(brim)) }
            expect { assertEquals(emptyList(), it.character.ownedBy(HAT)) }
            undo()
            expect(start)
        }
    }

    @Test
    fun anOwnedPartNeedsItsGarmentInTheWardrobe() {
        val stray = brimOnHead.copy(garment = GarmentId("none"))
        assertFailsWith<IllegalArgumentException> { AddParts(listOf(stray)).apply(WinterFennec.dressed()) }
    }

    @Test
    fun onlyThatGarmentsPartsSitOnAnOwnedPart() {
        val body = PartInstance(pom, WinterFennec.ref("blob"), SocketRef(brim, "socket.1"), role = Role.OTHER)
        assertFailsWith<IllegalArgumentException> { AddParts(listOf(body)).apply(withBrim()) }
        val owned = AddParts(listOf(body.copy(garment = HAT))).apply(withBrim()).document
        assertEquals(listOf(brim, pom), owned.character.ownedBy(HAT).map { it.id })
        assertEquals(WINTER, owned.character.wardrobe.shown)
    }
}
