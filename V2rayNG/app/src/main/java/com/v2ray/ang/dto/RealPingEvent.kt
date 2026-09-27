package com.v2ray.ang.dto

sealed class RealPingEvent {

    /** Periodic progress update while the batch is still running. */
    data class Progress(val text: String) : RealPingEvent()

    /**
     * A single server result is available.
     *
     * countryCode is where the traffic for this profile actually emerged, read from the same place
     * the connection panel reads it. A row filled from DNS can name a CDN edge or a relay instead
     * of the server behind it, so the flag would be a real flag for the wrong machine.
     */
    data class Result(
        val guid: String,
        val delayMillis: Long,
        val countryCode: String? = null,
    ) : RealPingEvent()

    /** The entire batch has finished or been cancelled. */
    data class Finish(val status: String) : RealPingEvent()
}

