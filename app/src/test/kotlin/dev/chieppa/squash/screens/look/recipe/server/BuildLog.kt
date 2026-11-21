package dev.chieppa.squash.screens.look.recipe.server

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream

/** Which stream a log line was printed to. */
enum class LogStream(val key: String) { OUT("out"), ERR("err") }

/**
 * A build's log, line by line as it is printed: each line kept, and sent to [sent] while the log is open. [close] stops the sending, so a
 * build that outlives its request prints into the log alone.
 */
class BuildLog(private val sent: (LogStream, String) -> Unit = { _, _ -> }) {
    private val lines = mutableListOf<Pair<LogStream, String>>()
    private var open = true

    /** A print stream that adds every line printed to it to the log as [stream]. */
    fun stream(stream: LogStream): PrintStream = PrintStream(Lines { add(stream, it) }, true, Charsets.UTF_8)

    @Synchronized
    fun add(stream: LogStream, line: String) {
        lines += stream to line
        if (open) runCatching { sent(stream, line) }.onFailure { open = false }
    }

    /** Runs [send], another message to where the lines go, while the log is open; a failure closes it as a failed line does. */
    @Synchronized
    fun alongside(send: () -> Unit) {
        if (open) runCatching(send).onFailure { open = false }
    }

    @Synchronized
    fun close() {
        open = false
    }

    /** Whether lines are still sent: the log is not closed and no send failed. */
    val isOpen: Boolean
        @Synchronized get() = open

    /** How many lines the log holds. */
    val size: Int
        @Synchronized get() = lines.size

    @Synchronized
    fun lines(): List<Pair<LogStream, String>> = lines.toList()

    /**
     * The files the build printed it wrote, from line [from] on, that are there now: a line's path, past a label such as `character: ` and
     * before a timing.
     */
    @Synchronized
    fun files(from: Int = 0): List<String> =
        lines.drop(from).filter { it.first == LogStream.OUT }.map { printedPath(it.second) }.filter { it.isNotEmpty() && File(it).isFile }.distinct()

    /** Bytes cut into UTF-8 lines at each newline, a carriage return before it dropped. */
    private class Lines(private val line: (String) -> Unit) : OutputStream() {
        private val pending = ByteArrayOutputStream()

        override fun write(b: Int) {
            if (b == '\n'.code) {
                line(pending.toString(Charsets.UTF_8).removeSuffix("\r"))
                pending.reset()
            } else {
                pending.write(b)
            }
        }

        override fun close() {
            if (pending.size() > 0) write('\n'.code)
        }
    }
}

private val LABEL = Regex("^[a-z][a-z ]*: ")
private val TIMING = Regex(" \\(\\d+ ms\\)$")

/** The path a printed line names, without its label and timing. */
internal fun printedPath(line: String): String = line.trim().replace(LABEL, "").replace(TIMING, "")
