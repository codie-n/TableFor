package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** B2: availability is stored by real date, so it doesn't shift a day each day. */
class AvailabilityTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    private fun TableForViewModel.cell(dayIndex: Int, timeSlot: String) =
        availabilityGrid.single { it.dayIndex == dayIndex && it.timeSlot == timeSlot }

    @Test
    fun `saved availability is read by date, ignoring old keys and past dates`() {
        val vm = TableForViewModel(repo, TEST_CLOCK) // today is 2026-10-29

        repo.availabilityStream.emit(
            mapOf(
                "2026-10-30_19:00" to true, // tomorrow, 7 PM
                "2026-11-04_22:00" to true, // six days out, 10 PM
                "0_7:00 PM" to true,        // old format: ignored
                "2026-10-28_19:00" to true, // yesterday: ignored
            )
        )

        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
        assertTrue(vm.cell(6, "10:00 PM").isAvailable)
        assertFalse(vm.cell(0, "7:00 PM").isAvailable)
        assertEquals(2, vm.availabilityGrid.count { it.isAvailable })
    }

    @Test
    fun `a slot saved yesterday for tomorrow shows today, not a day later`() {
        val saved = mapOf("2026-10-30_19:00" to true)
        val nextDay = Clock.offset(TEST_CLOCK, Duration.ofDays(1)) // today is 2026-10-30

        val vm = TableForViewModel(repo, nextDay)
        repo.availabilityStream.emit(saved)

        assertTrue(vm.cell(0, "7:00 PM").isAvailable)
        assertFalse(vm.cell(1, "7:00 PM").isAvailable)
    }

    @Test
    fun `grid cells carry their real dates, starting today`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(LocalDate.of(2026, 10, 29), vm.cell(0, "5:00 PM").date)
        assertEquals(LocalDate.of(2026, 11, 4), vm.cell(6, "10:00 PM").date)
        assertEquals(LocalTime.of(22, 0), vm.cell(6, "10:00 PM").time)
        assertEquals("2026-10-30_19:30", vm.cell(1, "7:30 PM").slotKey)
    }

    @Test
    fun `toggling a slot saves it under its ISO date and 24-hour time`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(listOf(AvailabilityWrite("2026-10-30_19:00", true, emptySet())), repo.availabilityWrites)
        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
    }

    @Test
    fun `keys parse only in the new format`() {
        assertEquals(LocalDate.of(2026, 10, 30) to LocalTime.of(19, 0), AvailabilityKeys.parse("2026-10-30_19:00"))
        assertNull(AvailabilityKeys.parse("0_7:00 PM"))
        assertNull(AvailabilityKeys.parse("2026-10-30_7:00 PM"))
        assertNull(AvailabilityKeys.parse("2026-13-30_19:00"))
    }

    @Test
    fun `saving drops old-format keys and past dates`() {
        val saved = mapOf(
            "0_7:00 PM" to true,
            "2026-10-28_19:00" to true,
            "2026-10-29_17:00" to false,
            "2026-11-01_20:30" to true,
        )

        val kept = AvailabilityKeys.prune(saved, today = LocalDate.of(2026, 10, 29))

        assertEquals(mapOf("2026-10-29_17:00" to false, "2026-11-01_20:30" to true), kept)
    }
}
