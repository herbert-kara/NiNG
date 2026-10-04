package com.v2ray.ang.ui.main

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The page showed a verdict on one row and nothing on the rest, and the country flag never
 * appeared at all.
 *
 * The cause was not the lookup, the parser, the row model or the bundled flags. Both walks were
 * `collectLatest` blocks over a flow derived from the row list, and publishing a result edits that
 * row list. The first row answered, its own answer produced a new emission, and collectLatest tore
 * the pass down with every remaining row unasked. Reopening the screen repeated it for whichever
 * row happened to be first, which is why it read as intermittent rather than absent.
 *
 * These assert the property that catches it: a walk that publishes into the state it reads from
 * still finishes every target, and two walks over the same rows do not cancel each other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlagWalkSurvivesItsOwnPublishTest {

    private data class Row(val guid: String, val address: String, val country: String? = null)

    /** A stand-in for the ViewModel's private carrier, so this file does not reach into it. */
    private data class Country(val code: String)

    @Test
    fun aWalkThatPublishesIntoTheListItReadsStillFinishesEveryRow() = runTest {
        val rows = MutableStateFlow((1..6).map { Row("g$it", "10.0.0.$it") })
        val published = mutableListOf<String>()

        // The shape under test: derive the targets from a state flow, then publish back into it.
        // If the walk re-reads the flow, its own publish cancels it after the first row.
        val targets = rows.map { state -> state.map { it.guid to it.address } }
            .distinctUntilChanged()
            .first()
        runFlagBatch(
            targets = targets.toList(),
            force = false,
            lookup = { _, _ ->
                delay(10)
                Country("DE")
            },
            publish = { guid, _, country ->
                published += guid
                rows.value = rows.value.map {
                    if (it.guid == guid) it.copy(country = country.code) else it
                }
            },
        )

        assertEquals(listOf("g1", "g2", "g3", "g4", "g5", "g6"),published)
        assertEquals(6,rows.value.count { it.country == "DE" })
    }

    @Test
    fun twoWalksOverTheSameRowsDoNotCancelEachOther() = runTest {
        val rows = (1..4).map { Row("g$it", "10.0.1.$it") }
        val verdicts = mutableListOf<String>()
        val countries = mutableListOf<String>()
        val targets: List<Pair<String, String>> = rows.map { it.guid to it.address }

        // The verdict walk and the country walk used to share one job slot per group, so whichever
        // collector emitted last cancelled the other and the page showed a verdict with no country
        // or the reverse.
        coroutineScope {
            awaitAll(
                async {
                    runFlagBatch(
                        targets = targets, force = true,
                        lookup = { _, _ ->
                            delay(5)
                            "FLAGGED"
                        },
                        publish = { guid, _, _ -> verdicts += guid },
                    )
                },
                async {
                    runFlagBatch(
                        targets = targets, force = true,
                        lookup = { _, _ ->
                            delay(5)
                            Country("DE")
                        },
                        publish = { guid, _, _ -> countries += guid },
                    )
                },
            )
        }

        assertEquals(4,verdicts.size)
        assertEquals(4,countries.size)
    }

    @Test
    fun everyRowOfTheReportedPageIsAskedEvenWhenTheFirstAnswersImmediately() = runTest {
        // The reported page: two rows on one host, two on named hosts. A walk that stops after the
        // first is the bug in its smallest form, and the two rows sharing an address are what the
        // per-address dedup has to handle.
        val rows = listOf(
            Row("a", "5.180.82.45"), Row("b", "5.180.82.45"),
            Row("c", "bow.tehtanshop.com"), Row("d", "landing.tehtanshop.com"),
        )
        val asked = mutableListOf<String>()
        runFlagBatch(
            targets = rows.map { it.guid to it.address },
            force = true,
            lookup = { address, _ ->
                asked += address
                null
            },
            publish = { _, _, _ -> },
        )
        assertEquals(4,asked.size)
        assertEquals(2, asked.count { it == "5.180.82.45" })
    }
}
