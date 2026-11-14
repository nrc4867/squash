package dev.chieppa.squash.character.surface

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Runs a batch of work whose pieces are independent of one another, returning once every piece is done. */
typealias RunAll = suspend (List<() -> Unit>) -> Unit

/** Runs each piece of a batch in turn, here. */
val IN_TURN: RunAll = { tasks -> tasks.forEach { it() } }

/** Runs every piece of a batch at once on the calling coroutine's dispatcher. */
val IN_PARALLEL: RunAll = { tasks -> coroutineScope { tasks.map { async { it() } }.awaitAll() } }

/** Pieces of work over [total] items, [chunk] a piece, each given the range from and until it covers. */
fun chunked(total: Int, chunk: Int, work: (Int, Int) -> Unit): List<() -> Unit> =
    (0 until total step chunk).map { from -> { work(from, minOf(total, from + chunk)) } }
