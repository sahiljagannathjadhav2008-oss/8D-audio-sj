package com.sj.audio8d.dsp

enum class MovementPattern {
    CIRCULAR,
    HORIZONTAL,
    VERTICAL,
    RANDOM
}

enum class MovementSpeed(val cyclesPerSecond: Float) {
    SLOW(0.06f),
    NORMAL(0.12f),
    FAST(0.22f),
    CUSTOM(0.12f)
}
