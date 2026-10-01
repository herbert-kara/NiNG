package com.v2ray.ang.dto.entities

/**
 * What the last batch test learned about one server.
 *
 * The stability fields default to the values a single-sample run would have produced, so a record
 * written by an older build still reads as a plain delay with nothing to claim about it.
 */
data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    /** Spread between the fastest and slowest sample. Absent until a multi-sample run fills it. */
    var testJitterMillis: Long = -1L,
    /** Share of samples that never answered, 0-100. -1 means the run did not measure it. */
    var testLossPercent: Int = -1,
    /** Ranking number from the last run; -1 means the run did not measure it. */
    var testScore: Int = -1,
)
