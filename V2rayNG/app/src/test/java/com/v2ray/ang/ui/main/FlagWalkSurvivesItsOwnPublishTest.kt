package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page showed a verdict on one row and nothing on the rest, forever, and the country flag
 * never appeared at all.
 *
 * The cause was not the lookup and not the parser: both walks were `collectLatest` blocks over a
 * flow derived from the row list, and publishing a result edits that row list. The first row
 * answered, its own answer produced a new `targets` emission, and collectLatest tore the pass down
 * with every remaining row unasked. Reopening the screen repeated it for whichever row happened to
 * be first, which is why the flag looked intermittent rather than absent.
 *
 * The fix puts each walk in a job the collector does not own. These assert the property that
 * catches it: a walk that publishes into the state it reads from must still finish every target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlagWalkSurvivesItsOwnPublishTest {

    private data class Row(val guid: String, val address: String, val country: String? = null)

    @Test
    fun aWalkThatPublishesIntoTheListItReadsStillFinishesEveryRow() = runTest {
        val rows = MutableStateFlow((1..6).map { Row("g$it", "10.0.0.$it") })
        val published = mutableListOf<String>()

        // The shape under test: derive the targets from a state flow, then publish into it.
        val walk = async {
            rows.map { state -> state.map { it.guid to it.address } }
                .distinctUntilChanged()
                .first()            // take one snapshot, the way the fixed collectors do
            runFlagBatch(
                targets = walk,
                force = false,
                lookup = { address, _ ->
                    delay(10)
                    "DE"
                },
                publish = { guid, address, country ->
                    published += guid
                    rows.value = rows.value.map {
                        if (it.guid == guid) it.copy(country = country) else it
                    }
                },
            )
        }
        walk.await()

        assertEquals(
            "a walk cancelled by its own publish leaves most of the page unasked, which is the " +
                "reported symptom: one row answered and the rest blank",
            listOf("g1", "g2", "g3", "g4", "g5", "g6"),
            published,
        )
        assertEquals("every row should end up with the country", 6, rows.value.count { it.country == "DE" })
    }

    @Test
    fun twoWalksOverTheSameRowsDoNotCancelEachOther() = runTest {
        val rows = MutableStateFlow((1..4).map { Row("g$it", "10.0.1.$it") })
        val verdicts = mutableListOf<String>()
        val countries = mutableListOf<String>()

        // The verdict walk and the country walk used to share one job slot per group, so whichever
        // collector emitted last cancelled the other and the page showed a verdict with no country
        // or the reverse.
        val verdictJob = async {
            val targets = rows.value.map { it.guid to it.address }
            runFlagBatch(
                targets = targets, force = true,
                lookup = { _, _ -> delay(5); FlagVerdictForTest },
                publish = { guid, _, _ ->
                    verdicts += guid
                    rows.value = rows.value.map { if (it.guid == guid) it.copy(country = "DE") else it }
                },
            )
        }
        val countryJob = async {
            val targets = rows.value.map { it.guid to it.address }
            runFlagBatch(
                targets = targets, force = true,
                lookup = { _, _ -> delay(5); CountryOnly("DE") },
                publish = { guid, _, _ -> countries += guid },
            )
        }
        coroutineScope { awaitAll(verdictJob, countryJob) }

        assertEquals("the verdict walk was cut short", 4, verdicts.size)
        assertEquals("the country walk was cut short by the verdict walk", 4, countries.size)
    }

    private object FlagVerdictForTest

    @Test
    fun everyTargetOfTheReportedPageIsAskedEvenWhenTheFirstAnswersImmediately() = runTest {
        // The reported page: two rows on the same host, three on other hosts, all of them
        // answering. A walk that stops after the first is the bug in its smallest form.
        val rows = listOf(
            Row("a", "5.180.82.45"), Row("b", "5.180.82.45"),
            Row("c", "bow.tehtanshop.com"), Row("d", "landing.tehtanshop.com"),
        )
        val asked = mutableListOf<String>()
        val state = MutableStateFlow(rows)
        coroutineScope {
            state.map { it.map { r -> r.guid to r.address } }.distinctUntilChanged().first()
            runFlagBatch(
                targets = rows.map { it.guid to it.address },
                force = true,
                lookup = { address, _ -> asked += address; null },
                publish = { _, _, _ -> },
            )
        }
        assertEquals("only the first row was asked", 4, asked.size)
        assertEquals(2, asked.count { it == "5.180.82.45" })
    }
}
