package com.v2ray.ang.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BalancerStrategyMappingTest {

    /**
     * The connect button's balancer menu stores the R.array.policy_group_type index
     * (0 = Least Ping, 3 = Round Robin) into ProfileItem.policyGroupType, exactly like
     * ServerGroupActivity does, and CoreConfigManager resolves it back with from().
     */
    @Test
    fun `policy group type index resolves to the strategy the menu labelled`() {
        assertEquals(BalancerStrategyType.ROUND_ROBIN, BalancerStrategyType.from("3"))
        assertEquals(BalancerStrategyType.LEAST_PING, BalancerStrategyType.from("0"))
    }
}
