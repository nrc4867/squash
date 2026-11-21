package dev.chieppa.squash.screens.look.recipe

import java.io.OutputStream
import java.io.PrintStream

/**
 * Standard out and error routed by thread: a thread inside [into] writes to the streams it was given, every other thread to the console
 * the process started with. Several builds in one process each print only into their own streams this way. Installed the first time
 * [into] or [install] runs.
 */
object ThreadOutput {
    private val console: PrintStream = System.out
    private val consoleErrors: PrintStream = System.err
    private val outs = ThreadLocal<PrintStream?>()
    private val errors = ThreadLocal<PrintStream?>()

    @Volatile
    private var installed = false

    /** Puts the routing streams in place of [System.out] and [System.err]. */
    fun install() = synchronized(this) {
        if (installed) return
        System.setOut(PrintStream(Routed(console) { outs.get() }, true, Charsets.UTF_8))
        System.setErr(PrintStream(Routed(consoleErrors) { errors.get() }, true, Charsets.UTF_8))
        installed = true
    }

    /** Runs [block] with this thread's standard out written to [out] and its standard error to [err]. */
    fun <T> into(out: PrintStream, err: PrintStream? = errors.get(), block: () -> T): T {
        install()
        val outBefore = outs.get()
        val errBefore = errors.get()
        outs.set(out)
        errors.set(err)
        try {
            return block()
        } finally {
            System.out.flush()
            System.err.flush()
            outs.set(outBefore)
            errors.set(errBefore)
        }
    }

    /** Bytes sent on to the current thread's stream, or [fallback] when it has none. */
    private class Routed(private val fallback: PrintStream, private val current: () -> PrintStream?) : OutputStream() {
        private fun target(): PrintStream = current() ?: fallback

        override fun write(b: Int) = target().write(b)

        override fun write(b: ByteArray, off: Int, len: Int) = target().write(b, off, len)

        override fun flush() = target().flush()
    }
}
