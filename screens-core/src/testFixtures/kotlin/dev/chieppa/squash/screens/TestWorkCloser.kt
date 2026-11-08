package dev.chieppa.squash.screens

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Closes every [TestWork] made during a test once the test ends, whether it is a field, a local or a bench's. JUnit finds it
 * through extension autodetection, which the build turns on for every test task, so no test registers it.
 */
class TestWorkCloser : AfterEachCallback {
    override fun afterEach(context: ExtensionContext) = closeAll()

    companion object {
        private val open = mutableListOf<TestWork>()

        internal fun opened(work: TestWork) = synchronized(open) { open += work }

        /** Closes and forgets every [TestWork] still open. */
        fun closeAll() {
            val closing = synchronized(open) { open.toList().also { open.clear() } }
            closing.forEach(TestWork::close)
        }
    }
}
