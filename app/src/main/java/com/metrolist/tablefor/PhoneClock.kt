package com.metrolist.tablefor

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The system clock in the phone's current time zone. Clock.systemDefaultZone()
 * keeps the zone it was created with; this one looks the zone up on every call,
 * so "today" follows a time-zone change at once. Tests pass their own Clock.
 */
object PhoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
}
