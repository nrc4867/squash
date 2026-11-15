package dev.chieppa.squash

import dev.chieppa.squash.fieldgpu.GpuFieldReads
import dev.chieppa.squash.geom.field.CpuFieldReads
import dev.chieppa.squash.geom.field.FieldReads

private const val READS_PROPERTY = "squash.reads"
private const val GPU_READS = "gpu"

/** Where the app reads a character's fields: on the GPU when the launch sets `squash.reads` to `gpu`, otherwise on the CPU. */
internal fun launchedFieldReads(): FieldReads =
    if (System.getProperty(READS_PROPERTY) == GPU_READS) GpuFieldReads() else CpuFieldReads
