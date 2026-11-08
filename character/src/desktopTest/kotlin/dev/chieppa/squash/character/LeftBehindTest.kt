package dev.chieppa.squash.character

import dev.chieppa.squash.character.catalog.newCharacter
import dev.chieppa.squash.character.catalog.newerThan
import dev.chieppa.squash.character.catalog.posedJointsMissing
import dev.chieppa.squash.character.catalog.template
import dev.chieppa.squash.character.catalog.unseatedParts
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.document.UpdateFromLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val HEAD = PartId("head")
private val EAR = PartId("ear")
private const val NEWER = 3

/** What Update from library finds newer, and what a version leaves behind: parts on a socket it lacks, posed joints it lacks. See HLD: data-model, Update from library. */
class LeftBehindTest {
    private val plan = shipped.plans.single { it.id == BodyPlanId("canine-quadruped") }
    private val head = assertNotNull(shipped.template(TemplateId("head")))

    /** A head version that drops the ear socket and the neck joint, which no shipped version would. */
    private val earless = head.copy(version = NEWER, blocks = head.blocks.filter { block -> block.sockets().none { it.name == "ear" } && block.joints().none { it.name == "neck" } })
    private val library = shipped.copy(parts = shipped.parts + earless)

    private val fennec: SquashDocument = run {
        val new = newCharacter(plan, shipped, "Fennec")
        val rooted = new.commands.fold(new.document) { document, command -> command.apply(document).document }
        val ear = assertNotNull(shipped.template(TemplateId("ear"))).ref
        AddParts(listOf(PartInstance(HEAD, head.ref, SocketRef(new.root, "neck")), PartInstance(EAR, ear, SocketRef(HEAD, "ear")))).apply(rooted).document
    }

    @Test
    fun theNewestVersionIsOfferedOnlyOverAnOlderOne() {
        assertEquals(NEWER, library.newerThan(head.ref)?.version)
        assertNull(shipped.newerThan(head.ref), "the shipped head is the newest")
        assertEquals(head, shipped.newerThan(TemplateRef(head.id, 1)))
    }

    @Test
    fun aPartOnASocketTheVersionLacksIsUnseatedUntilUndone() {
        assertEquals(emptySet(), unseatedParts(fennec, plan, library))
        val applied = UpdateFromLibrary(HEAD, NEWER).apply(fennec)
        assertEquals(setOf(EAR), unseatedParts(applied.document, plan, library))
        assertEquals(SocketRef(HEAD, "ear"), applied.document.character.part(EAR)?.socket, "the ear keeps its socket")
        assertEquals(emptySet(), unseatedParts(applied.undo.apply(applied.document).document, plan, library))
    }

    @Test
    fun aPosedJointTheVersionLacksIsListed() {
        val pose = Pose(listOf(BonePose(HEAD, "neck", degrees = 20f)))
        assertEquals(emptyList(), posedJointsMissing(pose, HEAD, head))
        assertEquals(listOf("neck"), posedJointsMissing(pose, HEAD, earless))
    }
}
