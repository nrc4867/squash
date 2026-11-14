package dev.chieppa.squash.screens.look.measure

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.SetParameter
import dev.chieppa.squash.screens.TestWork
import kotlin.time.measureTime

/** How much larger every other state makes the edited part. */
private const val EDIT_STEP = 0.02f

/** How much larger each state makes the part than the one two before it, so no two states are alike. */
private const val DRIFT = 0.0005f

/**
 * How many sizes an edit is timed at, in turn: more than any build keeps by its inputs, so each run builds a size none of the builds it
 * keeps were built at.
 */
const val EDIT_STATES = 8

/** Builds run before timing starts. */
const val EDIT_WARM_RUNS = 5

/** Runs timed once warm. */
const val EDIT_TIMED_RUNS = 21

/**
 * The placed character with [edit]'s part at [count] sizes, each about 2% from the one before and none alike, as a drag reaches a new
 * size each step, the first also 2% from the last; the session is left at rest.
 */
fun editStates(session: CharacterSession, work: TestWork, edit: SurfaceEdit, count: Int): List<AssembledCharacter> {
    val states = List(count) { i ->
        session.send(SetParameter(edit.part, edit.parameter, edit.rest * (1f + EDIT_STEP * (i % 2) + DRIFT * i)))
        work.settle(session)
        checkNotNull(session.placed.output.value)
    }
    session.send(SetParameter(edit.part, edit.parameter, edit.rest))
    work.settle(session)
    return states
}

/** The median, 90th percentile and worst of timed runs, in ms. */
class RunStats(runs: List<Double>) {
    private val sorted = runs.sorted()
    val median: Double = sorted[sorted.size / 2]
    val p90: Double = sorted[(sorted.size * 9 + 9) / 10 - 1]
    val worst: Double = sorted.last()

    override fun toString() = "%.1f ms (90th %.1f, worst %.1f)".format(median, p90, worst)
}

/** [run] given each run's index, [EDIT_WARM_RUNS] times untimed and then [EDIT_TIMED_RUNS] times timed, each after [before] untimed. */
fun timedRuns(before: (Int) -> Unit = {}, run: (Int) -> Unit): RunStats {
    repeat(EDIT_WARM_RUNS) {
        before(it)
        run(it)
    }
    return RunStats(List(EDIT_TIMED_RUNS) { i -> before(EDIT_WARM_RUNS + i); measureTime { run(EDIT_WARM_RUNS + i) }.inWholeMicroseconds / 1000.0 })
}
