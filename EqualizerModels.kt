package com.acoustictv.equalizer.model

import kotlin.math.roundToInt

enum class PresetCategory {
    SIGNATURE,
    STANDARD
}

data class BandFrequency(
    val index: Int,
    val frequencyHz: Int,
    val label: String
)

data class AudioPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: PresetCategory,
    val bandsDb: List<Float>,
    val surroundPercent: Int,
    val bassBoostPercent: Int,
    val dialogueClarityPercent: Int,
    val preampDb: Float
)

data class AtmosAppliedDeltas(
    val band60HzDelta: Float = 0f,
    val band15kHzDelta: Float = 0f,
    val surroundDelta: Int = 0,
    val preampDelta: Float = 0f
)

data class EqualizerState(
    val masterEnabled: Boolean = true,
    val dolbyAtmosEnabled: Boolean = false,
    val activePresetId: String = EqualizerCatalog.signaturePresets.first().id,
    val activePresetName: String = EqualizerCatalog.signaturePresets.first().name,
    val lastPresetId: String = EqualizerCatalog.signaturePresets.first().id,
    val bandsDb: List<Float> = EqualizerCatalog.signaturePresets.first().bandsDb,
    val surroundPercent: Int = EqualizerCatalog.signaturePresets.first().surroundPercent,
    val bassBoostPercent: Int = EqualizerCatalog.signaturePresets.first().bassBoostPercent,
    val dialogueClarityPercent: Int = EqualizerCatalog.signaturePresets.first().dialogueClarityPercent,
    val preampDb: Float = EqualizerCatalog.signaturePresets.first().preampDb,
    val atmosApplied: AtmosAppliedDeltas = AtmosAppliedDeltas(),
    val attachedSessionCount: Int = 1
) {
    companion object {
        const val CUSTOM_PRESET_ID = "custom"
        const val CUSTOM_PRESET_NAME = "Custom"

        const val EQ_MIN_DB = -12.0f
        const val EQ_MAX_DB = 12.0f
        const val EQ_STEP_DB = 0.5f

        const val PREAMP_MIN_DB = -6.0f
        const val PREAMP_MAX_DB = 12.0f
        const val PREAMP_STEP_DB = 0.5f

        const val ATMOS_SURROUND_DELTA = 30
        const val ATMOS_60HZ_DELTA_DB = 2.0f
        const val ATMOS_15KHZ_DELTA_DB = 2.5f
        const val ATMOS_PREAMP_DELTA_DB = 1.5f

        fun clampEqDb(value: Float): Float {
            val stepped = (value * 2f).roundToInt() / 2f
            return stepped.coerceIn(EQ_MIN_DB, EQ_MAX_DB)
        }

        fun clampPreampDb(value: Float): Float {
            val stepped = (value * 2f).roundToInt() / 2f
            return stepped.coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB)
        }

        fun clampPercent(value: Int): Int {
            return value.coerceIn(0, 100)
        }
    }

    fun withPresetApplied(preset: AudioPreset): EqualizerState {
        val nextBands = preset.bandsDb.toMutableList()
        var nextSurround = preset.surroundPercent
        var nextPreamp = preset.preampDb
        var nextDeltas = AtmosAppliedDeltas()

        if (dolbyAtmosEnabled) {
            val boosted60 = clampEqDb(nextBands[0] + ATMOS_60HZ_DELTA_DB)
            val boosted15k = clampEqDb(nextBands[6] + ATMOS_15KHZ_DELTA_DB)
            val boostedSurround = clampPercent(nextSurround + ATMOS_SURROUND_DELTA)
            val boostedPreamp = clampPreampDb(nextPreamp + ATMOS_PREAMP_DELTA_DB)

            nextDeltas = AtmosAppliedDeltas(
                band60HzDelta = boosted60 - nextBands[0],
                band15kHzDelta = boosted15k - nextBands[6],
                surroundDelta = boostedSurround - nextSurround,
                preampDelta = boostedPreamp - nextPreamp
            )

            nextBands[0] = boosted60
            nextBands[6] = boosted15k
            nextSurround = boostedSurround
            nextPreamp = boostedPreamp
        }

        return copy(
            activePresetId = preset.id,
            activePresetName = preset.name,
            lastPresetId = preset.id,
            bandsDb = nextBands,
            surroundPercent = nextSurround,
            bassBoostPercent = preset.bassBoostPercent,
            dialogueClarityPercent = preset.dialogueClarityPercent,
            preampDb = nextPreamp,
            atmosApplied = nextDeltas
        )
    }

    fun withBandsResetToZero(): EqualizerState {
        return copy(
            activePresetId = CUSTOM_PRESET_ID,
            activePresetName = CUSTOM_PRESET_NAME,
            bandsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f),
            atmosApplied = atmosApplied.copy(
                band60HzDelta = 0f,
                band15kHzDelta = 0f
            )
        )
    }

    fun withDolbyAtmosToggled(): EqualizerState {
        val enabling = !dolbyAtmosEnabled
        val nextBands = bandsDb.toMutableList()

        return if (enabling) {
            val boosted60 = clampEqDb(nextBands[0] + ATMOS_60HZ_DELTA_DB)
            val boosted15k = clampEqDb(nextBands[6] + ATMOS_15KHZ_DELTA_DB)
            val boostedSurround = clampPercent(surroundPercent + ATMOS_SURROUND_DELTA)
            val boostedPreamp = clampPreampDb(preampDb + ATMOS_PREAMP_DELTA_DB)

            val applied = AtmosAppliedDeltas(
                band60HzDelta = boosted60 - nextBands[0],
                band15kHzDelta = boosted15k - nextBands[6],
                surroundDelta = boostedSurround - surroundPercent,
                preampDelta = boostedPreamp - preampDb
            )

            nextBands[0] = boosted60
            nextBands[6] = boosted15k

            copy(
                dolbyAtmosEnabled = true,
                bandsDb = nextBands,
                surroundPercent = boostedSurround,
                preampDb = boostedPreamp,
                atmosApplied = applied
            )
        } else {
            nextBands[0] = clampEqDb(nextBands[0] - atmosApplied.band60HzDelta)
            nextBands[6] = clampEqDb(nextBands[6] - atmosApplied.band15kHzDelta)
            val restoredSurround = clampPercent(surroundPercent - atmosApplied.surroundDelta)
            val restoredPreamp = clampPreampDb(preampDb - atmosApplied.preampDelta)

            copy(
                dolbyAtmosEnabled = false,
                bandsDb = nextBands,
                surroundPercent = restoredSurround,
                preampDb = restoredPreamp,
                atmosApplied = AtmosAppliedDeltas()
            )
        }
    }
}

object EqualizerCatalog {
    val bands = listOf(
        BandFrequency(0, 60, "60Hz"),
        BandFrequency(1, 150, "150Hz"),
        BandFrequency(2, 400, "400Hz"),
        BandFrequency(3, 1000, "1kHz"),
        BandFrequency(4, 2400, "2.4kHz"),
        BandFrequency(5, 6000, "6kHz"),
        BandFrequency(6, 15000, "15kHz")
    )

    // Row 1 — Sound Company Signature Presets
    val signaturePresets = listOf(
        AudioPreset(
            id = "sony_clearaudio",
            name = "Sony ClearAudio+",
            subtitle = "Crisp vocal clarity & balanced dynamics",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(1.5f, -1.5f, 0.5f, 2.0f, 2.5f, 1.5f, 1.0f),
            surroundPercent = 35,
            bassBoostPercent = 15,
            dialogueClarityPercent = 30,
            preampDb = 2.0f
        ),
        AudioPreset(
            id = "sennheiser_acoustic",
            name = "Sennheiser Acoustic",
            subtitle = "Reference studio midrange transparency",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(-1.0f, -2.0f, -0.5f, 1.5f, 2.0f, 0.5f, -0.5f),
            surroundPercent = 20,
            bassBoostPercent = 0,
            dialogueClarityPercent = 15,
            preampDb = 1.5f
        ),
        AudioPreset(
            id = "dolby_surround",
            name = "Dolby Surround",
            subtitle = "Expansive theatrical field & deep impact",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(4.0f, 1.0f, -1.5f, 1.5f, 3.0f, 2.5f, 3.0f),
            surroundPercent = 70,
            bassBoostPercent = 40,
            dialogueClarityPercent = 45,
            preampDb = 3.5f
        ),
        AudioPreset(
            id = "bose_rich_bass",
            name = "Bose Rich Bass",
            subtitle = "Deep warm low-end presence & body",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(5.0f, 2.5f, -1.0f, 0.5f, 1.5f, 1.0f, 1.5f),
            surroundPercent = 45,
            bassBoostPercent = 60,
            dialogueClarityPercent = 20,
            preampDb = 2.5f
        ),
        AudioPreset(
            id = "jbl_punchy",
            name = "JBL Punchy",
            subtitle = "Energetic transient snap & upper sparkle",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(4.5f, 1.5f, -2.0f, 1.0f, 2.5f, 3.0f, 2.5f),
            surroundPercent = 40,
            bassBoostPercent = 50,
            dialogueClarityPercent = 25,
            preampDb = 3.0f
        ),
        AudioPreset(
            id = "harman_kardon_studio",
            name = "Harman Kardon Studio",
            subtitle = "Refined musicality & tight mid-bass",
            category = PresetCategory.SIGNATURE,
            bandsDb = listOf(2.0f, -0.5f, 0.0f, 1.5f, 2.0f, 2.0f, 1.5f),
            surroundPercent = 30,
            bassBoostPercent = 20,
            dialogueClarityPercent = 25,
            preampDb = 2.0f
        )
    )

    // Row 2 — Standard TV Presets
    val standardPresets = listOf(
        AudioPreset(
            id = "flat",
            name = "Flat",
            subtitle = "Neutral uncolored response",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f),
            surroundPercent = 0,
            bassBoostPercent = 0,
            dialogueClarityPercent = 0,
            preampDb = 0.0f
        ),
        AudioPreset(
            id = "cinema_movie",
            name = "Cinema / Movie",
            subtitle = "Wide theater stage & sub-bass weight",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(3.5f, 0.5f, -1.5f, 1.5f, 3.0f, 2.0f, 2.5f),
            surroundPercent = 65,
            bassBoostPercent = 35,
            dialogueClarityPercent = 40,
            preampDb = 3.0f
        ),
        AudioPreset(
            id = "clear_dialogue",
            name = "Clear Dialogue",
            subtitle = "Elevated 1kHz-4kHz speech intelligibility",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(-2.0f, -2.5f, 1.0f, 3.5f, 4.5f, 2.0f, 0.0f),
            surroundPercent = 15,
            bassBoostPercent = 0,
            dialogueClarityPercent = 75,
            preampDb = 2.5f
        ),
        AudioPreset(
            id = "music",
            name = "Music",
            subtitle = "Lively stereo depth & crisp highs",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(2.5f, 0.5f, -0.5f, 1.0f, 2.0f, 2.5f, 2.0f),
            surroundPercent = 30,
            bassBoostPercent = 25,
            dialogueClarityPercent = 10,
            preampDb = 2.0f
        ),
        AudioPreset(
            id = "sports",
            name = "Sports",
            subtitle = "Stadium ambience + commentator presence",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(1.5f, 0.0f, 0.5f, 2.5f, 3.0f, 1.5f, 1.0f),
            surroundPercent = 55,
            bassBoostPercent = 15,
            dialogueClarityPercent = 50,
            preampDb = 2.5f
        ),
        AudioPreset(
            id = "night_mode",
            name = "Night Mode",
            subtitle = "Reduced bass rumble & boosted quiet speech",
            category = PresetCategory.STANDARD,
            bandsDb = listOf(-4.0f, -2.0f, 1.0f, 3.0f, 3.5f, 1.0f, -1.5f),
            surroundPercent = 10,
            bassBoostPercent = 0,
            dialogueClarityPercent = 65,
            preampDb = 4.0f
        )
    )

    val allPresets: List<AudioPreset> = signaturePresets + standardPresets
}
