package dev.chieppa.squash.character

import dev.chieppa.squash.character.block.DigitTips
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.assemblyParts
import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.openSockets
import dev.chieppa.squash.character.catalog.placeOnRow
import dev.chieppa.squash.character.catalog.placeOnSocket
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.lint.TemplateLint
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TOES = 3

/** How near its toe's tip a claw's socket plane sits at most, as a share of the claw's length. */
private const val AT_THE_TIP = 0.5f

/** A toe row's tip sockets: one on every built digit, where the digit ends, filled with claws in one step. */
class DigitTipsTest {
    private val plan = shipped.plans.first()
    private val library = TemplateLibrary(shipped.parts, shipped.plans)
    private val claw = assertNotNull(shipped.template(TemplateId("claw")))
    private val paw = assertNotNull(shipped.template(TemplateId("paw")))

    /** A torso with a front leg and paw on it, the paw at [version] with [toes] toes; the paw's id comes second. */
    private fun pawed(version: Int = paw.version, toes: Int = TOES): Pair<SquashDocument, PartId> {
        val new = newCharacter(plan, shipped, "Dragon")
        var document = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val leg = openSockets(document, plan, shipped).first { it.name == "front-leg" }
        document = AddParts(placeOnSocket(leg, assertNotNull(shipped.template(TemplateId("leg"))), document) { "leg-${next()}" }).apply(document).document
        val socket = openSockets(document, plan, shipped).first { it.name == "paw" }
        val pawAt = assertNotNull(shipped.parts.firstOrNull { it.ref == TemplateRef(TemplateId("paw"), version) })
        val placed = placeOnSocket(socket, pawAt, document) { "paw-${next()}" }
        document = AddParts(placed).apply(document).document
        val left = placed.first().id
        return SetParameter(left, "toes", toes.toFloat()).apply(document).document to left
    }

    private var made = 0
    private fun next() = made++

    @Test
    fun theClawIsATaperingHornAndThePawsTipsAreNew() {
        assertEquals(emptyList(), TemplateLint.lint(claw))
        assertEquals(Role.HORN, claw.role)
        assertEquals(4, paw.version)
        assertTrue(shipped.parts.any { it.ref == TemplateRef(TemplateId("paw"), 3) }, "the paw's earlier versions keep shipping")
    }

    @Test
    fun everyToeOffersATipAndEarlierPawsNone() {
        val (document, left) = pawed()
        val tips = openSockets(document, plan, shipped).filter { DigitTips.isTip(it.name) }
        assertEquals((0 until TOES).map { DigitTips.name("toes:$it") }, tips.map { it.name })
        assertTrue(tips.all { it.parent == left && it.paired && claw.id in it.takes })
        val (older, _) = pawed(version = 3)
        assertEquals(emptyList(), openSockets(older, plan, shipped).filter { DigitTips.isTip(it.name) })
        val (toeless, _) = pawed(toes = 0)
        assertEquals(emptyList(), openSockets(toeless, plan, shipped).filter { DigitTips.isTip(it.name) })
    }

    @Test
    fun aTipLiesAtTheEndOfItsToe() {
        val (document, left) = pawed()
        val character = document.character
        val assembled = assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library)
        val placed = assertNotNull(assembled.part(left))
        for (i in 0 until TOES) {
            val toe = placed.surface.patches.first { it.name == "toes:$i" }
            val dot = assembled.sockets.first { it.ref.parent == left && it.ref.socket == DigitTips.name("toes:$i") && it.side != Side.RIGHT }
            assertEquals(assertNotNull(placed.pointOn(toe.ringUs.last(), 0f, toe.name)), dot.position)
        }
    }

    @Test
    fun addPartOnOneTipPutsAClawOnEveryToeInOneUndoableStep() {
        val (document, left) = pawed()
        val open = openSockets(document, plan, shipped)
        val tips = open.filter { DigitTips.isTip(it.name) }
        val placed = placeOnRow(tips[1], open, claw, document) { "claw-${next()}" }
        assertEquals(TOES, placed.size)
        val parts = placed.flatMap { it.first }
        assertEquals(TOES * 2, parts.size, "a claw on each side of the pair of paws")
        val applied = AddParts(parts).apply(document)
        assertEquals(emptyList(), openSockets(applied.document, plan, shipped).filter { DigitTips.isTip(it.name) })
        assertEquals(document, applied.undo.apply(applied.document).document)
        val character = applied.document.character
        val assembled = assembleNow(assemblyParts(character), { assertNotNull(character.part(it)).let(character::resolved) }, plan, library)
        val pawPlaced = assertNotNull(assembled.part(left))
        for (part in parts.filter { it.socket?.parent == left }) {
            val digit = assertNotNull(DigitTips.indexOf(assertNotNull(part.socket).socket))
            val toe = pawPlaced.surface.patches.first { it.name == "toes:$digit" }
            val tip = assertNotNull(pawPlaced.pointOn(toe.ringUs.last(), 0f, toe.name))
            val clawAt = assertNotNull(assembled.part(part.id))
            assertEquals(assembled.part(part.id)?.anchor, tip)
            assertTrue((clawAt.origin - tip).length < AT_THE_TIP * claw.axis.length, "claw $digit sits at its toe's tip: ${(clawAt.origin - tip).length}")
        }
    }
}
