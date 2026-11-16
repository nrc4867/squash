package dev.chieppa.squash.screens.shell.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Which of Library's grids the keyboard cursor is in, and how the focus reaches each, so the search field and the grids can hand the
 * cursor on. Keyed by grid; [shown] is the grids on screen, top first, with the keys of the items each holds.
 */
@Stable
internal class GridFocus {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /** The grid the cursor was last put in, which keeps it when the same item shows in two grids. */
    var held by mutableStateOf<String?>(null)

    var shown: List<Pair<String, List<Any>>> = emptyList()

    fun of(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** The grid showing [cursor]: the one it was put in, else the first holding it that is not [skip]. */
    fun holding(cursor: Any?, skip: String? = null): String? =
        shown.firstOrNull { it.first == held && cursor in it.second }?.first ?: shown.firstOrNull { it.first != skip && cursor in it.second }?.first

    /** Puts the focus in the grid showing [cursor]; false when none shows it. */
    fun focus(cursor: Any?, skip: String? = null): Boolean {
        val key = holding(cursor, skip) ?: return false
        held = key
        of(key).requestFocus()
        return true
    }
}
