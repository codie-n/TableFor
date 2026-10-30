package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

/** B17: "today" follows the phone's current time zone and clock, not the ones from app start. */
class TimeZoneTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val honolulu = ZoneId.of("Pacific/Honolulu")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val repo = FakeTableForRepository()

    /**
     * Friday 2026-10-30, 3:00 PM in Tokyo, which is Thursday 8:00 PM in Honolulu.
     * Midnight is 9 hours away in Tokyo and 4 hours away in Honolulu.
     */
    private val tokyoAfternoon = TestClock(Instant.parse("2026-10-30T06:00:00Z"), tokyo) { mainDispatcher.currentTime }

    /** Thursday 2026-10-29, 8:00 AM in Honolulu, moving with virtual time. */
    private val honoluluMorning = TestClock(Instant.parse("2026-10-29T18:00:00Z"), honolulu) { mainDispatcher.currentTime }

    private fun TableForViewModel.firstDay() = availabilityGrid.first().date

    @Test
    fun `a time-zone change while the app is open rebuilds the week`() {
        val vm = TableForViewModel(repo, tokyoAfternoon)
        vm.onAppForeground()
        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())

        tokyoAfternoon.moveTo(honolulu)
        vm.onTimeChanged()

        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `after a time-zone change the week rolls over at midnight in the new zone`() {
        val vm = TableForViewModel(repo, tokyoAfternoon)
        vm.onAppForeground()
        tokyoAfternoon.moveTo(honolulu)
        vm.onTimeChanged()
        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())

        mainDispatcher.advanceBy(Duration.ofHours(4).toMillis() - 1)
        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())
        mainDispatcher.advanceBy(1)

        assertEquals("Honolulu midnight, not Tokyo's", LocalDate.of(2026, 10, 30), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `setting the phone's clock forward moves the midnight timer`() {
        val vm = TableForViewModel(repo, honoluluMorning)
        vm.onAppForeground()

        honoluluMorning.jump(Duration.ofHours(15)) // now 11:00 PM
        vm.onTimeChanged()
        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())
        mainDispatcher.advanceBy(Duration.ofHours(1).toMillis())

        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `setting the phone's clock to another day rebuilds the week`() {
        val vm = TableForViewModel(repo, honoluluMorning)
        vm.onAppForeground()

        honoluluMorning.jump(Duration.ofDays(1))
        vm.onTimeChanged()

        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `coming back to the foreground in another time zone rebuilds the week`() {
        val vm = TableForViewModel(repo, tokyoAfternoon)
        vm.onAppForeground()
        vm.onAppBackground()

        tokyoAfternoon.moveTo(honolulu)
        vm.onAppForeground()

        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `with the app's own clock, today follows the phone's time-zone setting`() {
        // Pago Pago is UTC-11 and Kiritimati UTC+14, so their dates always differ.
        val pagoPago = ZoneId.of("Pacific/Pago_Pago")
        val kiritimati = ZoneId.of("Pacific/Kiritimati")
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(pagoPago))
            val vm = TableForViewModel(repo) // the default clock, as in the app
            assertEquals(LocalDate.now(pagoPago), vm.firstDay())

            TimeZone.setDefault(TimeZone.getTimeZone(kiritimati))
            vm.onAppForeground()

            assertNotEquals(LocalDate.now(pagoPago), LocalDate.now(kiritimati))
            assertEquals(LocalDate.now(kiritimati), vm.firstDay())
            vm.onAppBackground()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
