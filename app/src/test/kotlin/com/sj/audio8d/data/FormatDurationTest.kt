package com.sj.audio8d.data

import com.sj.audio8d.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatDurationTest {

    @Test
    fun `formats sub-minute durations`() {
        assertEquals("0:09", formatDuration(9_000))
    }

    @Test
    fun `formats multi-minute durations with zero padded seconds`() {
        assertEquals("4:04", formatDuration(244_000))
    }

    @Test
    fun `formats exact minute boundaries`() {
        assertEquals("2:00", formatDuration(120_000))
    }

    @Test
    fun `zero duration formats as zero`() {
        assertEquals("0:00", formatDuration(0))
    }
}
