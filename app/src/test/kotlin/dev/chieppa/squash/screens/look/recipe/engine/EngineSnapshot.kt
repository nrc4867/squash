package dev.chieppa.squash.screens.look.recipe.engine

import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.io.path.writeText

/** The file in a snapshot naming the Java to run it with, and the argument file that starts it. */
const val JAVA_FILE = "java.txt"
const val ARGS_FILE = "args.txt"

/** The file in a snapshot every JVM running from it holds a shared lock on, so a prune passes it by. */
const val LEASE_FILE = "lease.lock"

/** How many hex digits of the content hash name a snapshot. */
private const val VERSION_DIGITS = 16

/** The snapshots kept besides the current one; older ones are pruned once no JVM holds them. */
private const val KEPT_SNAPSHOTS = 4

/** Where a snapshot's copied classpath entries go. */
private const val LIB_FOLDER = "lib"

/** The prefix of a snapshot folder still being written, which no pointer ever names. */
private const val PARTIAL_PREFIX = ".partial-"

/**
 * The recipe engine's compiled classes, copied into a folder named by a hash of their content under [engines], and the [pointer] file
 * naming the snapshot to run. A snapshot is complete before the pointer names it and is never written again, so a build reads whole
 * classes whatever is compiled meanwhile; a JVM reads the pointer once, at its start. Entries under [immutable] folders (Gradle's
 * caches, whose files are named by their content) are used in place rather than copied.
 */
class EngineSnapshot(private val engines: Path, private val pointer: Path, private val immutable: List<Path> = emptyList()) {
    /** The snapshot the pointer names, or null before the first. */
    fun current(): String? = pointer.takeIf { it.isRegularFile() }?.readText()?.trim()?.ifEmpty { null }

    /** The folder of snapshot [version]. */
    fun folder(version: String): Path = engines.resolve(version)

    /**
     * Snapshots [classpath] and points at it: the snapshot's version, the same for the same classes, whose timestamps it ignores. [launch]
     * gives the argument file's lines for a snapshot from its folder and its classpath; [java] is the Java that runs it.
     */
    fun take(classpath: List<Path>, java: String, launch: (folder: Path, classpath: List<Path>) -> List<String>): String {
        Files.createDirectories(engines)
        val partial = Files.createTempDirectory(engines, PARTIAL_PREFIX)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(java)
        val copied = classpath.mapIndexed { i, entry ->
            digest.update("entry $i")
            if (immutable.any { entry.startsWith(it) } || !entry.exists()) {
                digest.update(entry.invariantSeparatorsPathString)
                entry
            } else {
                val name = "$i-${entry.name}"
                copyHashed(entry, partial.resolve(LIB_FOLDER).resolve(name), digest)
                Path.of(LIB_FOLDER, name)
            }
        }
        val version = digest.digest().joinToString("") { "%02x".format(it) }.take(VERSION_DIGITS)
        val target = folder(version)
        if (target.resolve(ARGS_FILE).isRegularFile()) {
            deleteTree(partial)
        } else {
            val entries = copied.map { if (it.isAbsolute) it else target.resolve(it) }
            partial.resolve(JAVA_FILE).writeText(java + "\n")
            partial.resolve(ARGS_FILE).writeText(launch(target, entries).joinToString("\n") + "\n")
            Files.createFile(partial.resolve(LEASE_FILE))
            if (target.exists()) deleteTree(target)
            Files.move(partial, target, ATOMIC_MOVE)
        }
        point(version)
        return version
    }

    /** Points at [version], replacing the pointer whole so a reader sees the old name or the new one. */
    fun point(version: String) {
        Files.createDirectories(pointer.parent)
        val next = pointer.resolveSibling(pointer.name + ".next")
        next.writeText(version + "\n")
        Files.move(next, pointer, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    /**
     * Deletes the snapshots beyond the newest [kept] and the current one, and any left partial, but none a JVM still holds a lease on.
     * The versions deleted.
     */
    fun prune(kept: Int = KEPT_SNAPSHOTS): List<String> {
        if (!engines.isDirectory()) return emptyList()
        val current = current()
        val entries = engines.listDirectoryEntries().filter { it.isDirectory() }
        entries.filter { it.name.startsWith(PARTIAL_PREFIX) }.forEach(::deleteTree)
        val snapshots = entries.filter { !it.name.startsWith(PARTIAL_PREFIX) && it.name != current }.sortedByDescending { it.getLastModifiedTime() }
        return snapshots.drop(kept).filter(::deleteUnleased).map { it.name }
    }

    /** Deletes [snapshot] when no JVM holds its lease; whether it did. */
    private fun deleteUnleased(snapshot: Path): Boolean {
        val lease = snapshot.resolve(LEASE_FILE)
        if (!lease.exists()) return deleteTree(snapshot).let { true }
        val free = FileChannel.open(lease, READ, WRITE, CREATE).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } ?: return@use false
            lock.use { snapshot.listDirectoryEntries().filter { it.name != LEASE_FILE }.forEach(::deleteTree) }
            true
        }
        if (free) deleteTree(snapshot)
        return free
    }

    private fun copyHashed(from: Path, to: Path, digest: MessageDigest) {
        Files.createDirectories(to.parent)
        if (from.isDirectory()) {
            val files = from.walk().filter { it.isRegularFile() }.sortedBy { from.relativize(it).invariantSeparatorsPathString }.toList()
            for (file in files) {
                val relative = from.relativize(file).invariantSeparatorsPathString
                val copy = to.resolve(relative)
                Files.createDirectories(copy.parent)
                Files.copy(file, copy)
                digest.update("file $relative")
                Files.newInputStream(file).use { digest.feed(it) }
            }
            Files.createDirectories(to)
        } else {
            Files.copy(from, to)
            if (from.name.endsWith(".jar")) hashJar(from, digest) else Files.newInputStream(from).use { digest.feed(it) }
        }
    }

    /** A jar's entries by name and content, so a jar packed again from the same classes hashes the same. */
    private fun hashJar(jar: Path, digest: MessageDigest) = ZipFile(jar.toFile()).use { zip ->
        zip.entries().asSequence().filter { !it.isDirectory }.sortedBy { it.name }.forEach { entry ->
            digest.update("jar ${entry.name}")
            zip.getInputStream(entry).use { digest.feed(it) }
        }
    }
}

/** Deletes [path] and everything under it, leaving what cannot be deleted (a file another process holds open). */
internal fun deleteTree(path: Path) {
    if (!path.exists()) return
    path.toFile().walkBottomUp().forEach { it.delete() }
}

private fun MessageDigest.update(text: String) = update((text + "\u0000").toByteArray())

private fun MessageDigest.feed(stream: InputStream) {
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val read = stream.read(buffer)
        if (read < 0) return
        update(buffer, 0, read)
    }
}
