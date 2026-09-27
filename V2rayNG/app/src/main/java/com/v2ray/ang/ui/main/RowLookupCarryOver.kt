package com.v2ray.ang.ui.main

import com.v2ray.ang.handler.FlagStatus
import com.v2ray.ang.handler.RiskLevel
import com.v2ray.ang.handler.RiskReason
import com.v2ray.ang.handler.RiskReport
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache

/**
 * Row identity across a list rebuild.
 *
 * Any list update rebuilds the rows from their profile, and a rebuilt row starts with no country
 * and no verdict. These are per address, so they have to be carried over. The verdict was not,
 * which is why a row showed "unchecked" moments after the lookup had resolved it.
 */
internal object RowLookupCarryOver {

    /**
     * [rebuilt] rows come straight from the profile. [previous] rows hold whatever the lookups
     * have resolved so far. A result is kept only while the address is unchanged: a row whose
     * address was edited describes a different server and must not inherit the old answer.
     */
    fun carry(fresh: List<ServerRowUiModel>, previous: List<ServerRowUiModel>): List<ServerRowUiModel> {
        val byGuid = previous.associateBy { it.guid }
        return fresh.map { row ->
            val old = byGuid[row.guid]
            if (old != null && old.profile.server == row.profile.server) {
                row.copy(
                    serverCountryCode = old.serverCountryCode,
                    flagStatus = old.flagStatus,
                )
            } else {
                row
            }
        }
    }
}

private fun row(
    guid: String,
    server: String,
    country: String? = null,
    flag: FlagStatus = FlagStatus.UNKNOWN,
) = ServerRowUiModel(
    guid = guid,
    profile = ProfileItem(server = server),
    remarks = "",
    statistics = "",
    typeDescription = "",
    testDelayMillis = 0L,
    subscriptionBadge = "",
    serverCountryCode = country,
    flagStatus = flag,
)
