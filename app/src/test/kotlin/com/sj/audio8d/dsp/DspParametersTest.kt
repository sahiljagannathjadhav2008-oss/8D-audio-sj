package com.sj.audio8d.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class DspParametersTest {

    @Test
    fun `default parameters are sane and off by default`() {
        val defaults = DspParameters.DEFAULT
        assertEquals(false, defaults.enabled)
        assertEquals(MovementPattern.CIRCULAR, defaults.pattern)
        assertEquals(MovementSpeed.NORMAL, defaults.speed)
    }

    @Test
    fun `effective speed uses preset unless CUSTOM is selected`() {
        val normal = DspParameters.DEFAULT.copy(speed = MovementSpeed.FAST, customSpeedHz = 0.99f)
        assertEquals(MovementSpeed.FAST.cyclesPerSecond, normal.effectiveSpeedHz)

        val custom = DspParameters.DEFAULT.copy(speed = MovementSpeed.CUSTOM, customSpeedHz = 0.33f)
        assertEquals(0.33f, custom.effectiveSpeedHz)
    }
}
