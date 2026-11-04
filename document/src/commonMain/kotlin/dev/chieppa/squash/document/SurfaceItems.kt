package dev.chieppa.squash.document

/** Where the item [matches] finds sits in this list and the item, or -1 and null if it holds none. */
internal fun <T> List<T>.locate(matches: (T) -> Boolean): IndexedValue<T?> {
    val at = indexOfFirst(matches)
    return IndexedValue(at, getOrNull(at))
}

/** The index that places a located item back, or null if none was held. */
internal val <T> IndexedValue<T?>.restoreIndex: Int? get() = index.takeIf { it >= 0 }

/**
 * This list with the item [matches] finds replaced in place by [item], or removed when [item] is null. An item it does not
 * hold goes in at [index], or last.
 */
internal fun <T> List<T>.withItem(item: T?, index: Int?, matches: (T) -> Boolean): List<T> {
    val at = indexOfFirst(matches)
    return when {
        at >= 0 && item != null -> toMutableList().also { it[at] = item }
        at >= 0 -> take(at) + drop(at + 1)
        item == null -> this
        else -> (index ?: size).let { take(it) + item + drop(it) }
    }
}
