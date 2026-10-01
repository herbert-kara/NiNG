package com.v2ray.ang.dto

import java.io.Serializable
import kotlin.math.roundToInt

/** One persisted delay-test result delivered from the task process to the UI. */
data class RealPingResult(
    val guid: String,
    /** The median of the samples taken, or -1 when none of them answered. */
    val delayMillis: Long,
    /** Spread between the fastest and the slowest sample. A large value is what drops a call. */
    val jitterMillis: Long = 0L,
    /** Share of samples that never answered, 0-100. */
    val lossPercent: Int = 0,
    /** How many samples were taken; 1 means the run predates multi-sample testing. */
    val samples: Int = 1,
    /**
     * The ranking number the run produced, carried rather than recomputed. Rebuilding it from the
     * three numbers above would need the original samples: a median plus a loss count describes a
     * jitter of zero, which scores better than the server that produced it actually is.
     */
    val score: Int = 0,
) : Serializable

/**
 * What several measurements of the same profile actually say about it.
 *
 * The batch test used to take one measurement and keep it, which made the resulting order a
 * snapshot of a lucky moment. Three samples cost more wall-clock time and answer a different
 * question: not how fast was this server once, but is this server dependable.
 */
object RealPingSample {

    /** Reported by the core when a measurement did not complete. */
    const val FAILED: Long = -1L

    data class Stats(
        val delayMillis: Long,
        val jitterMillis: Long,
        val lossPercent: Int,
        val samples: Int,
        /**
         * A ranking number, not a speed. A server that is fast half the time and dead the other
         * half scores below one that is slower every time, which is the order a person browsing
         * a list actually wants. Loss dominates: there is no ordering of "fast" that beats "does
         * not work".
         */
        val score: Int,
    )

    fun summarize(samples: List<Long>): Stats {
        if (samples.isEmpty()) {
            return Stats(FAILED, 0, 100, 0, 0)
        }
        val answered = samples.filter { it > 0L }.sorted()
        val loss = ((samples.size - answered.size) * 100.0 / samples.size).roundToInt()
        if (answered.isEmpty()) {
            return Stats(FAILED, 0, loss, samples.size, 0)
        }
        val median = answered[answered.size / 2]
        val jitter = answered.last() - answered.first()

        // Each term is a ratio in 0..1, and the weights sum to 100, so a perfect server scores 100
        // and nothing can be pushed outside that by a slow-but-usable one.
        val speed = (1.0 - (median - SPEED_FLOOR_MS) / (SPEED_CEIL_MS - SPEED_FLOOR_MS))
            .coerceIn(0.0, 1.0)
        val stability = (1.0 - jitter.toDouble() / JITTER_CEIL_MS).coerceIn(0.0, 1.0)
        val reliability = 1.0 - loss / 100.0
        val score = (speed * SPEED_WEIGHT + stability * STABILITY_WEIGHT + reliability * RELIABILITY_WEIGHT)
            .roundToInt()

        return Stats(median, jitter, loss, samples.size, score)
    }

    private const val SPEED_FLOOR_MS = 0.0
    private const val SPEED_CEIL_MS = 1000.0
    private const val JITTER_CEIL_MS = 500.0

    /** A server that drops a third of its samples is not a fast server, it is an unusable one. */
    private const val RELIABILITY_WEIGHT = 50.0
    private const val SPEED_WEIGHT = 30.0
    private const val STABILITY_WEIGHT = 20.0
}
