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

/**
 * Sets the scalp flow over head [on] to [flow], adding it after the others when new, or restored to [index] when undoing a removal; null
 * returns the head to the default flow. The head exists.
 */
@Serializable
@SerialName("setScalpFlow")
data class SetScalpFlow(val on: PartId, val flow: ScalpFlow?, val index: Int? = null) : DocumentCommand {
    init {
        require(flow == null || flow.on == on) { "Scalp flow on ${flow?.on?.value} given for ${on.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        if (flow != null) document.requirePart(on)
        val flows = document.character.scalpFlows
        val at = flows.indexOfFirst { it.on == on }
        val old = flows.getOrNull(at)
        val now = when {
            flow == null -> flows.filterNot { it.on == on }
            old == null -> (index ?: flows.size).coerceIn(0, flows.size).let { flows.take(it) + flow + flows.drop(it) }
            else -> flows.map { if (it.on == on) flow else it }
        }
        val undo = if (old == null) SetScalpFlow(on, null) else SetScalpFlow(on, old, at)
        return Applied(document.copy(character = document.character.copy(scalpFlows = now)), undo)
    }
}

/**
 * Sets the hair outline on head [on] to [outline], adding it after the others when new, or restored to [index] when undoing a removal;
 * null removes it. The head exists.
 */
@Serializable
@SerialName("setHairOutline")
data class SetHairOutline(val on: PartId, val outline: HairOutline?, val index: Int? = null) : DocumentCommand {
    init {
        require(outline == null || outline.on == on) { "Hair outline on ${outline?.on?.value} given for ${on.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        if (outline != null) document.requirePart(on)
        val outlines = document.character.hairOutlines
        val at = outlines.indexOfFirst { it.on == on }
        val old = outlines.getOrNull(at)
        val now = when {
            outline == null -> outlines.filterNot { it.on == on }
            old == null -> (index ?: outlines.size).coerceIn(0, outlines.size).let { outlines.take(it) + outline + outlines.drop(it) }
            else -> outlines.map { if (it.on == on) outline else it }
        }
        val undo = if (old == null) SetHairOutline(on, null) else SetHairOutline(on, old, at)
        return Applied(document.copy(character = document.character.copy(hairOutlines = now)), undo)
    }
}

/**
 * Sets the short-hair shell over head [on] to [shell], adding it after the others when new, or restored to [index] when undoing a
 * removal; null removes it. The head exists.
 */
@Serializable
@SerialName("setHairShell")
data class SetHairShell(val on: PartId, val shell: HairShell?, val index: Int? = null) : DocumentCommand {
    init {
        require(shell == null || shell.on == on) { "Hair shell on ${shell?.on?.value} given for ${on.value}" }
    }

    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        if (shell != null) document.requirePart(on)
        val shells = document.character.hairShells
        val at = shells.indexOfFirst { it.on == on }
        val old = shells.getOrNull(at)
        val now = when {
            shell == null -> shells.filterNot { it.on == on }
            old == null -> (index ?: shells.size).coerceIn(0, shells.size).let { shells.take(it) + shell + shells.drop(it) }
            else -> shells.map { if (it.on == on) shell else it }
        }
        val undo = if (old == null) SetHairShell(on, null) else SetHairShell(on, old, at)
        return Applied(document.copy(character = document.character.copy(hairShells = now)), undo)
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
