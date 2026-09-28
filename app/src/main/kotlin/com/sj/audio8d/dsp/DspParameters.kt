package com.sj.audio8d.dsp

data class DspParameters(
    val enabled: Boolean = false,
    val pattern: MovementPattern = MovementPattern.CIRCULAR,
    val speed: MovementSpeed = MovementSpeed.NORMAL,
    val customSpeedHz: Float = 0.12f,
    val intensity: Float = 0.7f,
    val stereoWidth: Float = 0.6f,
    val bassBoost: Boolean = true,
    val bassBoostDb: Float = 4f,
    val reverbEnabled: Boolean = false,
    val reverbMix: Float = 0.12f
) {
    val effectiveSpeedHz: Float
        get() = if (speed == MovementSpeed.CUSTOM) customSpeedHz else speed.cyclesPerSecond

    companion object {
        val DEFAULT = DspParameters()
    }
}
