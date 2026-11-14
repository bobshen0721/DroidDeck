package com.droiddeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.input.DesktopInputPrefs
import com.droiddeck.launcher.input.DesktopTextInput
import com.droiddeck.launcher.input.pcKeyLabel
import kotlin.math.roundToInt

/** What the phone controls for a KDE desktop session show; the session owns it and the controls draw it. */
internal class DesktopControlsState(settings: DesktopInputPrefs.Settings, expanded: Boolean, position: Pair<Float, Float>, scale: Int?) {
    enum class Panel { NONE, SCALE, SETTINGS }

    var expanded by mutableStateOf(expanded)
    var x by mutableFloatStateOf(position.first)
    var y by mutableFloatStateOf(position.second)
    var settings by mutableStateOf(settings)
    var keyboardShown by mutableStateOf(false)
    var imeHeightPx by mutableIntStateOf(0)
    var keysOpen by mutableStateOf(false)
    var preview by mutableStateOf("")
    var oneShot by mutableStateOf(emptySet<Int>())
    var touchpad by mutableStateOf(true)
    var rightClickArmed by mutableStateOf(false)
    var scale by mutableStateOf(scale)
    var scaleBusy by mutableStateOf(false)
    var panel by mutableStateOf(Panel.NONE)
    var tint by mutableIntStateOf(android.graphics.Color.rgb(90, 160, 255))
    var opacity by mutableIntStateOf(100)

    val keyRowShown: Boolean get() = keysOpen || (keyboardShown && settings.extraKeys)
}

internal interface DesktopControlsActions {
    fun toggleKeyboard()
    fun togglePcKeyboard()
    fun toggleTouchMode()
    fun rightClick()
    fun scale(percent: Int)
    fun openDisplay()
    fun openMenu()
    fun settings(settings: DesktopInputPrefs.Settings)
    fun moved(x: Float, y: Float)
    fun expanded(expanded: Boolean)
    fun key(code: Int, down: Boolean)
    fun chord(mods: Set<Int>, code: Int)
    fun modifier(code: Int)
}

private sealed interface RowKey {
    data class Mod(val code: Int) : RowKey
    data class Key(val code: Int) : RowKey
    data class Chord(val label: Int, val mods: Set<Int>, val code: Int) : RowKey
}

private val ROW_KEYS: List<RowKey> = with(DesktopTextInput) {
    listOf(
        RowKey.Key(KEY_ESC), RowKey.Key(KEY_TAB),
        RowKey.Mod(KEY_LEFTCTRL), RowKey.Mod(KEY_LEFTALT), RowKey.Mod(KEY_LEFTSHIFT), RowKey.Mod(KEY_LEFTMETA),
        RowKey.Key(KEY_LEFT), RowKey.Key(KEY_UP), RowKey.Key(KEY_DOWN), RowKey.Key(KEY_RIGHT),
        RowKey.Key(KEY_HOME), RowKey.Key(KEY_END), RowKey.Key(KEY_PAGEUP), RowKey.Key(KEY_PAGEDOWN), RowKey.Key(KEY_DELETE),
        RowKey.Chord(R.string.desktop_key_copy, setOf(KEY_LEFTCTRL), KEY_C),
        RowKey.Chord(R.string.desktop_key_paste, setOf(KEY_LEFTCTRL), KEY_V),
        RowKey.Chord(R.string.desktop_key_terminal_paste, setOf(KEY_LEFTCTRL, KEY_LEFTSHIFT), KEY_V),
        RowKey.Chord(R.string.desktop_key_cut, setOf(KEY_LEFTCTRL), KEY_X),
        RowKey.Chord(R.string.desktop_key_select_all, setOf(KEY_LEFTCTRL), KEY_A),
        RowKey.Chord(R.string.desktop_key_undo, setOf(KEY_LEFTCTRL), KEY_Z),
    )
}

private val TARGET = 48.dp
private const val PANEL_HEIGHT_SHARE = 0.7f

private fun colors(tint: Int, opacity: Int, active: Boolean): Pair<Color, Color> {
    val base = Color(tint)
    val alpha = opacity.coerceIn(30, 100) / 100f
    fun light(f: Float, a: Float) = Color(base.red + (1 - base.red) * f, base.green + (1 - base.green) * f, base.blue + (1 - base.blue) * f, a * alpha)
    val fill = if (active) light(0.1f, 0.55f) else Color(base.red * 0.12f, base.green * 0.12f, base.blue * 0.12f, 0.82f * alpha)
    return fill to light(if (active) 0.85f else 0.35f, 0.95f)
}

@Composable
internal fun DesktopControls(state: DesktopControlsState, actions: DesktopControlsActions, visible: Boolean) {
    if (!visible) return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val imeDp = with(density) { state.imeHeightPx.toDp() }
        var bottomPx by remember { mutableIntStateOf(0) }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.keyboardShown) imeDp else 0.dp).fillMaxWidth()
                .onSizeChanged { bottomPx = it.height },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.preview.isNotEmpty()) Preview(state)
            if (state.keyRowShown) KeyRow(state, actions)
        }
        if (state.settings.toolbar) {
            val reserved = bottomPx + if (state.keyboardShown) state.imeHeightPx else 0
            Toolbar(state, actions, widthPx, (heightPx - reserved).coerceAtLeast(0))
        }
    }
}

@Composable
private fun Preview(state: DesktopControlsState) {
    val (fill, line) = colors(state.tint, 100, false)
    Text(
        state.preview,
        color = line,
        fontSize = 18.sp,
        maxLines = 1,
        modifier = Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(12.dp)).background(fill)
            .border(1.dp, line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("desktop-preview"),
    )
}

@Composable
private fun KeyRow(state: DesktopControlsState, actions: DesktopControlsActions) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().background(Color(0xE6101418)).horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp).testTag("desktop-key-row"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (key in ROW_KEYS) when (key) {
            is RowKey.Mod -> ToggleKey(pcKeyLabel(context, key.code), key.code in state.oneShot, state) { actions.modifier(key.code) }
            is RowKey.Key -> HoldKey(pcKeyLabel(context, key.code), state) { down -> actions.key(key.code, down) }
            is RowKey.Chord -> ToggleKey(stringResource(key.label), false, state) { actions.chord(key.mods, key.code) }
        }
    }
}

@Composable
private fun KeyCap(label: String, active: Boolean, state: DesktopControlsState, modifier: Modifier) {
    val (fill, line) = colors(state.tint, 100, active)
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(shape).background(fill).border(1.dp, line, shape)
            .padding(horizontal = 10.dp),
    ) {
        Text(label, color = line, fontSize = 15.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun ToggleKey(label: String, active: Boolean, state: DesktopControlsState, onTap: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    KeyCap(label, active, state, Modifier.clickable(role = Role.Button) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onTap()
    })
}

@Composable
private fun HoldKey(label: String, state: DesktopControlsState, onKey: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    KeyCap(label, pressed, state, Modifier.semantics { contentDescription = label }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            pressed = true
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onKey(true)
            try {
                waitForUpOrCancellation()?.consume()
            } finally {
                pressed = false
                onKey(false)
            }
        }
    })
}

@Composable
private fun Toolbar(state: DesktopControlsState, actions: DesktopControlsActions, widthPx: Int, heightPx: Int) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val rangeX = (widthPx - size.width).coerceAtLeast(1)
    val rangeY = (heightPx - size.height).coerceAtLeast(1)
    Column(
        Modifier
            .offset { IntOffset((state.x * rangeX).roundToInt().coerceIn(0, rangeX), (state.y * rangeY).roundToInt().coerceIn(0, rangeY)) }
            .widthIn(max = with(LocalDensity.current) { widthPx.toDp() })
            .onSizeChanged { size = it }
            .testTag("desktop-toolbar"),
    ) {
        val (fill, line) = colors(state.tint, state.opacity, false)
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier.clip(shape).background(fill).border(1.dp, line, shape).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Handle(state, actions, line, rangeX, rangeY)
            ToolButton(Icons.Outlined.Keyboard, stringResource(R.string.desktop_toolbar_keyboard), state.keyboardShown, state, "toolbar-keyboard") {
                actions.toggleKeyboard()
            }
            if (state.expanded) {
                ToolText(stringResource(R.string.desktop_toolbar_keys), state.keysOpen, state, "toolbar-keys") { state.keysOpen = !state.keysOpen }
                ToolText(stringResource(R.string.desktop_toolbar_pc_keyboard), false, state, "toolbar-pc") { actions.togglePcKeyboard() }
                ToolButton(
                    if (state.touchpad) Icons.Outlined.Mouse else Icons.Outlined.TouchApp,
                    stringResource(if (state.touchpad) R.string.desktop_toolbar_touchpad else R.string.desktop_toolbar_direct),
                    false, state, "toolbar-touch",
                ) { actions.toggleTouchMode() }
                ToolText(stringResource(R.string.desktop_toolbar_right_click), state.rightClickArmed, state, "toolbar-right-click") { actions.rightClick() }
                ToolButton(Icons.Outlined.ZoomIn, stringResource(R.string.desktop_toolbar_scale), state.panel == DesktopControlsState.Panel.SCALE, state, "toolbar-scale") {
                    state.panel = if (state.panel == DesktopControlsState.Panel.SCALE) DesktopControlsState.Panel.NONE else DesktopControlsState.Panel.SCALE
                }
                ToolButton(Icons.Outlined.Monitor, stringResource(R.string.desktop_toolbar_display), false, state, "toolbar-display") { actions.openDisplay() }
                ToolButton(Icons.Outlined.Settings, stringResource(R.string.desktop_toolbar_settings), state.panel == DesktopControlsState.Panel.SETTINGS, state, "toolbar-settings") {
                    state.panel = if (state.panel == DesktopControlsState.Panel.SETTINGS) DesktopControlsState.Panel.NONE else DesktopControlsState.Panel.SETTINGS
                }
                ToolButton(Icons.Outlined.Menu, stringResource(R.string.desktop_toolbar_menu), false, state, "toolbar-menu") { actions.openMenu() }
                ToolButton(Icons.Outlined.ExpandLess, stringResource(R.string.desktop_toolbar_collapse), false, state, "toolbar-collapse") {
                    state.panel = DesktopControlsState.Panel.NONE
                    actions.expanded(false)
                }
            }
        }
        val panelMax = with(LocalDensity.current) { (heightPx * PANEL_HEIGHT_SHARE).toInt().toDp() }
        if (state.expanded) when (state.panel) {
            DesktopControlsState.Panel.SCALE -> ScalePanel(state, actions, panelMax)
            DesktopControlsState.Panel.SETTINGS -> SettingsPanel(state, actions, panelMax)
            DesktopControlsState.Panel.NONE -> Unit
        }
    }
}

@Composable
private fun Handle(state: DesktopControlsState, actions: DesktopControlsActions, line: Color, rangeX: Int, rangeY: Int) {
    val label = stringResource(if (state.expanded) R.string.desktop_toolbar_collapse else R.string.desktop_toolbar_expand)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(TARGET)
            .semantics { contentDescription = label }
            .pointerInput(rangeX, rangeY) {
                detectDragGestures(onDragEnd = { actions.moved(state.x, state.y) }) { change, amount ->
                    change.consume()
                    state.x = (state.x + amount.x / rangeX).coerceIn(0f, 1f)
                    state.y = (state.y + amount.y / rangeY).coerceIn(0f, 1f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures {
                    state.panel = DesktopControlsState.Panel.NONE
                    actions.expanded(!state.expanded)
                }
            }
            .testTag("toolbar-handle"),
    ) {
        Icon(Icons.Outlined.DragIndicator, null, tint = line, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, active: Boolean, state: DesktopControlsState, tag: String, onClick: () -> Unit) {
    val (fill, line) = colors(state.tint, state.opacity, active)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(TARGET).clip(RoundedCornerShape(24.dp)).background(if (active) fill else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).testTag(tag),
    ) {
        Icon(icon, label, tint = line, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ToolText(label: String, active: Boolean, state: DesktopControlsState, tag: String, onClick: () -> Unit) {
    val (fill, line) = colors(state.tint, state.opacity, active)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.height(TARGET).widthIn(min = TARGET).clip(RoundedCornerShape(24.dp))
            .background(if (active) fill else Color.Transparent).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp).testTag(tag),
    ) {
        Text(label, color = line, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun PanelCard(state: DesktopControlsState, maxHeight: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val (fill, line) = colors(state.tint, 100, false)
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(top = 6.dp).widthIn(max = 420.dp).heightIn(max = maxHeight).clip(shape).background(fill)
            .border(1.dp, line, shape).verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ScalePanel(state: DesktopControlsState, actions: DesktopControlsActions, maxHeight: androidx.compose.ui.unit.Dp) {
    val (_, line) = colors(state.tint, 100, false)
    PanelCard(state, maxHeight) {
        Text(stringResource(R.string.desktop_scale_title), color = line, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.desktop_scale_hint), color = line, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (percent in DesktopInputPrefs.SCALES) {
                ToggleKey("$percent%", state.scale == percent, state) { if (!state.scaleBusy) actions.scale(percent) }
            }
        }
        if (state.scaleBusy) Text(stringResource(R.string.desktop_scale_applying), color = line, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsPanel(state: DesktopControlsState, actions: DesktopControlsActions, maxHeight: androidx.compose.ui.unit.Dp) {
    val (_, line) = colors(state.tint, 100, false)
    val s = state.settings
    PanelCard(state, maxHeight) {
        SettingSwitch(stringResource(R.string.desktop_setting_long_press_right_click), s.longPressRightClick, line) {
            actions.settings(s.copy(longPressRightClick = it))
        }
        Text(stringResource(R.string.desktop_setting_pointer_speed), color = line, fontSize = 13.sp)
        SpeedRow(DesktopInputPrefs.POINTER_SPEEDS, s.pointerSpeed, state) { actions.settings(s.copy(pointerSpeed = it)) }
        Text(stringResource(R.string.desktop_setting_scroll_speed), color = line, fontSize = 13.sp)
        SpeedRow(DesktopInputPrefs.SCROLL_SPEEDS, s.scrollSpeed, state) { actions.settings(s.copy(scrollSpeed = it)) }
        SettingSwitch(stringResource(R.string.desktop_setting_extra_keys), s.extraKeys, line) { actions.settings(s.copy(extraKeys = it)) }
        SettingSwitch(stringResource(R.string.desktop_setting_suggestions), s.suggestions, line) { actions.settings(s.copy(suggestions = it)) }
        SettingSwitch(stringResource(R.string.desktop_setting_paste_fallback), s.pasteFallback, line) { actions.settings(s.copy(pasteFallback = it)) }
    }
}

@Composable
private fun SpeedRow(options: List<Float>, selected: Float, state: DesktopControlsState, onPick: (Float) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (option in options) {
            val label = (if (option % 1f == 0f) option.toInt().toString() else option.toString()) + "×"
            ToggleKey(label, option == selected, state) { onPick(option) }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, line: Color, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TARGET).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = line, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
