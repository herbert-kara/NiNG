package com.v2ray.ang.dto.entities

data class ServersCache(
    val guid: String,
    val profile: ProfileItem,
    val testDelayMillis: Long = 0L,
    // The rest of the batch-test verdict, carried beside the delay so the row and the sort order
    // are built from one record. -1 means the run that wrote this did not measure them, which is
    // what every row looks like until the first multi-sample test replaces it.
    val testJitterMillis: Long = -1L,
    val testLossPercent: Int = -1,
    val testScore: Int = -1,
)
