package dev.chieppa.squash.screens.look.recipe.engine

import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import kotlin.io.path.exists

/** The system property naming the snapshot folder a JVM runs from, which its argument file sets. */
const val ENGINE_PROPERTY = "squash.engine"

/** The system property naming the pointer file of the checkout the snapshot was taken in. */
const val POINTER_PROPERTY = "squash.engine.pointer"

/**
 * A shared lock on the lease file of the snapshot this JVM runs from, held until the process ends, so [EngineSnapshot.prune] never
 * deletes classes a running build still loads.
 */
object EngineLease {
    private var held: FileLock? = null

    /** The snapshot folder this JVM runs from, or null when it runs from Gradle's own classpath. */
    val folder: Path? = System.getProperty(ENGINE_PROPERTY)?.let(Path::of)

    /** This JVM's engine version, the snapshot's name, or null outside a snapshot. */
    val version: String? = folder?.fileName?.toString()

    /** Takes the lease, once. */
    @Synchronized
    fun hold() {
        if (held != null) return
        val lease = folder?.resolve(LEASE_FILE)?.takeIf { it.exists() } ?: return
        held = FileChannel.open(lease, READ).lock(0, Long.MAX_VALUE, true)
    }
}
