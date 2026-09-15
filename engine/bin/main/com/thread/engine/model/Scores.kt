package com.thread.engine.model

/**
 * A score always carries the factors that produced it.
 *
 * This is not decoration. It powers the Studio view, it is how an offer explains
 * itself, and it means "why did it fire?" is always answerable.
 */
data class Factor(
    val name: String,
    /** Normalised 0..1 observation before weighting. */
    val raw: Double,
    /** Percentage points this factor contributed to [Score.value]. */
    val weighted: Double,
)

data class Score(
    val kind: ScoreKind,
    /** 0..100 */
    val value: Double,
    val factors: List<Factor>,
) {
    val explanation: String
        get() = factors
            .filter { it.weighted >= 1.0 }
            .sortedByDescending { it.weighted }
            .take(3)
            .joinToString("; ") { "${it.name} (${"%.0f".format(it.weighted)}pts)" }
            .ifEmpty { "no significant factors" }

    companion object {
        fun of(kind: ScoreKind, factors: List<Factor>): Score =
            Score(kind, factors.sumOf { it.weighted }.coerceIn(0.0, 100.0), factors)
    }
}

enum class ScoreKind {
    /** Screen Memory Load - design time, cached per screen template. */
    SML,

    /** Context Loss - computed on return from an interruption. */
    CLS,

    /** Disorientation: search loop / orbit. */
    DS_ORBIT,

    /** Disorientation: choice freeze. */
    DS_FREEZE,

    /** Disorientation: task initiation / scatter. */
    DS_INITIATION,
}
