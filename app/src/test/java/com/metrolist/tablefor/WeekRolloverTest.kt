package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** B13: the Schedule week follows the date, at local midnight and when the app comes back. */
class WeekRolloverTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val honolulu = ZoneId.of("Pacific/Honolulu")
    private val repo = FakeTableForRepository()

    /** Thursday 2026-10-29, 8:00 AM in Honolulu, moving with virtual time. */
    private val morning = TestClock(Instant.parse("2026-10-29T18:00:00Z"), honolulu) { mainDispatcher.currentTime }

    /** Thursday 2026-10-29, 11:59 PM in Honolulu, moving with virtual time. */
    private val beforeMidnight = TestClock(Instant.parse("2026-10-30T09:59:00Z"), honolulu) { mainDispatcher.currentTime }

    private fun TableForViewModel.firstDay() = availabilityGrid.first().date
    private fun TableForViewModel.cell(dayIndex: Int, timeSlot: String) =
        availabilityGrid.single { it.dayIndex == dayIndex && it.timeSlot == timeSlot }

    @Test
    fun `returning to the foreground on a new day rebuilds the week and reapplies saved slots`() {
        val vm = TableForViewModel(repo, morning)
        repo.availabilityStream.emit(mapOf("2026-10-30_19:00" to true, "2026-11-05_18:00" to true))
        vm.onAppBackground()

        morning.jump(Duration.ofDays(1))
        vm.onAppForeground()

        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())
        assertEquals(LocalDate.of(2026, 11, 5), vm.availabilityGrid.last().date)
        assertTrue("tomorrow's slot is now today's", vm.cell(0, "7:00 PM").isAvailable)
        assertTrue("the new seventh day gets its saved slot", vm.cell(6, "6:00 PM").isAvailable)
        assertEquals(2, vm.availabilityGrid.count { it.isAvailable })
        vm.onAppBackground()
    }

    @Test
    fun `the week rolls over at local midnight while the app is open`() {
        val vm = TableForViewModel(repo, beforeMidnight)
        vm.onAppForeground()

        mainDispatcher.advanceBy(59_999)
        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())
        mainDispatcher.advanceBy(1)

        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `it keeps rolling over on later nights`() {
        val vm = TableForViewModel(repo, beforeMidnight)
        vm.onAppForeground()

        mainDispatcher.advanceBy(Duration.ofMinutes(1).plusDays(1).toMillis())

        assertEquals(LocalDate.of(2026, 10, 31), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `in the background there is no timer, and coming back catches up`() {
        val vm = TableForViewModel(repo, beforeMidnight)
        vm.onAppForeground()
        vm.onAppBackground()

        mainDispatcher.advanceBy(Duration.ofMinutes(2).toMillis())
        assertEquals(LocalDate.of(2026, 10, 29), vm.firstDay())

        vm.onAppForeground()
        assertEquals(LocalDate.of(2026, 10, 30), vm.firstDay())
        vm.onAppBackground()
    }

    @Test
    fun `in sample mode your changes stay on their dates`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), morning)
        vm.toggleAvailability(1, "7:00 PM") // Fri 2026-10-30 now free

        morning.jump(Duration.ofDays(1))
        vm.onAppForeground()

        assertTrue(vm.cell(0, "7:00 PM").isAvailable)
        assertFalse(vm.cell(1, "7:00 PM").isAvailable)
        vm.onAppBackground()
    }

    @Test
    fun `the schedule keeps the selected day while it's still in the week`() {
        val week = (30..31).map { LocalDate.of(2026, 10, it) } + (1..5).map { LocalDate.of(2026, 11, it) }

        assertEquals(LocalDate.of(2026, 11, 2), scheduleDayToShow(LocalDate.of(2026, 11, 2), week))
    }

    @Test
    fun `the schedule shows today when the selected day has dropped off the week`() {
        val week = (30..31).map { LocalDate.of(2026, 10, it) } + (1..5).map { LocalDate.of(2026, 11, it) }

        assertEquals(LocalDate.of(2026, 10, 30), scheduleDayToShow(LocalDate.of(2026, 10, 29), week))
        assertEquals(LocalDate.of(2026, 10, 30), scheduleDayToShow(null, week))
    }
}
