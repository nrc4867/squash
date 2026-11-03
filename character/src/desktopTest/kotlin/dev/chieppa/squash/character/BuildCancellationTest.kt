package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.PartBuild
import dev.chieppa.squash.character.build.PartBuilder
import dev.chieppa.squash.character.build.partMeshNode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.graph.Graph
import dev.chieppa.squash.graph.cancellationCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuildCancellationTest {
    private val paw = template("paw")
    private val part = PartId("paw-1")

    @Test
    fun aCancelledRunStopsAtTheNextBlock() = runTest {
        var checks = 0
        var finished = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            val check = cancellationCheck()
            val job = coroutineContext.job
            PartBuilder.build(
                paw,
                checkCancelled = {
                    checks++
                    if (checks == 2) job.cancel()
                    check()
                },
            )
            finished = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(2, checks, "nothing runs past the check that saw the cancellation")
        assertTrue(!finished)
    }

    @Test
    fun aBuildChecksBeforeEveryBlockAndEveryToe() {
        var checks = 0
        PartBuilder.build(paw, checkCancelled = { checks++ })
        val blocks = paw.blocks.size
        val toes = 4
        val toePads = 4
        assertEquals(blocks + toes + toePads, checks)
    }

    @Test
    fun theMeshChecksBeforeEveryPatchAndStops() {
        val patches = PartBuilder.build(paw)
        var checks = 0
        assertFailsWith<CancellationException> {
            PartBuilder.mesh(
                "paw",
                paw,
                patches,
                checkCancelled = {
                    checks++
                    if (checks == 3) throw CancellationException("stale")
                },
            )
        }
        assertEquals(3, checks)
        checks = 0
        PartBuilder.mesh("paw", paw, patches, checkCancelled = { checks++ })
        assertEquals(patches.size, checks)
    }

    @Test
    fun theTemplateIsAnInputOfTheMeshNode() = runTest {
        val graph = Graph(backgroundScope, StandardTestDispatcher(testScheduler))
        val patches = PartBuilder.build(paw)
        val rejointed = paw.copy(blocks = paw.blocks.filter { it.joints().isEmpty() })
        val builds = MutableStateFlow(PartBuild(paw, patches))
        val node = graph.partMeshNode(graph.node(builds) { it }, part)
        runCurrent()
        val first = assertNotNull(node.output.value)

        builds.value = PartBuild(rejointed, patches)
        runCurrent()
        val second = assertNotNull(node.output.value)
        assertNotEquals(first, second)
        assertEquals(PartBuilder.mesh(part.value, rejointed, patches).patches.first().bones.toList(), second.patches.first().bones.toList())

        builds.value = PartBuild(paw, patches)
        runCurrent()
        assertSame(first, node.output.value, "the first template's mesh is still cached under its own input")
    }
}
