package dev.chieppa.squash.screens.surface

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.lipRegionsOf
import dev.chieppa.squash.character.model.MouthStyle
import dev.chieppa.squash.character.mouth.dentitionOf
import dev.chieppa.squash.character.mouth.shaped.buildsJawOpen
import dev.chieppa.squash.character.mouth.mouthMarks
import dev.chieppa.squash.character.mouth.mouthStyleOf
import dev.chieppa.squash.character.mouth.saying
import dev.chieppa.squash.character.mouth.shapeMouth
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.MouthShapeName
import dev.chieppa.squash.document.MouthShapeValues
import dev.chieppa.squash.document.SetMouthShape
import dev.chieppa.squash.screens.lines.PickedGroup
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.face_shape_ai
import dev.chieppa.squash.uidesign.generated.face_shape_e
import dev.chieppa.squash.uidesign.generated.face_shape_etc
import dev.chieppa.squash.uidesign.generated.face_shape_fv
import dev.chieppa.squash.uidesign.generated.face_shape_l
import dev.chieppa.squash.uidesign.generated.face_shape_mbp
import dev.chieppa.squash.uidesign.generated.face_shape_o
import dev.chieppa.squash.uidesign.generated.face_shape_reset
import dev.chieppa.squash.uidesign.generated.face_shape_rest
import dev.chieppa.squash.uidesign.generated.face_shape_u
import dev.chieppa.squash.uidesign.generated.face_shapes
import dev.chieppa.stretch.frame.DrawerSection
import dev.chieppa.stretch.theme.Stretch
import dev.chieppa.stretch.ui.Button
import dev.chieppa.stretch.ui.ButtonKind
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val STRIP_GAP = 6.dp

/** Each spoken shape's name on its tile. */
internal val MOUTH_SHAPE_NAMES: Map<MouthShapeName, StringResource> = mapOf(
    MouthShapeName.REST to Res.string.face_shape_rest,
    MouthShapeName.MBP to Res.string.face_shape_mbp,
    MouthShapeName.AI to Res.string.face_shape_ai,
    MouthShapeName.E to Res.string.face_shape_e,
    MouthShapeName.O to Res.string.face_shape_o,
    MouthShapeName.U to Res.string.face_shape_u,
    MouthShapeName.FV to Res.string.face_shape_fv,
    MouthShapeName.L to Res.string.face_shape_l,
    MouthShapeName.ETC to Res.string.face_shape_etc,
)

/**
 * The mouth's Shapes, where its style speaks: a strip of its nine spoken shapes, each tile drawn by the mouth shaper from the mouth's own marks. Picking one shows
 * every mouth saying it in the panes, which is no edit and is let go when the section leaves, and shows the shape's seven controls, each
 * drag one step of undo, with Reset to take the style's shape back.
 */
@Composable
internal fun MouthShapesSection(session: CharacterSession, picked: PickedGroup, group: MarkGroup) {
    val preview = session.mouthPreview
    val shown by preview.collectAsState()
    DisposableEffect(preview) { onDispose { preview.value = null } }
    val drag = rememberCommandDrag(session)
    val work by session.bodySurfaceWork.output.collectAsState()
    val assembled by session.assembly.output.collectAsState()
    val document by session.document.collectAsState()
    val held = work?.parts?.get(picked.part)
    val surface = assembled?.part(picked.part)?.surface
    val style = document.character.part(picked.part)?.let { session.templates.mouthStyleOf(it, group) } ?: MouthStyle.PLAIN
    if (!style.speaks) return
    val palette = document.character.palette
    val colors = MouthTileColors(
        ink = Stretch.colors.ink,
        inner = palette.colorOf(group.roles.firstOrNull { it.role == MarkRole.INNER }?.fill),
        tongue = palette.colorOf(group.roles.firstOrNull { it.role == MarkRole.TONGUE }?.fill),
        teeth = palette.colorOf(group.roles.firstOrNull { it.role == MarkRole.TEETH }?.fill),
        lips = palette.colorOf(group.roles.firstOrNull { it.role == MarkRole.LIP_LINE }?.fill),
    )
    val marks = held?.marks?.groupBy { it.source }.orEmpty()
    val lipRegions = held?.let { lipRegionsOf(it, group) }.orEmpty().map { region -> region.border.map { it.anchor } }
    val builtOpen = document.character.part(picked.part)?.let { buildsJawOpen(document.character, it, session.templates.find(it.template)) } ?: false
    val placed = group.mouthMarks({ id -> marks[id].orEmpty().map { mark -> mark.points.map { it.anchor } } }, lipRegions, builtOpen)
    val dentition = session.templates.dentitionOf(group, style, builtOpen)
    fun shaped(shape: MouthShapeName) = surface?.let { shapeMouth(picked.part, placed, group.controls.speaking(group.saying(shape, style)), style, it, dentition) }
    val rest = shaped(MouthShapeName.REST)
    DrawerSection(stringResource(Res.string.face_shapes)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag(FaceTags.SHAPES), horizontalArrangement = Arrangement.spacedBy(STRIP_GAP)) {
            MouthShapeName.entries.forEach { shape ->
                Tile(stringResource(MOUTH_SHAPE_NAMES.getValue(shape)), shown == shape, FaceTags.shape(shape), { preview.value = shape.takeUnless { shown == shape } }) {
                    MouthShapeThumbnail(shaped(shape)?.flat, rest?.flat, colors, it)
                }
            }
        }
        val saying = shown ?: return@DrawerSection
        val values = group.saying(saying, style)
        val override = group.shapes[saying]
        MOUTH_CONTROLS.forEach { control ->
            ControlSlider(control.label, control.shapeRead(values) ?: 0f, FaceTags.shapeControl(control.name), drag::finish, control.range) {
                drag.update(SetMouthShape(picked.part, group.id, saying, control.shapeWrite(override ?: MouthShapeValues(), it)))
            }
        }
        Button(
            stringResource(Res.string.face_shape_reset),
            { session.send(SetMouthShape(picked.part, group.id, saying, null)) },
            Modifier.fillMaxWidth().testTag(FaceTags.SHAPE_RESET),
            kind = ButtonKind.Quiet,
            enabled = override != null,
        )
    }
}
