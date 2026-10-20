package app.vela.carlauncher.ui

import android.content.SharedPreferences
import android.graphics.Color as NativeColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.vela.R
import app.vela.carlauncher.widgets.InterlockingClockView
import app.vela.ui.settings.ToggleRow
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Full settings page; the real panel and preview share saved appearance and drawing code. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ClockCustomizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { SmallClockAppearance.preferences(context) }
    var style by remember(prefs) { mutableStateOf(SmallClockAppearance.read(prefs)) }
    var target by remember { mutableIntStateOf(0) }
    var section by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(java.util.Date()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("clock_") == true) style = SmallClockAppearance.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) { now = java.util.Date(); delay(1000) }
    }
    fun change(value: SmallClockAppearance) {
        style = value.normalized()
        style.save(prefs)
    }
    val background = remember(context) { SmallClockAppearance.backgroundColors(context.getDrawable(R.drawable.bg_default_music_art)) }
    val preview: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF55452B), Color(0xFF44212E), Color(0xFF1A213B))))
            .padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.car_clock_preview), color = Color.White.copy(alpha = .7f))
            AndroidView(factory = { InterlockingClockView(it) }, modifier = Modifier.fillMaxWidth().height(150.dp),
                update = { view ->
                    style.apply(view, context, background)
                    view.textSize = 76f * style.scale
                    view.text = android.text.format.DateFormat.format(style.timePattern(context), now)
                    view.contentDescription = context.getString(R.string.car_clock_preview) + ": " + view.text
                })
            Text(stringResource(R.string.car_clock_live_hint), color = Color.White.copy(alpha = .8f),
                style = MaterialTheme.typography.bodySmall)
        }
    }
    val controls: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.car_clock_tab_appearance, R.string.car_clock_tab_colors, R.string.car_clock_tab_time)
                    .forEachIndexed { index, label -> FilterChip(section == index, { section = index },
                        label = { Text(stringResource(label)) }) }
            }
            val scroll = remember(section) { androidx.compose.foundation.ScrollState(0) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (section) {
                    0 -> {
                        ClockSlider(stringResource(R.string.car_clock_size), style.scale * 100, 60f..150f,
                            (style.scale * 100).roundToInt().toString() + "%") { change(style.copy(scale = it / 100)) }
                        Text(stringResource(R.string.car_clock_fit_hint), style = MaterialTheme.typography.bodySmall)
                        ClockSlider(stringResource(R.string.car_clock_spacing), style.spacing * 100, -26f..12f,
                            (style.spacing * 100).roundToInt().toString() + "%") { change(style.copy(spacing = it / 100)) }
                        ClockSlider(stringResource(R.string.car_clock_colon_spacing), style.colonSpacing * 100, 0f..20f,
                            (style.colonSpacing * 100).roundToInt().toString() + "%") { change(style.copy(colonSpacing = it / 100)) }
                        ClockSlider(stringResource(R.string.car_clock_outline_width), style.outline, .5f..3f,
                            "%.1f".format(style.outline)) { change(style.copy(outline = it)) }
                        ClockSlider(stringResource(R.string.car_clock_opacity), style.opacity.toFloat(), 0f..100f,
                            style.opacity.toString() + "%") { change(style.copy(opacity = it.roundToInt())) }
                        Text(stringResource(R.string.car_clock_font), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(R.string.car_clock_font_boxed, R.string.car_clock_font_rounded, R.string.car_clock_font_classic)
                                .forEachIndexed { index, label -> FilterChip(style.font == index, { change(style.copy(font = index)) },
                                    label = { Text(stringResource(label)) }) }
                        }
                        ToggleRow(label = stringResource(R.string.car_small_clock_embossed), checked = style.embossed,
                            onCheckedChange = { change(style.copy(embossed = it)) })
                    }
                    1 -> {
                        Text(stringResource(R.string.car_small_clock_color), style = MaterialTheme.typography.titleMedium)
                        val labels = listOf(R.string.car_small_clock_turquoise, R.string.car_small_clock_theme,
                            R.string.car_small_clock_amber, R.string.car_small_clock_transparent,
                            R.string.car_small_clock_white, R.string.car_small_clock_custom,
                            R.string.car_small_clock_soft, R.string.car_small_clock_background)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            labels.forEachIndexed { index, label ->
                                FilterChip(style.palette == index, { change(style.copy(palette = index)) },
                                    label = { Text(stringResource(label)) })
                            }
                        }
                        ToggleRow(label = stringResource(R.string.car_small_clock_two_colors), checked = style.dual,
                            onCheckedChange = { if (!it && target == 1) target = 0; change(style.copy(dual = it)) })
                        Text(stringResource(R.string.car_clock_custom_colors), style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(R.string.car_small_clock_hours, R.string.car_small_clock_minutes, R.string.car_clock_outline_color)
                                .forEachIndexed { index, label ->
                                    FilterChip(target == index, { target = index }, label = { Text(stringResource(label)) },
                                        enabled = index != 1 || style.dual)
                                }
                        }
                        val selected = when (target) { 1 -> style.secondary; 2 -> style.outlineColor; else -> style.primary }
                        fun setColor(color: Int) = change(when (target) {
                            1 -> style.copy(palette = 5, secondary = color)
                            2 -> style.copy(outlineColor = color)
                            else -> style.copy(palette = 5, primary = color)
                        })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFFFFFFFF, 0xFFEBE8DB, 0xFFD7C8E7, 0xFFB8DDD4, 0xFFBDD3E8, 0xFFE7C9C5, 0xFFE7D5B4, 0xFF22252D)
                                .forEach { color ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = Color(color),
                                        border = if (selected == color.toInt()) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.size(48.dp).semantics { contentDescription = "#%06X".format(color.toInt() and 0xFFFFFF) }.clickable { setColor(color.toInt()) }) {}
                                }
                        }
                        val hsv = FloatArray(3).also { NativeColor.colorToHSV(selected, it) }
                        Text("#%06X".format(selected and 0xFFFFFF), style = MaterialTheme.typography.labelLarge)
                        listOf(R.string.car_clock_hue, R.string.car_clock_saturation, R.string.car_clock_brightness)
                            .forEachIndexed { index, label ->
                                ClockSlider(stringResource(label), hsv[index], if (index == 0) 0f..360f else 0f..1f,
                                    if (index == 0) hsv[index].roundToInt().toString() else (hsv[index] * 100).roundToInt().toString() + "%") {
                                    val next = hsv.copyOf(); next[index] = it; setColor(NativeColor.HSVToColor(next))
                                }
                            }
                    }
                    else -> {
                        Text(stringResource(R.string.car_clock_format), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to R.string.car_clock_format_system, "24" to R.string.car_clock_format_24, "12" to R.string.car_clock_format_12)
                                .forEach { (key, label) -> FilterChip(style.format == key, { change(style.copy(format = key)) },
                                    label = { Text(stringResource(label)) }) }
                        }
                        ToggleRow(label = stringResource(R.string.car_clock_seconds), checked = style.seconds,
                            onCheckedChange = { change(style.copy(seconds = it)) })
                    }
                }
                OutlinedButton(onClick = { change(SmallClockAppearance()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.car_clock_reset))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.car_clock_customize)) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.car_clock_back)) } }) }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (maxWidth >= 600.dp) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(.38f)) { preview() }
                Box(Modifier.weight(.62f)) { controls() }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                preview()
                Box(Modifier.weight(1f)) { controls() }
            }
        }
    }
}

@Composable
private fun ClockSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, display: String,
    onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label })
    }
}
