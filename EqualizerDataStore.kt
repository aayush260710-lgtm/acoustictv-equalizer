package com.acoustictv.equalizer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.acoustictv.equalizer.model.AtmosAppliedDeltas
import com.acoustictv.equalizer.model.EqualizerCatalog
import com.acoustictv.equalizer.model.EqualizerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "acoustictv_equalizer_prefs"
)

class EqualizerRepository(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val DOLBY_ATMOS_ENABLED = booleanPreferencesKey("dolby_atmos_enabled")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val ACTIVE_PRESET_NAME = stringPreferencesKey("active_preset_name")
        val LAST_PRESET_ID = stringPreferencesKey("last_preset_id")

        val BAND_0 = floatPreferencesKey("band_0_60hz")
        val BAND_1 = floatPreferencesKey("band_1_150hz")
        val BAND_2 = floatPreferencesKey("band_2_400hz")
        val BAND_3 = floatPreferencesKey("band_3_1khz")
        val BAND_4 = floatPreferencesKey("band_4_2_4khz")
        val BAND_5 = floatPreferencesKey("band_5_6khz")
        val BAND_6 = floatPreferencesKey("band_6_15khz")

        val SURROUND_PERCENT = intPreferencesKey("surround_percent")
        val BASS_BOOST_PERCENT = intPreferencesKey("bass_boost_percent")
        val DIALOGUE_CLARITY_PERCENT = intPreferencesKey("dialogue_clarity_percent")
        val PREAMP_DB = floatPreferencesKey("preamp_db")

        val ATMOS_DELTA_60HZ = floatPreferencesKey("atmos_delta_60hz")
        val ATMOS_DELTA_15KHZ = floatPreferencesKey("atmos_delta_15khz")
        val ATMOS_DELTA_SURROUND = intPreferencesKey("atmos_delta_surround")
        val ATMOS_DELTA_PREAMP = floatPreferencesKey("atmos_delta_preamp")
    }

    val stateFlow: Flow<EqualizerState> = context.equalizerDataStore.data.map { prefs ->
        val defaultPreset = EqualizerCatalog.signaturePresets.first()
        val bands = listOf(
            prefs[Keys.BAND_0] ?: defaultPreset.bandsDb[0],
            prefs[Keys.BAND_1] ?: defaultPreset.bandsDb[1],
            prefs[Keys.BAND_2] ?: defaultPreset.bandsDb[2],
            prefs[Keys.BAND_3] ?: defaultPreset.bandsDb[3],
            prefs[Keys.BAND_4] ?: defaultPreset.bandsDb[4],
            prefs[Keys.BAND_5] ?: defaultPreset.bandsDb[5],
            prefs[Keys.BAND_6] ?: defaultPreset.bandsDb[6]
        )

        EqualizerState(
            masterEnabled = prefs[Keys.MASTER_ENABLED] ?: true,
            dolbyAtmosEnabled = prefs[Keys.DOLBY_ATMOS_ENABLED] ?: false,
            activePresetId = prefs[Keys.ACTIVE_PRESET_ID] ?: defaultPreset.id,
            activePresetName = prefs[Keys.ACTIVE_PRESET_NAME] ?: defaultPreset.name,
            lastPresetId = prefs[Keys.LAST_PRESET_ID] ?: defaultPreset.id,
            bandsDb = bands,
            surroundPercent = prefs[Keys.SURROUND_PERCENT] ?: defaultPreset.surroundPercent,
            bassBoostPercent = prefs[Keys.BASS_BOOST_PERCENT] ?: defaultPreset.bassBoostPercent,
            dialogueClarityPercent = prefs[Keys.DIALOGUE_CLARITY_PERCENT]
                ?: defaultPreset.dialogueClarityPercent,
            preampDb = prefs[Keys.PREAMP_DB] ?: defaultPreset.preampDb,
            atmosApplied = AtmosAppliedDeltas(
                band60HzDelta = prefs[Keys.ATMOS_DELTA_60HZ] ?: 0f,
                band15kHzDelta = prefs[Keys.ATMOS_DELTA_15KHZ] ?: 0f,
                surroundDelta = prefs[Keys.ATMOS_DELTA_SURROUND] ?: 0,
                preampDelta = prefs[Keys.ATMOS_DELTA_PREAMP] ?: 0f
            )
        )
    }

    suspend fun saveState(state: EqualizerState) {
        context.equalizerDataStore.edit { prefs ->
            prefs[Keys.MASTER_ENABLED] = state.masterEnabled
            prefs[Keys.DOLBY_ATMOS_ENABLED] = state.dolbyAtmosEnabled
            prefs[Keys.ACTIVE_PRESET_ID] = state.activePresetId
            prefs[Keys.ACTIVE_PRESET_NAME] = state.activePresetName
            prefs[Keys.LAST_PRESET_ID] = state.lastPresetId

            prefs[Keys.BAND_0] = state.bandsDb.getOrElse(0) { 0f }
            prefs[Keys.BAND_1] = state.bandsDb.getOrElse(1) { 0f }
            prefs[Keys.BAND_2] = state.bandsDb.getOrElse(2) { 0f }
            prefs[Keys.BAND_3] = state.bandsDb.getOrElse(3) { 0f }
            prefs[Keys.BAND_4] = state.bandsDb.getOrElse(4) { 0f }
            prefs[Keys.BAND_5] = state.bandsDb.getOrElse(5) { 0f }
            prefs[Keys.BAND_6] = state.bandsDb.getOrElse(6) { 0f }

            prefs[Keys.SURROUND_PERCENT] = state.surroundPercent
            prefs[Keys.BASS_BOOST_PERCENT] = state.bassBoostPercent
            prefs[Keys.DIALOGUE_CLARITY_PERCENT] = state.dialogueClarityPercent
            prefs[Keys.PREAMP_DB] = state.preampDb

            prefs[Keys.ATMOS_DELTA_60HZ] = state.atmosApplied.band60HzDelta
            prefs[Keys.ATMOS_DELTA_15KHZ] = state.atmosApplied.band15kHzDelta
            prefs[Keys.ATMOS_DELTA_SURROUND] = state.atmosApplied.surroundDelta
            prefs[Keys.ATMOS_DELTA_PREAMP] = state.atmosApplied.preampDelta
        }
    }
}
