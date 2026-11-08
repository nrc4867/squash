package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.PartTemplate
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.covers
import dev.chieppa.squash.geom.positionAt

/**
 * Where this template's mount lies from its axis start, in its own axes, or zero when it has none: how far a part is carried back
 * so that point, not its axis start, sits on its socket. It reads the template's build with nothing drawn, as [socketSink] does.
 */
internal fun PartTemplate.mountOffset(): Vec3 {
    val mount = mount ?: return Vec3.ZERO
    val run = PartBuilder.build(this).firstOrNull { it.slices != null && it.covers(mount.u) } ?: return Vec3.ZERO
    return run.positionAt(mount.u, mount.v)
}
