package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sets the lock group [id] to [group], adding it after the others when new, or restored to [index] when undoing a removal; null removes
 * it. The head it grows on, and its mass where it names one, exist.
 */
@Serializable
@SerialName("setLockGroup")
data class SetLockGroup(val id: LockGroupId, val group: LockGroup?, val index: Int? = null) : DocumentCommand {
    init {
        require(group == null || group.id == id) { "Lock group ${group?.id?.value} given for ${id.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val groups = document.character.lockGroups
        val at = groups.indexOfFirst { it.id == id }
        val old = groups.getOrNull(at)
        group?.let {
            document.requirePart(it.on)
            it.mass?.let(document::requirePart)
        }
        val now = when {
            group == null -> groups.filterNot { it.id == id }
            old == null -> (index ?: groups.size).coerceIn(0, groups.size).let { groups.take(it) + group + groups.drop(it) }
            else -> groups.map { if (it.id == id) group else it }
        }
        val undo = if (old == null) SetLockGroup(id, null) else SetLockGroup(id, old, at)
        return Applied(document.copy(character = document.character.copy(lockGroups = now)), undo)
    }
}

/** Sets the lock at [index] of group [group] to [override], or returns it to the group with null. */
@Serializable
@SerialName("setLockOverride")
data class SetLockOverride(val group: LockGroupId, val index: Int, val override: LockOverride?) : DocumentCommand {
    init {
        require(override == null || override.index == index) { "Override for lock ${override?.index} given for $index" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val held = requireNotNull(document.character.lockGroup(group)) { "No lock group ${group.value}" }
        require(index < held.count) { "Lock group ${group.value} holds ${held.count} locks, not one at $index" }
        val old = held.overrideOf(index)
        val now = held.withOverride(index, override)
        val groups = document.character.lockGroups.map { if (it.id == group) now else it }
        return Applied(document.copy(character = document.character.copy(lockGroups = groups)), SetLockOverride(group, index, old))
    }
}

/** Sets the character's collision shapes to [colliders]; every part they ride exists. */
@Serializable
@SerialName("setColliders")
data class SetColliders(val colliders: List<Collider>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        colliders.forEach { document.requirePart(it.part) }
        val old = document.character.colliders
        return Applied(document.copy(character = document.character.copy(colliders = colliders)), SetColliders(old))
    }
}
