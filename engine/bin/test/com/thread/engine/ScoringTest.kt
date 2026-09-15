package com.thread.engine

import com.thread.engine.model.ScoreKind
import com.thread.engine.scores.Normalise
import com.thread.engine.scores.Sml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormaliseTest {

    @Test
    fun `context loss peaks mid-task, not at the end`() {
        val early = Normalise.midTaskPeak(0.05)
        val middle = Normalise.midTaskPeak(0.5)
        val late = Normalise.midTaskPeak(0.95)

        assertTrue(middle > early, "mid-task must cost more than just-started")
        assertTrue(middle > late, "mid-task must cost more than nearly-finished")
        assertEquals(1.0, middle, 0.001)
    }

    @Test
    fun `interrupted at the very start or very end loses nothing`() {
        assertEquals(0.0, Normalise.midTaskPeak(0.0), 0.001)
        assertEquals(0.0, Normalise.midTaskPeak(1.0), 0.001)
    }

    @Test
    fun `option count is log-scaled, so twenty options is not twice ten`() {
        val ten = Normalise.hick(10)
        val twenty = Normalise.hick(20)
        assertTrue(twenty < ten * 2.0, "Hick's Law: growth must be logarithmic")
        assertTrue(twenty > ten)
    }

    @Test
    fun `interruption duration rises steeply then plateaus`() {
        val thirtySeconds = Normalise.logDuration(30.0)
        val fiveMinutes = Normalise.logDuration(300.0)
        val thirtyMinutes = Normalise.logDuration(1800.0)
        val twoHours = Normalise.logDuration(7200.0)

        assertTrue(thirtySeconds < fiveMinutes)
        assertTrue(fiveMinutes < thirtyMinutes)
        assertEquals(thirtyMinutes, twoHours, 0.001, "must plateau, not grow forever")
    }

    @Test
    fun `repeated interruptions compound`() {
        assertEquals(0.0, Normalise.compounding(1), 0.001)
        assertTrue(Normalise.compounding(3) > Normalise.compounding(2))
        assertTrue(Normalise.compounding(6) > 0.9)
    }

    @Test
    fun `working through a form sequentially is not scatter`() {
        assertEquals(0.0, Normalise.scatter(listOf(1, 2, 3, 4, 5)), 0.001)
    }

    @Test
    fun `jumping between distant fields is scatter`() {
        assertTrue(Normalise.scatter(listOf(1, 9, 3, 14, 2)) > 0.9)
    }

    @Test
    fun `every factor stays within zero and one`() {
        val values = listOf(
            Normalise.hick(1000),
            Normalise.logDuration(999_999.0),
            Normalise.compounding(500),
            Normalise.ratio(9999, 3.0),
            Normalise.pathEntropy(500, 0),
        )
        assertTrue(values.all { it in 0.0..1.0 }, "got $values")
    }
}

class SmlTest {

    private val overloadedForm = Sml.ScreenFacts(
        screenId = "expense/allocation",
        itemsToHold = 8,
        optionCount = 25,
        progressVisible = false,
        irreversibleActions = 1,
        crossReferences = 3,
        languageComplexity = 0.7,
    )

    private val simpleScreen = Sml.ScreenFacts(
        screenId = "expense/details",
        itemsToHold = 1,
        optionCount = 2,
        progressVisible = true,
        irreversibleActions = 0,
        crossReferences = 0,
        languageComplexity = 0.1,
    )

    @Test
    fun `the deliberately overloaded screen scores high`() {
        val score = Sml.score(overloadedForm)
        assertTrue(score.value > 70, "expected >70, got ${score.value}")
        assertEquals(ScoreKind.SML, score.kind)
    }

    @Test
    fun `a simple screen scores low`() {
        assertTrue(Sml.score(simpleScreen).value < 25)
    }

    @Test
    fun `score always explains itself`() {
        val score = Sml.score(overloadedForm)
        assertEquals(6, score.factors.size)
        assertTrue(score.explanation.isNotBlank())
    }

    @Test
    fun `unknown screens fall back to neutral rather than guessing`() {
        assertEquals(Sml.NEUTRAL, Sml.lookup("never/seen", emptyMap()))
        assertEquals(82.0, Sml.lookup("known", mapOf("known" to 82.0)))
    }
}
