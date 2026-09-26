package com.sj.audio8d.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sj.audio8d.dsp.DspParameters
import com.sj.audio8d.dsp.MovementPattern
import com.sj.audio8d.dsp.MovementSpeed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "audio8d_settings")

/**
 * Persists the user's default 8D preset and app-level settings locally via
 * Jetpack DataStore. Nothing here ever leaves the device.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("dsp_enabled_default")
        val PATTERN = stringPreferencesKey("dsp_pattern")
        val SPEED = stringPreferencesKey("dsp_speed")
        val CUSTOM_SPEED = floatPreferencesKey("dsp_custom_speed_hz")
        val INTENSITY = floatPreferencesKey("dsp_intensity")
        val WIDTH = floatPreferencesKey("dsp_width")
        val BASS = booleanPreferencesKey("dsp_bass_boost")
        val BASS_DB = floatPreferencesKey("dsp_bass_db")
        val REVERB = booleanPreferencesKey("dsp_reverb_enabled")
        val REVERB_MIX = floatPreferencesKey("dsp_reverb_mix")
        val AUTO_NEXT = booleanPreferencesKey("auto_next")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
    }

    val defaultDspParameters: Flow<DspParameters> = context.dataStore.data.map { prefs ->
        DspParameters(
            enabled = prefs[Keys.ENABLED] ?: false,
            pattern = runCatching { MovementPattern.valueOf(prefs[Keys.PATTERN] ?: "") }
                .getOrDefault(MovementPattern.CIRCULAR),
            speed = runCatching { MovementSpeed.valueOf(prefs[Keys.SPEED] ?: "") }
                .getOrDefault(MovementSpeed.NORMAL),
            customSpeedHz = prefs[Keys.CUSTOM_SPEED] ?: 0.12f,
            intensity = prefs[Keys.INTENSITY] ?: 0.7f,
            stereoWidth = prefs[Keys.WIDTH] ?: 0.6f,
            bassBoost = prefs[Keys.BASS] ?: true,
            bassBoostDb = prefs[Keys.BASS_DB] ?: 4f,
            reverbEnabled = prefs[Keys.REVERB] ?: false,
            reverbMix = prefs[Keys.REVERB_MIX] ?: 0.12f
        )
    }

    val autoNext: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_NEXT] ?: true }
    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[Keys.PLAYBACK_SPEED] ?: 1.0f }

    suspend fun saveDefaultDspParameters(params: DspParameters) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ENABLED] = params.enabled
            prefs[Keys.PATTERN] = params.pattern.name
            prefs[Keys.SPEED] = params.speed.name
            prefs[Keys.CUSTOM_SPEED] = params.customSpeedHz
            prefs[Keys.INTENSITY] = params.intensity
            prefs[Keys.WIDTH] = params.stereoWidth
            prefs[Keys.BASS] = params.bassBoost
            prefs[Keys.BASS_DB] = params.bassBoostDb
            prefs[Keys.REVERB] = params.reverbEnabled
            prefs[Keys.REVERB_MIX] = params.reverbMix
        }
    }

    suspend fun setAutoNext(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_NEXT] = value }
    }

    suspend fun setPlaybackSpeed(value: Float) {
        context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = value }
    }
}
