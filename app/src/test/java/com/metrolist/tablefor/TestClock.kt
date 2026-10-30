package com.metrolist.tablefor

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A clock tests can move: it follows virtual time on the test scheduler
 * ([virtualMillis]), jumps forward with [jump], and changes time zone with
 * [moveTo], like a phone whose zone setting changes.
 */
class TestClock(
    private val start: Instant,
    zone: ZoneId,
    private val virtualMillis: () -> Long = { 0L },
) : Clock() {
    private var jumped: Duration = Duration.ZERO
    private var currentZone: ZoneId = zone

    fun jump(by: Duration) {
        jumped += by
    }

    fun moveTo(zone: ZoneId) {
        currentZone = zone
    }

    override fun instant(): Instant = start.plus(jumped).plusMillis(virtualMillis())
    override fun getZone(): ZoneId = currentZone
    override fun withZone(zone: ZoneId): Clock = TestClock(start.plus(jumped), zone, virtualMillis)
}
