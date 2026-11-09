package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.character.build.AssemblyPart
import dev.chieppa.squash.character.build.Dressing
import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.load.TemplateFolder
import dev.chieppa.squash.character.build.assembleNow
import dev.chieppa.squash.character.build.dressedIn
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartData
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.TemplateId
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.measureTime
import org.junit.jupiter.api.Tag

private const val ROUNDS = 10

/**
 * What dressing a small fennec body (torso, head, ears, front legs) costs: a sweater with its sleeves filleted into its body and a cap
 * with the ears through it, against the same without the sleeves. Numbers go to the test's output.
 */
@Tag("slow")
class GarmentFilletBenchTest {
    private val folder = TemplateFolder.shipped()
    private val library = TemplateLibrary(folder.parts, folder.plans)
    private val torso = PartId("torso")
    private val head = PartId("head")
    private val earL = PartId("ear-l")
    private val earR = PartId("ear-r")
    private val legL = PartId("leg-l")
    private val legR = PartId("leg-r")
    private val twins = mapOf(earL to earR, earR to earL, legL to legR, legR to legL)

    private val body by lazy {
        val plan = folder.plans.single { it.id.value == "canine-quadruped" }
        fun ref(id: String) = library.latest(TemplateId(id))!!.ref
        assembleNow(
            listOf(
                AssemblyPart(torso, ref("torso"), null, null, null),
                AssemblyPart(head, ref("head"), SocketRef(torso, "neck"), null, null),
                AssemblyPart(earL, ref("ear"), SocketRef(head, "ear"), PairId("ears"), Side.LEFT),
                AssemblyPart(earR, ref("ear"), SocketRef(head, "ear"), PairId("ears"), Side.RIGHT),
                AssemblyPart(legL, ref("leg"), SocketRef(torso, "front-leg"), PairId("legs"), Side.LEFT),
                AssemblyPart(legR, ref("leg"), SocketRef(torso, "front-leg"), PairId("legs"), Side.RIGHT),
            ),
            { PartData() }, plan, library,
        )
    }

    private val cap = Garment(GarmentId("cap"), "Cap", branches = listOf(GarmentBranch(head, 0f, 0.45f, fromV = -0.22f, toV = 0.22f)))
    private val sweater = Garment(GarmentId("sweater"), "Sweater", branches = listOf(GarmentBranch(torso, 0.4f, 0.97f)))
    private val sleeved = sweater.copy(branches = sweater.branches + GarmentBranch(legL, 0f, 0.6f))

    private fun average(block: () -> Unit): Duration {
        repeat(2) { block() }
        return (0 until ROUNDS).map { measureTime(block) }.reduce(Duration::plus) / ROUNDS
    }

    @Test
    fun theSmallFennec() {
        val withSleeves = average { body.dressedIn(Dressing(listOf(sleeved, cap), twins)) }
        val without = average { body.dressedIn(Dressing(listOf(sweater, cap), twins)) }
        println("small fennec dressed: sweater with sleeves and cap $withSleeves, without sleeves $without")
    }
}
