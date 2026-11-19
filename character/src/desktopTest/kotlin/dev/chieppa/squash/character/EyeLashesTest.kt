package dev.chieppa.squash.character

import dev.chieppa.squash.character.surface.EyeShapes
import dev.chieppa.squash.document.EyeLook
import dev.chieppa.squash.document.LashCountSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An eye's lashes as a count of flicks along its upper lid, and a lash flag in a saved file read as a count. */
class EyeLashesTest {
    private val look = EyeLook(openingTilt = 12f, tilt = -5f)

    @Test
    fun eachCountDrawsThatManyFlicks() {
        for (count in 0..3) assertEquals(count, EyeShapes(look.copy(lashes = count)).lashes.size)
    }

    @Test
    fun theFirstFlickRisesFromTheBackCornerAndTheOthersFurtherForwardAlongTheLid() {
        val lashes = EyeShapes(look.copy(lashes = 3)).lashes
        val roots = lashes.map { it.first() }
        assertTrue(roots.zipWithNext().all { (a, b) -> b.u > a.u }, "each further flick rises further toward the front: $roots")
        assertTrue(lashes.all { it.last().v > it.first().v }, "every flick rises from the lid")
    }

    @Test
    fun aFlagInASavedFileReadsAsOneFlickOrNone() {
        val json = Json
        assertEquals(1, json.decodeFromString(LashCountSerializer, "true"))
        assertEquals(0, json.decodeFromString(LashCountSerializer, "false"))
        assertEquals(2, json.decodeFromString(LashCountSerializer, "2"))
        assertEquals(0, json.decodeFromString(EyeLook.serializer(), """{"lashes": false}""").lashes)
    }
}
