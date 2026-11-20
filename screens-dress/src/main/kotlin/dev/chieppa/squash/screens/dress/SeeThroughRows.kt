package dev.chieppa.squash.screens.dress

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.chieppa.squash.character.session.CharacterSession
import dev.chieppa.squash.document.ClothNet
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.LEAST_NET_HOLE
import dev.chieppa.squash.document.LEAST_NET_THREAD
import dev.chieppa.squash.document.MOST_NET_ANGLE
import dev.chieppa.squash.document.NetPattern
import dev.chieppa.squash.document.SetAreaNet
import dev.chieppa.squash.document.SetAreaOpacity
import dev.chieppa.squash.screens.edit.SliderGesture
import dev.chieppa.squash.uidesign.SquashIcons
import dev.chieppa.squash.uidesign.generated.Res
import dev.chieppa.squash.uidesign.generated.dress_net_angle
import dev.chieppa.squash.uidesign.generated.dress_net_area
import dev.chieppa.squash.uidesign.generated.dress_net_diamond
import dev.chieppa.squash.uidesign.generated.dress_net_hole
import dev.chieppa.squash.uidesign.generated.dress_net_of
import dev.chieppa.squash.uidesign.generated.dress_net_remove
import dev.chieppa.squash.uidesign.generated.dress_net_square
import dev.chieppa.squash.uidesign.generated.dress_net_thread
import dev.chieppa.squash.uidesign.generated.dress_sheer_area
import dev.chieppa.squash.uidesign.generated.dress_sheer_of
import dev.chieppa.squash.uidesign.generated.dress_sheer_remove
import dev.chieppa.squash.uidesign.generated.edit_percent
import dev.chieppa.stretch.frame.SettingRow
import dev.chieppa.stretch.theme.StretchIcons
import dev.chieppa.stretch.ui.IconButton
import dev.chieppa.stretch.ui.Segmented
import dev.chieppa.stretch.ui.Slider
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** A newly sheer area's opacity: sheer stockings'. */
private const val NEW_SHEER = 0.25f

/** The widest a net's slider spaces its threads, in body units. */
private const val MOST_SLIDER_HOLE = 0.08f

/** The widest a net's thread slider reaches, in body units. */
private const val MOST_SLIDER_THREAD = 0.02f

private const val PERCENT = 100f

/** Thousandths of a body unit, as a net's spacing and thread read out. */
private const val THOUSANDTHS = 1000f

/**
 * A row for each sheer area, its opacity, with its cross, and Sheer an area; then for each area knitted as a net, its pattern, hole,
 * thread and angle, with its cross, and Net an area.
 */
@Composable
internal fun SeeThroughRows(session: CharacterSession, garment: Garment, gesture: SliderGesture) {
    for (area in garment.areas) {
        val opacity = area.opacity ?: continue
        val name = areaName(area)
        val label = stringResource(Res.string.dress_sheer_of, name)
        SettingRow(label) {
            Slider(opacity, { gesture.change(SetAreaOpacity(garment.id, area.id, it)) }, label, Modifier.weight(1f).testTag(DressTags.sheer(area.id)), 0f..1f, gesture::done)
            Readout(stringResource(Res.string.edit_percent, (opacity * PERCENT).roundToInt()))
            IconButton(StretchIcons.Close, stringResource(Res.string.dress_sheer_remove, name), { session.send(SetAreaOpacity(garment.id, area.id, null)) }, Modifier.testTag(DressTags.removeSheer(area.id)))
        }
    }
    val whole = garment.areas.filter { it.opacity == null }
    if (whole.isNotEmpty()) AreaMenu(stringResource(Res.string.dress_sheer_area), SquashIcons.Sheer, DressTags.SHEER_AREA, whole) { session.send(SetAreaOpacity(garment.id, it.id, NEW_SHEER)) }
    for (area in garment.areas) {
        val net = area.net ?: continue
        NetRows(session, garment, area, net, gesture)
    }
    val plain = garment.areas.filter { it.net == null }
    if (plain.isNotEmpty()) AreaMenu(stringResource(Res.string.dress_net_area), SquashIcons.Net, DressTags.NET_AREA, plain) { session.send(SetAreaNet(garment.id, it.id, ClothNet())) }
}

/** [area]'s [net]: its pattern with its cross, then its hole, thread and angle. */
@Composable
private fun NetRows(session: CharacterSession, garment: Garment, area: GarmentArea, net: ClothNet, gesture: SliderGesture) {
    val name = areaName(area)
    fun set(changed: ClothNet) = gesture.change(SetAreaNet(garment.id, area.id, ClothNet.of(changed.pattern, changed.hole, changed.thread, changed.angle)))
    val diamond = stringResource(Res.string.dress_net_diamond)
    val square = stringResource(Res.string.dress_net_square)
    SettingRow(stringResource(Res.string.dress_net_of, name)) {
        Segmented(
            NetPattern.entries, net.pattern, { session.send(SetAreaNet(garment.id, area.id, net.copy(pattern = it))) },
            { if (it == NetPattern.DIAMOND) diamond else square }, Modifier.weight(1f).testTag(DressTags.netPattern(area.id)), fill = true, compact = true,
        )
        IconButton(StretchIcons.Close, stringResource(Res.string.dress_net_remove, name), { session.send(SetAreaNet(garment.id, area.id, null)) }, Modifier.testTag(DressTags.removeNet(area.id)))
    }
    val hole = stringResource(Res.string.dress_net_hole)
    SettingRow(hole) {
        Slider(net.hole, { set(net.copy(hole = it, thread = minOf(net.thread, it * ClothNet.MOST_THREAD_SHARE))) }, hole, Modifier.weight(1f).testTag(DressTags.netHole(area.id)), LEAST_NET_HOLE..MOST_SLIDER_HOLE, gesture::done)
        Readout((net.hole * THOUSANDTHS).roundToInt().toString())
    }
    val thread = stringResource(Res.string.dress_net_thread)
    SettingRow(thread) {
        Slider(net.thread, { set(net.copy(thread = minOf(it, net.hole * ClothNet.MOST_THREAD_SHARE))) }, thread, Modifier.weight(1f).testTag(DressTags.netThread(area.id)), LEAST_NET_THREAD..MOST_SLIDER_THREAD, gesture::done)
        Readout(((net.thread * THOUSANDTHS * 10f).roundToInt() / 10f).toString())
    }
    val angle = stringResource(Res.string.dress_net_angle)
    SettingRow(angle) {
        Slider(net.angle, { set(net.copy(angle = it)) }, angle, Modifier.weight(1f).testTag(DressTags.netAngle(area.id)), 0f..MOST_NET_ANGLE, gesture::done)
        Readout(net.angle.roundToInt().toString())
    }
}

private fun areaName(area: GarmentArea): String = area.name.ifBlank { area.id.value }
