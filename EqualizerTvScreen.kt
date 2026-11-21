package com.acoustictv.equalizer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acoustictv.equalizer.model.AudioPreset
import com.acoustictv.equalizer.model.BandFrequency
import com.acoustictv.equalizer.model.EqualizerCatalog
import com.acoustictv.equalizer.model.EqualizerState
import java.util.Locale

private val BgCanvas = Color(0xFF090C11)
private val SurfaceCard = Color(0xFF131822)
private val SurfaceElevated = Color(0xFF1A2230)
private val BorderSubtle = Color(0xFF242E40)
private val FocusGlowSky = Color(0xFF38BDF8)
private val AtmosGold = Color(0xFFF59E0B)
private val ActiveGreen = Color(0xFF10B981)
private val MutedText = Color(0xFF94A3B8)

@Composable
fun EqualizerTvScreen(
    state: EqualizerState,
    onToggleMaster: (Boolean) -> Unit,
    onToggleDolbyAtmos: () -> Unit,
    onSelectPreset: (AudioPreset) -> Unit,
    onBandChange: (Int, Float) -> Unit,
    onSurroundChange: (Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onDialogueChange: (Int) -> Unit,
    onPreampChange: (Float) -> Unit,
    onResetBands: () -> Unit,
    onRevertLastPreset: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas)
            .verticalScroll(scrollState)
            .padding(horizontal = 36.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Top Header Bar (Title + Preset Readout + Dolby Atmos Button + Master ON/OFF Switch)
        TvTopHeaderBar(
            state = state,
            onToggleDolbyAtmos = onToggleDolbyAtmos,
            onToggleMaster = onToggleMaster
        )

        // 2. Preset Rows (Row 1: Sound Company Signatures, Row 2: Standard TV Presets)
        PresetRowSection(
            title = "Sound Company Signatures",
            presets = EqualizerCatalog.signaturePresets,
            activePresetId = state.activePresetId,
            onSelectPreset = onSelectPreset
        )

        PresetRowSection(
            title = "Standard TV Presets",
            presets = EqualizerCatalog.standardPresets,
            activePresetId = state.activePresetId,
            onSelectPreset = onSelectPreset
        )

        // 3. 7-Band Vertical EQ + 4 Unlocked Spatial & Dynamics Sliders
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left: 7-Band Graphic Equalizer (-12.0 dB to +12.0 dB, 0.5 dB steps)
            Column(
                modifier = Modifier
                    .weight(1.45f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "7-Band Graphic Equalizer",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.activePresetId == EqualizerState.CUSTOM_PRESET_ID) {
                            val lastPresetName = EqualizerCatalog.allPresets
                                .firstOrNull { it.id == state.lastPresetId }
                                ?.name ?: "Flat"
                            TvActionChipButton(
                                label = "Revert: $lastPresetName",
                                highlight = true,
                                onClick = onRevertLastPreset
                            )
                        }
                        TvActionChipButton(
                            label = "Reset Bands",
                            highlight = false,
                            onClick = onResetBands
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EqualizerCatalog.bands.forEach { band ->
                        val bandValue = state.bandsDb.getOrElse(band.index) { 0f }
                        TvVerticalEqSlider(
                            band = band,
                            valueDb = bandValue,
                            enabled = state.masterEnabled,
                            onValueChange = { nextDb -> onBandChange(band.index, nextDb) }
                        )
                    }
                }
            }

            // Right: Surround, Bass Boost, Dialogue Clarity, Preamp / Loudness Gain
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spatial & Dynamics Stage",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                TvHorizontalDspSlider(
                    label = "Surround Sound (Virtualizer)",
                    valueText = "${state.surroundPercent}%",
                    normalized = state.surroundPercent / 100f,
                    accentColor = FocusGlowSky,
                    masterEnabled = state.masterEnabled,
                    onStepUp = { onSurroundChange(state.surroundPercent + 5) },
                    onStepDown = { onSurroundChange(state.surroundPercent - 5) }
                )

                TvHorizontalDspSlider(
                    label = "Bass Boost (0–1000 API)",
                    valueText = "${state.bassBoostPercent}%",
                    normalized = state.bassBoostPercent / 100f,
                    accentColor = Color(0xFF34D399),
                    masterEnabled = state.masterEnabled,
                    onStepUp = { onBassBoostChange(state.bassBoostPercent + 5) },
                    onStepDown = { onBassBoostChange(state.bassBoostPercent - 5) }
                )

                TvHorizontalDspSlider(
                    label = "Dialogue Clarity (1kHz–4kHz)",
                    valueText = "${state.dialogueClarityPercent}%",
                    normalized = state.dialogueClarityPercent / 100f,
                    accentColor = AtmosGold,
                    masterEnabled = state.masterEnabled,
                    onStepUp = { onDialogueChange(state.dialogueClarityPercent + 5) },
                    onStepDown = { onDialogueChange(state.dialogueClarityPercent - 5) }
                )

                TvHorizontalDspSlider(
                    label = "Preamp / Loudness Gain",
                    valueText = formatDb(state.preampDb),
                    normalized = ((state.preampDb - EqualizerState.PREAMP_MIN_DB) /
                        (EqualizerState.PREAMP_MAX_DB - EqualizerState.PREAMP_MIN_DB)),
                    accentColor = Color(0xFFA78BFA),
                    masterEnabled = state.masterEnabled,
                    onStepUp = { onPreampChange(state.preampDb + EqualizerState.PREAMP_STEP_DB) },
                    onStepDown = { onPreampChange(state.preampDb - EqualizerState.PREAMP_STEP_DB) }
                )
            }
        }
    }
}

@Composable
private fun TvTopHeaderBar(
    state: EqualizerState,
    onToggleDolbyAtmos: () -> Unit,
    onToggleMaster: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(FocusGlowSky.copy(alpha = 0.14f))
                    .border(1.dp, FocusGlowSky.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = FocusGlowSky
                )
            }

            Column {
                Text(
                    text = "AcousticTV Global Equalizer",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Preset: ${state.activePresetName}  ·  Sessions: ${state.attachedSessionCount}  ·  " +
                        if (state.masterEnabled) "DSP Active" else "DSP Bypassed",
                    color = MutedText,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DolbyAtmosToggleButton(
                enabled = state.dolbyAtmosEnabled,
                onClick = onToggleDolbyAtmos
            )

            MasterPowerSwitch(
                checked = state.masterEnabled,
                onToggle = { onToggleMaster(!state.masterEnabled) }
            )
        }
    }
}

@Composable
private fun DolbyAtmosToggleButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> AtmosGold
            enabled -> FocusGlowSky
            else -> BorderSubtle
        },
        animationSpec = tween(150),
        label = "atmosBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            enabled -> FocusGlowSky.copy(alpha = 0.2f)
            focused -> SurfaceElevated
            else -> Color(0xFF0E131B)
        },
        animationSpec = tween(150),
        label = "atmosBg"
    )

    Row(
        modifier = Modifier
            .shadow(if (enabled || focused) 14.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.SurroundSound,
            contentDescription = "Dolby Atmos",
            tint = if (enabled) FocusGlowSky else MutedText,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "Dolby Atmos",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (enabled) AtmosGold else Color(0xFF1F2937))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (enabled) "ON" else "OFF",
                color = if (enabled) Color(0xFF111827) else MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MasterPowerSwitch(
    checked: Boolean,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> FocusGlowSky
            checked -> ActiveGreen.copy(alpha = 0.7f)
            else -> BorderSubtle
        },
        animationSpec = tween(150),
        label = "masterBorder"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) ActiveGreen.copy(alpha = 0.14f) else Color(0xFF0E131B))
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onToggle()
                    true
                } else {
                    false
                }
            }
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PowerSettingsNew,
            contentDescription = "Master Power",
            tint = if (checked) ActiveGreen else Color(0xFFEF4444),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = if (checked) "MASTER ON" else "MASTER OFF",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(if (checked) ActiveGreen else Color(0xFF334155))
                .padding(2.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun PresetRowSection(
    title: String,
    presets: List<AudioPreset>,
    activePresetId: String,
    onSelectPreset: (AudioPreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = MutedText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                TvPresetCard(
                    preset = preset,
                    isSelected = preset.id == activePresetId,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }
    }
}

@Composable
private fun TvPresetCard(
    preset: AudioPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> FocusGlowSky
            isSelected -> FocusGlowSky.copy(alpha = 0.6f)
            else -> BorderSubtle
        },
        animationSpec = tween(140),
        label = "presetBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> FocusGlowSky.copy(alpha = 0.14f)
            focused -> SurfaceElevated
            else -> SurfaceCard
        },
        animationSpec = tween(140),
        label = "presetBg"
    )

    Column(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = preset.name,
            color = if (isSelected || focused) Color.White else Color(0xFFE2E8F0),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Surr ${preset.surroundPercent}% · Bass ${preset.bassBoostPercent}% · Pre ${formatDb(preset.preampDb)}",
            color = MutedText,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TvVerticalEqSlider(
    band: BandFrequency,
    valueDb: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val normalized by animateFloatAsState(
        targetValue = (valueDb - EqualizerState.EQ_MIN_DB) /
            (EqualizerState.EQ_MAX_DB - EqualizerState.EQ_MIN_DB),
        animationSpec = tween(120),
        label = "eqBandNorm"
    )

    Column(
        modifier = Modifier
            .width(74.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) SurfaceElevated else Color(0xFF0E131B))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusGlowSky else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onValueChange(valueDb + EqualizerState.EQ_STEP_DB)
                            true
                        }
                        Key.DirectionDown -> {
                            onValueChange(valueDb - EqualizerState.EQ_STEP_DB)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = formatDb(valueDb),
            color = if (enabled) FocusGlowSky else MutedText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .width(26.dp)
                    .fillMaxHeight()
            ) {
                val trackWidth = 6.dp.toPx()
                val trackX = (size.width - trackWidth) / 2f
                val centerY = size.height / 2f

                // Base track
                drawRoundRect(
                    color = Color(0xFF1F2937),
                    topLeft = Offset(trackX, 0f),
                    size = Size(trackWidth, size.height),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // Zero dB midpoint notch
                drawLine(
                    color = Color(0xFF475569),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.5.dp.toPx()
                )

                val thumbY = size.height * (1f - normalized.coerceIn(0f, 1f))
                val fillTop = minOf(centerY, thumbY)
                val fillHeight = kotlin.math.abs(centerY - thumbY)

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = if (enabled) {
                            listOf(FocusGlowSky, Color(0xFF0284C7))
                        } else {
                            listOf(Color(0xFF475569), Color(0xFF334155))
                        }
                    ),
                    topLeft = Offset(trackX, fillTop),
                    size = Size(trackWidth, fillHeight),
                    cornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                drawCircle(
                    color = if (focused) Color.White else if (enabled) FocusGlowSky else MutedText,
                    radius = 7.dp.toPx(),
                    center = Offset(size.width / 2f, thumbY)
                )
            }
        }

        Text(
            text = band.label,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TvHorizontalDspSlider(
    label: String,
    valueText: String,
    normalized: Float,
    accentColor: Color,
    masterEnabled: Boolean,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val animNorm by animateFloatAsState(
        targetValue = normalized.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "dspSliderNorm"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) SurfaceElevated else Color(0xFF0E131B))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusGlowSky else BorderSubtle,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onStepUp()
                            true
                        }
                        Key.DirectionDown -> {
                            onStepDown()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                color = if (masterEnabled) accentColor else MutedText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        ) {
            val barHeight = 5.dp.toPx()
            val barTop = (size.height - barHeight) / 2f
            drawRoundRect(
                color = Color(0xFF1F2937),
                topLeft = Offset(0f, barTop),
                size = Size(size.width, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            val activeW = size.width * animNorm
            drawRoundRect(
                color = if (masterEnabled) accentColor else Color(0xFF475569),
                topLeft = Offset(0f, barTop),
                size = Size(activeW, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            drawCircle(
                color = if (focused) Color.White else if (masterEnabled) accentColor else MutedText,
                radius = 5.5.dp.toPx(),
                center = Offset(activeW.coerceIn(6f, size.width - 6f), size.height / 2f)
            )
        }
    }
}

@Composable
private fun TvActionChipButton(
    label: String,
    highlight: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> FocusGlowSky
            highlight -> FocusGlowSky.copy(alpha = 0.45f)
            else -> BorderSubtle
        },
        animationSpec = tween(140),
        label = "resetBtnBorder"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> SurfaceElevated
                    highlight -> FocusGlowSky.copy(alpha = 0.14f)
                    else -> Color(0xFF0E131B)
                }
            )
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.DirectionCenter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = label,
            tint = if (highlight || focused) FocusGlowSky else MutedText,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = if (highlight || focused) Color.White else Color(0xFFCBD5E1),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDb(value: Float): String {
    val rounded = (value * 10f).toInt() / 10f
    return if (rounded > 0f) {
        String.format(Locale.US, "+%.1f dB", rounded)
    } else {
        String.format(Locale.US, "%.1f dB", rounded)
    }
}
