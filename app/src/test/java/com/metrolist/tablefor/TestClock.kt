package com.metrolist.tablefor

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * A clock tests can move: it follows virtual time on the test scheduler
 * ([virtualMillis]) and jumps forward with [jump].
 */
class TestClock(
    private val start: Instant,
    private val zone: ZoneId,
    private val virtualMillis: () -> Long = { 0L },
) : Clock() {
    private var jumped: Duration = Duration.ZERO

    fun jump(by: Duration) {
        jumped += by
    }

    override fun instant(): Instant = start.plus(jumped).plusMillis(virtualMillis())
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = TestClock(start.plus(jumped), zone, virtualMillis)
}
