package com.thread.engine.scores

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2

/** Shared normalisation helpers. Every factor must land in 0..1 before weighting. */
internal object Normalise {

    fun clamp01(v: Double): Double = v.coerceIn(0.0, 1.0)

    /** Linear ramp to [full], then saturated. */
    fun ratio(count: Number, full: Double): Double =
        clamp01(count.toDouble() / full)

    /**
     * Log-scaled duration. Short interruptions cost little; the curve rises
     * steeply through the first few minutes and plateaus at [plateauSeconds].
     */
    fun logDuration(seconds: Double, plateauSeconds: Double = 1800.0, kneeSeconds: Double = 30.0): Double {
        if (seconds <= 0.0) return 0.0
        val capped = seconds.coerceAtMost(plateauSeconds)
        return clamp01(ln(1 + capped / kneeSeconds) / ln(1 + plateauSeconds / kneeSeconds))
    }

    /**
     * Hick's Law: choice reaction time grows with log2(n + 1). Option counts are
     * never treated linearly - 20 options is not twice as hard as 10.
     */
    fun hick(optionCount: Int, saturationCount: Int = 32): Double {
        if (optionCount <= 0) return 0.0
        return clamp01(log2(1.0 + optionCount) / log2(1.0 + saturationCount))
    }

    /**
     * Compounding cost of repeated interruption. The first interruption scores 0
     * on this factor - the others carry it. By the fifth, it dominates.
     */
    fun compounding(n: Int, rate: Double = 0.5): Double {
        if (n <= 1) return 0.0
        return clamp01(1.0 - exp(-rate * (n - 1)))
    }

    /**
     * Context loss as a function of where the user was when interrupted.
     *
     * The non-obvious one: loss peaks mid-task. Interrupted at 5% you have lost
     * nothing; at 95% you are nearly done and the remaining step is obvious. At
     * 50% you have the most state in your head - and that is exactly where
     * abandonment happens.
     *
     * Trapezoid: full weight across 0.3..0.7, ramping either side.
     */
    fun midTaskPeak(progress: Double): Double = when {
        progress <= 0.0 || progress >= 1.0 -> 0.0
        progress < 0.3 -> progress / 0.3
        progress <= 0.7 -> 1.0
        else -> (1.0 - progress) / 0.3
    }

    /**
     * Path entropy: breadth of screens touched relative to work actually
     * committed. High breadth with no commits is the signature of searching.
     */
    fun pathEntropy(screensVisited: Int, actionsCommitted: Int): Double {
        if (screensVisited <= 1) return 0.0
        val ratio = screensVisited.toDouble() / (actionsCommitted + 1).toDouble()
        return clamp01((ratio - 1.0) / 4.0)
    }

    /**
     * Scatter: are field visits sequential (working through the form) or jumping
     * around (cannot find a starting point)? Returns 0 for perfectly sequential.
     */
    fun scatter(fieldIndices: List<Int>): Double {
        if (fieldIndices.size < 3) return 0.0
        val jumps = fieldIndices.zipWithNext().count { (a, b) -> abs(b - a) > 1 }
        return clamp01(jumps.toDouble() / (fieldIndices.size - 1).toDouble())
    }
}
