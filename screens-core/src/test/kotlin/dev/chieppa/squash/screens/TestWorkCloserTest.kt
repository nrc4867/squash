package dev.chieppa.squash.screens

import java.util.ServiceLoader
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.jupiter.api.extension.Extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every TestWork is closed when its test ends, by an extension JUnit finds on its own. */
class TestWorkCloserTest {
    @Test
    fun junitFindsTheCloserForEveryTest() {
        assertEquals("true", System.getProperty("junit.jupiter.extensions.autodetection.enabled"))
        assertTrue(ServiceLoader.load(Extension::class.java).any { it is TestWorkCloser })
    }

    @Test
    fun closingCancelsEverythingTheWorkStarted() {
        val first = TestWork()
        val second = TestWork()
        val jobs = listOf(first, second).map { work -> work.scope.launch { awaitCancellation() }.also { work.drain() } }
        assertTrue(jobs.all { it.isActive })
        TestWorkCloser.closeAll()
        assertTrue(jobs.all { it.isCancelled }, "both are closed, whoever made them")
    }
}
