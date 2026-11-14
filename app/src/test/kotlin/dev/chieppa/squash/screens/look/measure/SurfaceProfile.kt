package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.surface.SurfaceBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.TimeSource

/** Builds run before a build is profiled. */
private const val WARM_BUILDS = 3

/**
 * Where a one-surface build's time goes, batch by batch: what each batch's pieces took together, how long the batch took on all the
 * machine's threads, how busy that kept them, and how long the work between batches took on one; a whole build of [rest], and the build
 * after the edit from [rest] to [edited].
 */
object SurfaceProfile {
    fun run(name: String, rest: AssembledCharacter, edited: AssembledCharacter) {
        val threads = Runtime.getRuntime().availableProcessors()
        val pool = Executors.newFixedThreadPool(threads)
        val runAll: (List<() -> Unit>) -> Unit = { tasks -> tasks.map { pool.submit(it) }.forEach { it.get() } }
        repeat(WARM_BUILDS) { SurfaceBuilder().build(rest, runAll = runAll) }
        println("$name profile: $threads threads")
        profiled("$name whole", SurfaceBuilder(), rest, pool, threads)
        val builder = SurfaceBuilder()
        builder.build(rest, runAll = runAll)
        repeat(WARM_BUILDS) {
            builder.build(edited, runAll = runAll)
            builder.build(rest, runAll = runAll)
        }
        profiled("$name edit", builder, edited, pool, threads)
        pool.shutdown()
    }

    private fun ms(t: TimeSource.Monotonic.ValueTimeMark) = t.elapsedNow().inWholeMicroseconds / 1000.0

    private fun profiled(label: String, builder: SurfaceBuilder, character: AssembledCharacter, pool: ExecutorService, threads: Int) {
        val all = TimeSource.Monotonic.markNow()
        var t = TimeSource.Monotonic.markNow()
        val build = checkNotNull(builder.start(character, false))
        println("$label: field %.1f ms".format(ms(t)))
        var batch = 0
        while (true) {
            t = TimeSource.Monotonic.markNow()
            val tasks = build.next()
            val between = ms(t)
            if (tasks == null) {
                println("$label: last step %.1f ms".format(between))
                break
            }
            val work = AtomicLong()
            val timed = tasks.map { task ->
                {
                    val start = System.nanoTime()
                    task()
                    work.addAndGet(System.nanoTime() - start)
                    Unit
                }
            }
            t = TimeSource.Monotonic.markNow()
            timed.map { pool.submit(it) }.forEach { it.get() }
            val wall = ms(t)
            val busy = 100.0 * work.get() / 1e6 / (wall * threads)
            println("$label: before batch $batch %.1f ms; batch $batch ${tasks.size} pieces, %.1f ms, work %.1f ms, threads busy %.0f%%".format(between, wall, work.get() / 1e6, busy))
            batch++
        }
        println("$label: all %.1f ms, ${build.surface.mesh.vertexCount} vertices".format(ms(all)))
    }
}
