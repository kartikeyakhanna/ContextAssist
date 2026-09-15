package com.thread.engine

import com.thread.engine.model.Interruption
import com.thread.engine.model.TaskState
import com.thread.engine.model.ValueLookup
import com.thread.engine.scores.Cls
import com.thread.engine.scores.DsInitiation
import com.thread.engine.scores.DsOrbit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClsTest {

    private fun stateWith(
        interruptions: List<Interruption>,
        progress: Double = 0.5,
    ) = TaskState(
        taskId = "t1",
        intent = "Submitting Q3 travel claim",
        currentScreenId = "expense/allocation",
        startedAt = 0L,
        progress = progress,
        interruptions = interruptions,
    )

    @Test
    fun `a brief glance away does not earn an interruption`() {
        val state = stateWith(
            listOf(Interruption(leftAt = 0, returnedAt = 5_000, progressAtExit = 0.5, toPackage = null)),
        )
        val score = Cls.score(state, smlForScreen = 40.0)
        assertTrue(score.value < 30, "expected silence band, got ${score.value}")
    }

    @Test
    fun `Maya's interruption - four minutes, mid-task, disoriented on return - fires`() {
        val state = stateWith(
            listOf(
                Interruption(0, 30_000, 0.2, "teams"),
                Interruption(60_000, 300_000, 0.5, "com.microsoft.teams"),
            ),
            progress = 0.5,
        )
        val score = Cls.score(
            state,
            signals = Cls.PostReturnSignals(
                scrollReversals = 3,
                focusWithoutEdit = 2,
                secondsBeforeFirstAction = 18.0,
            ),
            smlForScreen = 78.0,
        )
        assertTrue(score.value >= 60, "expected offer band, got ${score.value} (${score.explanation})")
    }

    @Test
    fun `same interruption hurts more in the middle of a task than at the end`() {
        val middle = Cls.score(
            stateWith(listOf(Interruption(0, 300_000, 0.5, null))),
            smlForScreen = 50.0,
        )
        val nearlyDone = Cls.score(
            stateWith(listOf(Interruption(0, 300_000, 0.98, null))),
            smlForScreen = 50.0,
        )
        assertTrue(middle.value > nearlyDone.value)
    }

    @Test
    fun `score carries its factors so it can always explain why it fired`() {
        val score = Cls.score(stateWith(listOf(Interruption(0, 300_000, 0.5, null))))
        assertEquals(5, score.factors.size)
        assertTrue(score.explanation.contains("mid-task") || score.explanation.contains("time away"))
    }
}

class DsOrbitTest {

    @Test
    fun `fetching the same value repeatedly is detected and is pinnable`() {
        val state = TaskState(
            taskId = "t1",
            intent = "Submitting Q3 travel claim",
            currentScreenId = "expense/allocation",
            startedAt = 0L,
            visitCounts = mapOf("expense/allocation" to 6, "budget/lookup" to 5),
            lookups = listOf(
                ValueLookup(1000, "expense/allocation", "Budget code", "DEL-4471", "com.android.chrome"),
                ValueLookup(2000, "expense/allocation", "Budget code", "DEL-4471", "com.android.chrome"),
                ValueLookup(3000, "expense/allocation", "Budget code", "DEL-4471", "com.android.chrome"),
            ),
        )

        val score = DsOrbit.score(
            state,
            DsOrbit.OrbitSignals(deadEndReturns = 3, zeroProgressSeconds = 400.0),
        )
        assertTrue(score.value >= 60, "expected offer band, got ${score.value}")

        val pin = OfferComposer.pin(state, score)
        assertNotNull(pin)
        assertEquals("DEL-4471", pin.value)
    }

    @Test
    fun `hitting the same validation error repeatedly is detected`() {
        val state = TaskState(
            taskId = "t1",
            intent = "Submitting claim",
            currentScreenId = "expense/details",
            startedAt = 0L,
            errorCounts = mapOf("date must be after #" to 4),
            visitCounts = mapOf("expense/details" to 4),
        )
        assertNotNull(state.repeatedError())
        assertTrue(DsOrbit.score(state, DsOrbit.OrbitSignals(zeroProgressSeconds = 200.0)).value > 40)
    }

    @Test
    fun `steady progress through a task does not look like orbiting`() {
        val state = TaskState(
            taskId = "t1",
            intent = "Submitting claim",
            currentScreenId = "expense/review",
            startedAt = 0L,
            completed = List(6) {
                com.thread.engine.model.FieldSnapshot("f$it", "Field $it", "v", it * 1000L)
            },
            visitCounts = mapOf("a" to 1, "b" to 1, "c" to 1),
        )
        assertTrue(DsOrbit.score(state).value < 30)
    }
}

class DsInitiationTest {

    @Test
    fun `scattered focus on a heavy form with nothing filled in fires`() {
        val score = DsInitiation.score(
            DsInitiation.InitiationSignals(
                fieldsCommitted = 0,
                focusWithoutEdit = 5,
                fieldFocusOrder = listOf(1, 8, 3, 15, 2),
                scrollReversals = 5,
                secondsOnScreen = 75.0,
            ),
            smlForScreen = 85.0,
        )
        assertTrue(score.value >= 60, "expected offer band, got ${score.value} (${score.explanation})")
    }

    @Test
    fun `committing even one field clears the strongest factor`() {
        val base = DsInitiation.InitiationSignals(
            focusWithoutEdit = 5,
            fieldFocusOrder = listOf(1, 8, 3, 15, 2),
            scrollReversals = 5,
            secondsOnScreen = 75.0,
        )
        val stalled = DsInitiation.score(base.copy(fieldsCommitted = 0), smlForScreen = 85.0)
        val started = DsInitiation.score(base.copy(fieldsCommitted = 1), smlForScreen = 85.0)

        assertEquals(
            0.0,
            started.factors.first { it.name == "nothing started yet" }.weighted,
            0.001,
        )
        assertEquals(
            Weights.DEFAULT.initiationZeroCommits,
            stalled.value - started.value,
            0.001,
            "starting the task must remove exactly the zero-commit weight",
        )
    }

    @Test
    fun `the user is not judged in the first seconds on a screen`() {
        val score = DsInitiation.score(
            DsInitiation.InitiationSignals(
                focusWithoutEdit = 3,
                fieldFocusOrder = listOf(1, 9, 2),
                secondsOnScreen = 4.0,
            ),
            smlForScreen = 90.0,
        )
        assertEquals(0.0, score.value, 0.001)
    }
}
