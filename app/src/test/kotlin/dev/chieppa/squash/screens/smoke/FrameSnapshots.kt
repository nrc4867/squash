package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.GpuSnapshots
import kotlinx.coroutines.runBlocking

/**
 * Where scene frames render to images: inside [keep], on one device and scene kept for every image, which a run of many images pays for
 * once; outside it, on a device of each image's own. Threads share the kept device one image at a time, each image's errors its own.
 */
object FrameSnapshots {
    @Volatile
    private var kept: GpuSnapshots<SceneFrame>? = null
    @Volatile
    private var errors: MutableList<String>? = null

    /** Runs [block] with every frame rendered on one device, opened now and closed after it. */
    fun <T> keep(block: () -> T): T {
        if (kept != null) return block()
        val opened = runBlocking { GpuSnapshots.open(GpuOptions(onError = { message -> errors?.let { synchronized(it) { it.add(message) } } })) { ViewsScene(it) } }
        kept = opened
        try {
            return block()
        } finally {
            kept = null
            synchronized(this) { opened.close() }
        }
    }

    /** [frame] rendered into a [width] by [height] image, its WebGPU errors added to [errors]. */
    fun render(frame: SceneFrame, width: Int, height: Int, errors: MutableList<String>): GpuImage {
        val snapshots = kept ?: return renderSnapshot(frame, width, height, GpuOptions(onError = { errors += it })) { ViewsScene(it) }
        synchronized(this) {
            this.errors = errors
            try {
                return runBlocking { snapshots.render(frame, width, height) }
            } finally {
                this.errors = null
            }
        }
    }
}
