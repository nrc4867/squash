package dev.chieppa.squash.document

import dev.chieppa.stretch.core.Applied
import dev.chieppa.stretch.core.Command
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every change to a [SquashDocument]. Each command and its undo is serializable, so anything can send them. */
@Serializable
sealed interface DocumentCommand : Command<SquashDocument>

/** Adds [part], or restores it to [index] when undoing a removal. A pair it is first to name starts from [pairData], or empty. */
@Serializable
@SerialName("addPart")
data class AddPart(val part: PartInstance, val index: Int? = null, val pairData: PartData? = null) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val parts = document.character.parts
        require(parts.none { it.id == part.id }) { "Part ${part.id.value} already exists" }
        val at = index ?: parts.size
        val next = parts.take(at) + part + parts.drop(at)
        val restored = if (part.pair != null && pairData != null) mapOf(part.pair to pairData) else emptyMap()
        return Applied(document.withParts(next, restored), RemovePart(part.id))
    }
}

/** Removes [part]; its pair's data goes with the pair's last side. */
@Serializable
@SerialName("removePart")
data class RemovePart(val part: PartId) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val parts = document.character.parts
        val at = parts.indexOfFirst { it.id == part }
        require(at >= 0) { "No part ${part.value}" }
        val removed = parts[at]
        val next = document.withParts(parts - removed)
        return Applied(next, AddPart(removed, at, document.pairDataDropped(next)[removed.pair]))
    }
}

/**
 * Adds [parts] as one step, as a mirrored pair is placed, or restores them to [indices] when undoing a removal. A pair
 * they are first to name starts from [pairs], or empty.
 */
@Serializable
@SerialName("addParts")
data class AddParts(
    val parts: List<PartInstance>,
    val indices: List<Int>? = null,
    val pairs: Map<PairId, PartData> = emptyMap(),
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val next = document.character.parts.toMutableList()
        parts.forEachIndexed { i, part ->
            require(next.none { it.id == part.id }) { "Part ${part.id.value} already exists" }
            next.add(indices?.get(i) ?: next.size, part)
        }
        return Applied(document.withParts(next, pairs), RemoveParts(parts.map { it.id }))
    }
}

/** Removes [parts] as one step; a pair's data goes with the pair's last side. */
@Serializable
@SerialName("removeParts")
data class RemoveParts(val parts: List<PartId>) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val current = document.character.parts
        val indices = parts.map { id -> current.indexOfFirst { it.id == id }.also { require(it >= 0) { "No part ${id.value}" } } }
        val removed = indices.map { current[it] }
        val order = indices.indices.sortedBy { indices[it] }
        val next = document.withParts(current - removed.toSet())
        return Applied(next, AddParts(order.map { removed[it] }, order.map { indices[it] }, document.pairDataDropped(next)))
    }
}

/**
 * Sets the outline drawn in [view]: on the pair for a paired part, or with [thisSide] as that side's override alone. A
 * null [curve] clears it, and clearing an override shows the pair's outline again. An unpaired part has one place for it.
 */
@Serializable
@SerialName("setOutline")
data class SetOutline(
    val part: PartId,
    val view: View,
    val curve: OutlineCurve?,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide)
        return Applied(
            document.withWritten(instance, thisSide, before.withOutline(view, curve)),
            SetOutline(part, view, before.outlines[view], thisSide),
        )
    }
}

/**
 * Sets the [outlines] of several views as one step, where each goes as [SetOutline] would send it: on the pair for a paired
 * part, or with [thisSide] as that side's override alone. A null curve clears its view.
 */
@Serializable
@SerialName("setOutlines")
data class SetOutlines(
    val part: PartId,
    val outlines: Map<View, OutlineCurve?>,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide)
        val after = outlines.entries.fold(before) { data, (view, curve) -> data.withOutline(view, curve) }
        return Applied(
            document.withWritten(instance, thisSide, after),
            SetOutlines(part, outlines.mapValues { (view, _) -> before.outlines[view] }, thisSide),
        )
    }
}

/**
 * Sets a parameter's value: on the pair for a paired part, or with [thisSide] as that side's override alone. A null
 * [value] returns it to the template's default, and clearing an override returns it to the pair's value. An unpaired
 * part has one place for it.
 */
@Serializable
@SerialName("setParameter")
data class SetParameter(
    val part: PartId,
    val name: String,
    val value: Float?,
    val thisSide: Boolean = false,
) : DocumentCommand {
    override fun apply(document: SquashDocument): Applied<SquashDocument> {
        val instance = document.requirePart(part)
        val before = document.writtenData(instance, thisSide)
        return Applied(
            document.withWritten(instance, thisSide, before.withParam(name, value)),
            SetParameter(part, name, before.params[name], thisSide),
        )
    }
}

internal fun SquashDocument.requirePart(id: PartId): PartInstance =
    requireNotNull(character.part(id)) { "No part ${id.value}" }

/** This document with [parts], holding data for just the pairs they name: from [restored], from what it has, or empty. */
private fun SquashDocument.withParts(parts: List<PartInstance>, restored: Map<PairId, PartData> = emptyMap()): SquashDocument {
    val named = parts.mapNotNullTo(mutableSetOf()) { it.pair }
    val pairs = named.associateWith { restored[it] ?: character.pairs[it] ?: PartData() }
    return copy(character = character.copy(parts = parts, pairs = pairs))
}

/** The pair data this document holds and [next] does not. */
private fun SquashDocument.pairDataDropped(next: SquashDocument): Map<PairId, PartData> =
    character.pairs.filterKeys { it !in next.character.pairs }

/** The values a write to [part] replaces: its pair's, its side's overrides, or its own when unpaired. */
private fun SquashDocument.writtenData(part: PartInstance, thisSide: Boolean): PartData = when {
    part.pair == null -> part.own
    thisSide -> part.overrides
    else -> character.pairs.getValue(part.pair)
}

private fun SquashDocument.withWritten(part: PartInstance, thisSide: Boolean, data: PartData): SquashDocument = when {
    part.pair == null -> withPart(part.copy(own = data))
    thisSide -> withPart(part.copy(overrides = data))
    else -> copy(character = character.copy(pairs = character.pairs + (part.pair to data)))
}

private fun SquashDocument.withPart(part: PartInstance): SquashDocument =
    copy(character = character.copy(parts = character.parts.map { if (it.id == part.id) part else it }))
