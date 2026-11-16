package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.TemplateLibrary
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.AddParts
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.BodyPlanId
import dev.chieppa.squash.document.BodyPlanRef
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.CharacterId
import dev.chieppa.squash.document.Mark
import dev.chieppa.squash.document.MarkId
import dev.chieppa.squash.document.PairId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.PartInstance
import dev.chieppa.squash.document.Side
import dev.chieppa.squash.document.SocketRef
import dev.chieppa.squash.document.SquashDocument
import dev.chieppa.squash.document.TemplateId
import dev.chieppa.squash.document.TemplateRef
import dev.chieppa.squash.geom.PartSurface
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val POINT_CLOSE = 1e-3f

fun templateRef(id: String) = TemplateRef(TemplateId(id), 1)

val torsoPart = PartInstance(PartId("torso"), templateRef("torso"))
private val legPair = PairId("front-legs")
val leftLeg = PartInstance(PartId("leg-l"), templateRef("leg"), SocketRef(torsoPart.id, "front-leg"), pair = legPair, side = Side.LEFT)
val rightLeg = PartInstance(PartId("leg-r"), templateRef("leg"), SocketRef(torsoPart.id, "front-leg"), pair = legPair, side = Side.RIGHT)

/** A session over [parts] on a fennec, with the shipped templates and plans. */
fun TestScope.surfaceSession(vararg parts: PartInstance): CharacterSession {
    val empty = SquashDocument(Character(CharacterId("fennec"), "Fennec", BodyPlanRef(BodyPlanId("canine-quadruped"), 1)))
    return CharacterSession(
        AddParts(parts.toList()).apply(empty).document,
        TemplateLibrary(shipped.parts, shipped.plans),
        backgroundScope,
        StandardTestDispatcher(testScheduler),
    )
}

suspend fun CharacterSession.settle() {
    graph.settled.first { it }
}

fun on(part: PartInstance, u: Float, v: Float, offset: Float = 0f, patch: String = BODY_PATCH) = Anchor(part.id, patch, u, v, offset)

fun line(id: String, vararg points: Anchor) = Mark(MarkId(id), points.toList())

fun CharacterSession.builtSurface(part: PartInstance): PartSurface = assertNotNull(surface(part.id).output.value)

fun PartSurface.body(): Patch = patches.first { it.name == BODY_PATCH }

fun assertClose(expected: Vec3, actual: Vec3, tolerance: Float = POINT_CLOSE) {
    assertTrue((expected - actual).length <= tolerance, "expected $expected, got $actual")
}
