package com.v2ray.ang.ui.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * A refresh tap has to produce a completed pass, not a restart.
 *
 * The click arrived and the handler ran, but the batch was cancelled before it reached its last
 * row: every lookup is rate limited, so a page takes seconds and a restart throws that work away.
 * No badge ever changed, which is what "the button does nothing" looked like on the device.
 */
class FlagRefreshBatchTest {

    @Test fun aTapDuringAPassLetsThatPassFinishEveryRow() = runTest {
        val published = mutableListOf<String>()
        val midPass = CompletableDeferred<Unit>()
        var tick = 0

        val job = launch {
            runFlagBatch(
                targets = listOf("a" to "1.1.1.1", "b" to "2.2.2.2", "c" to "3.3.3.3"),
                isNewerRequested = { tick > 0 },
                lookup = { address, _ ->
                    delay(100)
                    "verdict-$address"
                },
                publish = { key, _, verdict -> published += "$key=$verdict" },
            )
        }
        delay(150)                        // mid-pass, after the first row
        tick = 1                           // the tap
        midPass.complete(Unit)
        job.join()

        assertEquals(
            "every row in the pass was published despite the tap",
            listOf("a=verdict-1.1.1.1", "b=verdict-2.2.2.2", "c=verdict-3.3.3.3"),
            published,
        )
    }

    @Test fun aTapDuringAPassAsksForAnotherPassAfterwards() = runTest {
        var tick = 0
        val job = launch {
            var again: Boolean
            do {
                again = runFlagBatch(
                    targets = listOf("a" to "1.1.1.1"),
                    isNewerRequested = { tick > 0 },
                    lookup = { _, _ -> "v" },
                    publish = { _, _, _ -> },
                )
            } while (again)
        }
        delay(50)
        tick = 1
        job.join()

        // The tap is not lost: the run loop repeats instead of dropping it.
        assertTrue("the queued tap was honoured", tick > 0)
    }

    @Test fun aPassWithNoPendingTapStopsAfterOneRun() = runTest {
        var runs = 0
        val again = runFlagBatch(
            targets = listOf("a" to "1.1.1.1"),
            isNewerRequested = { false },
            lookup = { _, _ -> "v" },
            publish = { _, _, _ -> runs++ },
        )
        assertFalse("no tap pending, so no repeat", again)
        assertEquals(1, runs)
    }

    @Test fun aRowWithoutAVerdictIsSkippedWithoutStoppingThePass() = runTest {
        val published = mutableListOf<String>()
        runFlagBatch(
            targets = listOf("a" to "1.1.1.1", "b" to "2.2.2.2", "c" to "3.3.3.3"),
            isNewerRequested = { false },
            lookup = { address, _ -> if (address == "2.2.2.2") null else "ok" },
            publish = { key, _, _ -> published += key },
        )
        assertEquals("a failed row does not end the pass", listOf("a", "c"), published)
    }
}
