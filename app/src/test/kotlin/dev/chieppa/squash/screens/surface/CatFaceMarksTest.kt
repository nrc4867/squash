package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.screens.TestWork
import dev.chieppa.squash.screens.cat.CAT_HEAD
import dev.chieppa.squash.screens.cat.catDocument
import dev.chieppa.squash.screens.smoke.CloseUps
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** How far from the painted eyes' lids, in body units, the whiskers stay. */
private const val CLEAR_OF_EYES = 0.03f

/** The cat's whiskers lie on its whisker pads and never run up the bridge of its nose to its painted eyes. */
class CatFaceMarksTest {
    @Test
    fun theWhiskersStayClearOfTheEyes() {
        TestWork().use { work ->
            val session = CharacterSession(catDocument(), CloseUps.shippedLibrary(), work.scope, work.dispatcher)
            work.settle(session)
            val paint = assertNotNull(session.surfacePaint.output.value?.paint)
            val whiskers = paint.marks[CAT_HEAD].orEmpty().filter { it.id.value.startsWith("whisker") }
            assertTrue(whiskers.isNotEmpty(), "the cat has whiskers")
            val lids = paint.eyes.flatMap { eye -> eye.look.marks[CAT_HEAD].orEmpty().filter { it.id.value.endsWith("lid") } }
            assertTrue(lids.isNotEmpty(), "the cat's eyes have lids")
            val lidPoints = lids.flatMap { it.points }.map { it.position }
            val nearest = whiskers.flatMap { it.points }.minOf { whisker -> lidPoints.minOf { (it - whisker.position).length } }
            assertTrue(nearest > CLEAR_OF_EYES, "every whisker point lies more than $CLEAR_OF_EYES from the eyes' lids, the nearest $nearest")
        }
    }
}
