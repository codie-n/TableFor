package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * B26: the schedule loads like the profile and the chat. Until a signed-in user's
 * saved availability has arrived, a Schedule tap does nothing: no write and no
 * change on screen. The same if it couldn't load. Sample mode is loaded at once.
 */
class ScheduleLoadingTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()
    private val denied = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

    private fun TableForViewModel.cell(dayIndex: Int, timeSlot: String) =
        availabilityGrid.single { it.dayIndex == dayIndex && it.timeSlot == timeSlot }

    private fun TableForViewModel.freeSlots() = availabilityGrid.filter { it.isAvailable }.map { it.slotKey }.toSet()

    @Test
    fun `a tap while signing in writes nothing and changes nothing, and after sign-in the grid shows only the saved slots`() {
        val gate = CompletableDeferred<Unit>()
        repo.signInGate = gate
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(ScheduleLoad.Loading, vm.scheduleLoad)
        val before = vm.availabilityGrid

        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
        assertEquals(before, vm.availabilityGrid)
        assertEquals(SyncStatus.Connecting, vm.syncStatus)

        gate.complete(Unit)
        assertEquals("signed in, waiting for the saved slots", ScheduleLoad.Loading, vm.scheduleLoad)
        repo.availabilityStream.emit(mapOf("2026-10-31_18:00" to true))

        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)
        assertEquals(setOf("2026-10-31_18:00"), vm.freeSlots())
        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
    }

    @Test
    fun `a tap after sign-in but before the first snapshot writes nothing and changes nothing`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(SyncStatus.Synced, vm.syncStatus)
        assertEquals(ScheduleLoad.Loading, vm.scheduleLoad)
        val before = vm.availabilityGrid

        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
        assertEquals(before, vm.availabilityGrid)
        assertEquals(SyncStatus.Synced, vm.syncStatus)
    }

    @Test
    fun `the first snapshot loads the schedule, even with nothing saved, and taps then write`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.availabilityStream.emit(emptyMap())
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)
        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(listOf(AvailabilityWrite("2026-10-30_19:00", true, emptySet())), repo.availabilityWrites)
        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
    }

    @Test
    fun `an availability listener failing before its first snapshot means the schedule couldn't load, and taps do nothing`() {
        val honoluluMorning =
            TestClock(Instant.parse("2026-10-29T18:00:00Z"), ZoneId.of("Pacific/Honolulu")) { mainDispatcher.currentTime }
        val vm = TableForViewModel(repo, honoluluMorning)

        repo.availabilityStream.fail(denied)
        assertEquals(ScheduleLoad.Failed, vm.scheduleLoad)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
        val before = vm.availabilityGrid
        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
        assertEquals(before, vm.availabilityGrid)

        // Still the case on a new day.
        honoluluMorning.jump(Duration.ofDays(1))
        vm.onAppForeground()
        vm.toggleAvailability(1, "7:00 PM")
        assertEquals(ScheduleLoad.Failed, vm.scheduleLoad)
        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
        assertEquals(emptySet<String>(), vm.freeSlots())
        vm.onAppBackground()
    }

    @Test
    fun `an availability stream that ends before its first snapshot means the schedule couldn't load, and taps do nothing`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.availabilityStream.end()
        assertEquals(ScheduleLoad.Failed, vm.scheduleLoad)
        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(emptyList<AvailabilityWrite>(), repo.availabilityWrites)
        assertEquals(emptySet<String>(), vm.freeSlots())
    }

    @Test
    fun `only the availability listener decides whether the schedule loaded`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))
        repo.saveProfileError = denied
        vm.updateProfile(vm.profile.copy(budget = "$ (Budget)")) // a rejected write
        repo.chatStream.fail(denied)
        repo.profileStream.fail(denied)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
        assertEquals(ScheduleLoad.Loading, vm.scheduleLoad)

        repo.availabilityStream.emit(mapOf("2026-10-31_18:00" to true))
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)
        assertEquals(setOf("2026-10-31_18:00"), vm.freeSlots())
        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(listOf(AvailabilityWrite("2026-10-30_19:00", true, emptySet())), repo.availabilityWrites)
    }

    @Test
    fun `an availability listener failing after its first snapshot keeps the schedule loaded, and a tap still writes`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.availabilityStream.emit(mapOf("2026-10-31_18:00" to true))

        repo.availabilityStream.fail(denied)
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
        assertEquals("the grid keeps what it shows", setOf("2026-10-31_18:00"), vm.freeSlots())
        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(listOf(AvailabilityWrite("2026-10-30_19:00", true, emptySet())), repo.availabilityWrites)
        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
    }

    @Test
    fun `sample mode is loaded at once, and a tap changes the grid without writing`() {
        val offline = FakeTableForRepository(isFirebaseAvailable = false)
        val vm = TableForViewModel(offline, TEST_CLOCK)
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)

        vm.toggleAvailability(1, "7:00 PM")

        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
        assertEquals(emptyList<String>(), offline.calls)
    }

    @Test
    fun `after a failed sign-in the schedule is loaded at once, and a tap changes the grid without writing`() {
        repo.signInResult = null
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(SyncStatus.SampleData, vm.syncStatus)
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)

        vm.toggleAvailability(1, "7:00 PM")

        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
        assertEquals(listOf("signIn"), repo.calls)
    }

    @Test
    fun `after a sign-in that throws the schedule is loaded at once, and a tap changes the grid without writing`() {
        repo.signInError = RuntimeException("network error")
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertEquals(SyncStatus.SampleData, vm.syncStatus)
        assertEquals(ScheduleLoad.Loaded, vm.scheduleLoad)

        vm.toggleAvailability(1, "7:00 PM")

        assertTrue(vm.cell(1, "7:00 PM").isAvailable)
        assertEquals(listOf("signIn"), repo.calls)
    }
}
