package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The sort used to order a subscription by a single delay measurement, which put a server that
 * answered once at 60ms above one that answers every time at 150ms, and a server that lost its
 * single sample somewhere it would otherwise have been mid-list.
 */
class TestResultSortTest {

    /** Stands in for what a batch test has recorded for one server. */
    data class Row(
        val guid: String,
        val delayMillis: Long,
        val score: Int,
        val hasScore: Boolean,
    )

    /**
     * The rule, kept here so it can be reasoned about without storage or Android. Mirrored by
     * AngConfigManager.sortByTestResultsForSub, and the guard in verify_brand.py checks the two
     * agree.
     */
    fun order(rows: List<Row>): List<String> =
        rows.sortedWith(
            compareByDescending<Row> { it.score.takeIf { s -> it.hasScore && s > 0 } ?: 0 }
                .thenBy { if (it.delayMillis <= 0L) Long.MAX_VALUE else it.delayMillis }
                .thenBy { it.guid }
        ).map { it.guid }

    private fun scored(guid: String, delay: Long, score: Int) = Row(guid, delay, score, true)

    @Test
    fun `a dependable 150ms server ranks above a 60ms server that never answers`() {
        val sorted = order(
            listOf(
                scored("jumpy-but-fast", 60, 78),
                scored("steady", 150, 95),
            )
        )
        assertEquals(listOf("steady", "jumpy-but-fast"), sorted)
    }

    @Test
    fun `a server that never answered goes to the bottom`() {
        val sorted = order(
            listOf(
                scored("dead", -1, 0),
                scored("working", 200, 90),
            )
        )
        assertEquals(listOf("working", "dead"), sorted)
    }

    @Test
    fun `untested servers keep their relative order behind tested ones`() {
        val sorted = order(
            listOf(
                Row("never-tested-a", 0, 0, false),
                scored("tested", 300, 60),
                Row("never-tested-b", 0, 0, false),
            )
        )
        assertEquals(listOf("tested", "never-tested-a", "never-tested-b"), sorted)
    }

    @Test
    fun `rows from an old run with no score still sort by delay`() {
        // the record predates multi-sample testing, so it has a delay and nothing else; the sort
        // has to keep working for it rather than treat every server as equal
        val sorted = order(
            listOf(
                Row("old-fast", 80, 0, false),
                Row("old-slow", 400, 0, false),
            )
        )
        assertEquals(listOf("old-fast", "old-slow"), sorted)
    }

    @Test
    fun `a tie on score falls back to delay`() {
        val sorted = order(
            listOf(
                scored("slower", 250, 80),
                scored("faster", 120, 80),
            )
        )
        assertEquals(listOf("faster", "slower"), sorted)
    }

    @Test
    fun `the sort is stable for identical rows so repeated runs do not reshuffle the list`() {
        val sorted = order(
            listOf(
                scored("a", 100, 90),
                scored("b", 100, 90),
                scored("c", 100, 90),
            )
        )
        assertEquals(listOf("a", "b", "c"), sorted)
    }

    @Test
    fun `a heavily lossy server ranks below a slow clean one`() {
        val sorted = order(
            listOf(
                scored("fast-but-drops", 90, 64),
                scored("slow-but-solid", 300, 88),
            )
        )
        assertEquals(listOf("slow-but-solid", "fast-but-drops"), sorted)
    }
}
