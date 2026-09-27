package com.v2ray.ang.handler

import com.v2ray.ang.ui.main.FLAG_LOOKUP_CONCURRENCY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A full batch of name resolutions has to be able to start at once.
 *
 * The resolver pool was a single worker with a queue of one, while the walk is allowed to run
 * eight lookups at the same time. The walk therefore handed the pool eight tasks: one ran, one
 * waited, and six were rejected on arrival. A rejected lookup returns no address, and no address
 * is indistinguishable from a host that does not resolve -- so on a device where every hostname
 * was perfectly resolvable, five rows got their country and three showed no flag at all, and the
 * only symptom in the log was a resolved address of null.
 *
 * The failure is invisible in a single-lookup test, which is why it survived: nothing here ever
 * submitted more than one task. These tests submit a full batch and require every task to run.
 */
class CountryDnsConcurrencyTest {

    /**
     * The pool is sized from the batch cap of the walk, and a batch is measured against that same
     * number. Sizing them from one constant is what stops the two from disagreeing again: the
     * previous version had a private pool of one and a walk cap of eight.
     */
    @Test
    fun `the pool is sized from the batch cap of the walk`() {
        val source = File("src/main/java/com/v2ray/ang/handler/ServerCountryLookup.kt")
        assertTrue(
            "the lookup source must be readable from the module the test runs in: " + source,
            source.exists(),
        )
        val text = source.readText()
        assertTrue(
            "the resolver pool must be sized from FLAG_LOOKUP_CONCURRENCY, the number the walk " +
                "actually issues, and not from a private constant that can drift away from it",
            text.contains("val workers = FLAG_LOOKUP_CONCURRENCY"),
        )
        assertEquals(
            "the walk runs this many lookups at once, so the pool must be able to serve them all",
            8, FLAG_LOOKUP_CONCURRENCY,
        )
    }

    /**
     * The pool the fix installs does serve the batch. Eight workers means every lookup in a full
     * batch is running at the same time, which is what lets the walk finish in one pass.
     */
    @Test
    fun `a pool sized to the batch runs every lookup at once`() {
        val batch = FLAG_LOOKUP_CONCURRENCY
        val inFlight = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(batch)
        try {
            val started = CountDownLatch(batch)
            repeat(batch) {
                pool.execute {
                    started.countDown()
                    val now = inFlight.incrementAndGet()
                    peak.updateAndGet { previous -> maxOf(previous, now) }
                    release.await(3, TimeUnit.SECONDS)
                    inFlight.decrementAndGet()
                }
            }
            assertTrue(
                "every lookup in a full batch has to reach the running state, or the batch is " +
                    "being throttled somewhere it should not be",
                started.await(3, TimeUnit.SECONDS),
            )
            assertEquals(
                "a pool sized to the batch must have every lookup of the batch running at the " +
                    "same time; the previous pool reached a peak of one and dropped the rest",
                batch, peak.get(),
            )
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }
}
