package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * B25: the sample free slots (days 3 and 4 at 7 and 8 PM) show only in sample mode.
 * With Firebase configured the week starts all busy, and a signed-in user sees only
 * what's saved, even before it arrives or if it never does.
 */
class SampleSlotsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()
    private val denied = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

    /** Thursday 2026-10-29, 11:59 PM in Honolulu, moving with virtual time. */
    private val beforeMidnight =
        TestClock(Instant.parse("2026-10-30T09:59:00Z"), ZoneId.of("Pacific/Honolulu")) { mainDispatcher.currentTime }

    private fun TableForViewModel.freeSlots() = availabilityGrid.filter { it.isAvailable }.map { it.slotKey }.toSet()

    /** Rolls [beforeMidnight] over to Friday 2026-10-30 with the midnight timer. */
    private fun TableForViewModel.passMidnight() {
        onAppForeground()
        mainDispatcher.advanceBy(Duration.ofMinutes(1).toMillis())
        assertEquals(LocalDate.of(2026, 10, 30), availabilityGrid.first().date)
        onAppBackground()
    }

    @Test
    fun `without Firebase the sample free slots show from the start`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals(SAMPLE_FREE_SLOTS, vm.freeSlots())
    }

    @Test
    fun `signed in, no slot is free before the first availability snapshot`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(SyncStatus.Synced, vm.syncStatus)
        assertEquals(emptySet<String>(), vm.freeSlots())
    }

    @Test
    fun `signed in, a date change before the first snapshot still shows no free slot`() {
        val vm = TableForViewModel(repo, beforeMidnight)

        vm.passMidnight()

        assertEquals(emptySet<String>(), vm.freeSlots())
    }

    @Test
    fun `signed in, an availability listener failing before its first snapshot leaves no slot free, also after a date change`() {
        val vm = TableForViewModel(repo, beforeMidnight)

        repo.availabilityStream.fail(denied)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
        assertEquals(emptySet<String>(), vm.freeSlots())

        vm.passMidnight()

        assertEquals(emptySet<String>(), vm.freeSlots())
    }

    @Test
    fun `while signing in no slot is free, and a failed sign-in shows the sample free slots`() {
        val gate = CompletableDeferred<Unit>()
        repo.signInGate = gate
        repo.signInResult = null
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(SyncStatus.Connecting, vm.syncStatus)
        assertEquals(emptySet<String>(), vm.freeSlots())

        gate.complete(Unit)

        assertEquals(SyncStatus.SampleData, vm.syncStatus)
        assertEquals(SAMPLE_FREE_SLOTS, vm.freeSlots())
    }

    @Test
    fun `while signing in no slot is free, and a sign-in that throws shows the sample free slots`() {
        val gate = CompletableDeferred<Unit>()
        repo.signInGate = gate
        repo.signInError = RuntimeException("network error")
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(emptySet<String>(), vm.freeSlots())

        gate.complete(Unit)

        assertEquals(SyncStatus.SampleData, vm.syncStatus)
        assertEquals(SAMPLE_FREE_SLOTS, vm.freeSlots())
    }

    private companion object {
        /** Days 3 and 4 from Thursday 2026-10-29 (Sunday and Monday), at 7 and 8 PM. */
        val SAMPLE_FREE_SLOTS = setOf("2026-11-01_19:00", "2026-11-01_20:00", "2026-11-02_19:00", "2026-11-02_20:00")
    }
}
