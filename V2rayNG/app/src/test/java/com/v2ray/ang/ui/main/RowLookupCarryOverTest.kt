package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The regression that shipped four releases: a resolved verdict was discarded by the next list
 * rebuild, so the row fell back to "unchecked" moments after the lookup had answered.
 */
class RowLookupCarryOverTest {

    private fun row(guid: String, server: String, country: String?, flag: FlagStatus) =
        ServerRowUiModel(
            guid = guid,
            profile = com.v2ray.ang.dto.entities.ProfileItem(server = server),
            remarks = "",
            statistics = "",
            typeDescription = "",
            testDelayMillis = 0L,
            subscriptionBadge = "",
            serverCountryCode = country,
            flagStatus = flag,
        )

    @Test
    fun aResolvedVerdictSurvivesAListRebuild() {
        val resolved = listOf(row("g1", "188.114.97.6", "CA", FlagStatus.CLEAN))
        // The rebuild starts from the profile, so it knows nothing about the lookups yet.
        val rebuilt = listOf(row("g1", "188.114.97.6", null, FlagStatus.UNKNOWN))

        val result = RowLookupCarryOver.carry(rebuilt, resolved)

        assertEquals(FlagStatus.CLEAN, result[0].flagStatus)
        assertEquals("CA", result[0].serverCountryCode)
    }

    @Test
    fun aFlaggedVerdictAlsoSurvivesARebuild() {
        val resolved = listOf(row("g1", "158.173.20.208", "NL", FlagStatus.FLAGGED))
        val rebuilt = listOf(row("g1", "158.173.20.208", null, FlagStatus.UNKNOWN))
        assertEquals(FlagStatus.FLAGGED, RowLookupCarryOver.carry(rebuilt, resolved)[0].flagStatus)
    }

    @Test
    fun anEditedAddressDoesNotInheritTheOldServersVerdict() {
        val resolved = listOf(row("g1", "188.114.97.6", "CA", FlagStatus.CLEAN))
        // Same GUID, different address: the row now describes a different server.
        val rebuilt = listOf(row("g1", "1.1.1.1", null, FlagStatus.UNKNOWN))

        val result = RowLookupCarryOver.carry(rebuilt, resolved)

        assertEquals(FlagStatus.UNKNOWN, result[0].flagStatus)
        assertEquals(null, result[0].serverCountryCode)
    }

    @Test
    fun aRemovedServerDoesNotResurrectAndANewOneDoesNotInherit() {
        val resolved = listOf(row("g1", "188.114.97.6", "CA", FlagStatus.CLEAN))
        val rebuilt = listOf(row("g2", "1.1.1.1", null, FlagStatus.UNKNOWN))

        val result = RowLookupCarryOver.carry(rebuilt, resolved)

        assertEquals(1, result.size)
        assertEquals("g2", result[0].guid)
        assertEquals(FlagStatus.UNKNOWN, result[0].flagStatus)
    }

    @Test
    fun repeatedRebuildsDoNotLoseTheAnswer() {
        var rows = listOf(row("g1", "188.114.97.6", null, FlagStatus.UNKNOWN))
        rows = RowLookupCarryOver.carry(rows, rows)
        rows = applyServerFlag(rows, "g1", "188.114.97.6", FlagStatus.CLEAN, "CA")
        // Three list updates in a row, as a ping test produces.
        repeat(3) { rows = RowLookupCarryOver.carry(
            listOf(row("g1", "188.114.97.6", null, FlagStatus.UNKNOWN)), rows) }

        assertEquals(FlagStatus.CLEAN, rows[0].flagStatus)
        assertEquals("CA", rows[0].serverCountryCode)
    }
}
