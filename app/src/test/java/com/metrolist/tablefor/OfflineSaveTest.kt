package com.metrolist.tablefor

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * B9: offline, Firestore queues a write on the phone but confirms it only when
 * the server answers. Save and Submit wait 3 seconds of (virtual) time for the
 * server, then report the write as saved on this phone.
 */
class OfflineSaveTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()
    private val rejected = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

    private fun signedInWithProfile(): TableForViewModel {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))
        return vm
    }

    @Test
    fun `a profile save the server hasn't confirmed after 3 seconds shows as saved on this phone`() {
        val vm = signedInWithProfile()
        repo.saveProfileGate = CompletableDeferred() // offline: never confirmed

        vm.saveProfile(vm.profile.copy(bio = "Hungry"))
        mainDispatcher.advanceBy(2_999)
        assertEquals(SaveState.Saving, vm.profileSaveState)
        mainDispatcher.advanceBy(1)

        assertEquals(SaveState.SavedOnPhone, vm.profileSaveState)
        assertEquals("virtual time only", 3_000L, mainDispatcher.currentTime)
    }

    @Test
    fun `a queued profile save the server confirms later shows the normal saved message`() {
        val vm = signedInWithProfile()
        val gate = CompletableDeferred<Unit>()
        repo.saveProfileGate = gate
        vm.saveProfile(vm.profile.copy(bio = "Hungry"))
        mainDispatcher.advanceBy(3_000)

        gate.complete(Unit)

        assertEquals(SaveState.Saved, vm.profileSaveState)
        assertEquals(SyncStatus.Synced, vm.syncStatus)
    }

    @Test
    fun `a queued profile save the server rejects later shows the error and the sync strip`() {
        val vm = signedInWithProfile()
        val gate = CompletableDeferred<Unit>()
        repo.saveProfileGate = gate
        vm.saveProfile(vm.profile.copy(bio = "Hungry"))
        mainDispatcher.advanceBy(3_000)
        assertEquals(SaveState.SavedOnPhone, vm.profileSaveState)

        repo.saveProfileError = rejected
        gate.complete(Unit)

        assertEquals(SaveState.Failed, vm.profileSaveState)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `a rating submit the server hasn't confirmed after 3 seconds shows as saved on this phone`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.submitGate = CompletableDeferred()

        vm.submitRatings()
        mainDispatcher.advanceBy(3_000)

        assertEquals(SaveState.SavedOnPhone, vm.ratingsSubmitState)
        // The button is enabled again (it's disabled only while Saving), so a second submit goes through.
        vm.submitRatings()
        assertEquals(2, repo.calls.count { it == "submitRatings" })
    }

    @Test
    fun `a queued rating submit the server rejects later shows the error and the sync strip`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        val gate = CompletableDeferred<Unit>()
        repo.submitGate = gate
        vm.submitRatings()
        mainDispatcher.advanceBy(3_000)
        assertEquals(SaveState.SavedOnPhone, vm.ratingsSubmitState)

        repo.submitError = rejected
        gate.complete(Unit)

        assertEquals(SaveState.Failed, vm.ratingsSubmitState)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `a schedule toggle deletes the stale keys in the last saved map`() {
        val vm = TableForViewModel(repo, TEST_CLOCK) // today is 2026-10-29
        repo.availabilityStream.emit(
            mapOf(
                "0_7:00 PM" to true,         // old format
                "2026-10-28_19:00" to true,  // yesterday
                "2026-10-31_18:00" to true,  // still current
            )
        )

        vm.toggleAvailability(1, "7:00 PM")

        val write = repo.availabilityWrites.single()
        assertEquals("2026-10-30_19:00", write.slotKey)
        assertEquals(setOf("0_7:00 PM", "2026-10-28_19:00"), write.staleKeys)
    }

    @Test
    fun `stale keys are old-format keys and dates before today`() {
        val saved = mapOf("0_7:00 PM" to true, "2026-10-28_19:00" to false, "2026-10-29_17:00" to true)

        assertEquals(setOf("0_7:00 PM", "2026-10-28_19:00"), AvailabilityKeys.staleKeys(saved, LocalDate.of(2026, 10, 29)))
    }

    @Test
    fun `the availability write merges one slot and deletes stale keys`() {
        val fields = availabilityUpdate("2026-10-30_19:00", true, setOf("0_7:00 PM"))

        assertEquals(mapOf("slots" to mapOf("0_7:00 PM" to FieldValue.delete(), "2026-10-30_19:00" to true)), fields)
    }

    @Test
    fun `a schedule toggle the server rejects shows the sync strip`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.availabilityStream.emit(emptyMap()) // the schedule has loaded, with nothing saved yet
        repo.availabilityError = rejected

        vm.toggleAvailability(1, "7:00 PM")

        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `a preference tap the server rejects shows the sync strip`() {
        val vm = signedInWithProfile()
        repo.saveProfileError = rejected

        vm.updateProfile(vm.profile.copy(budget = "$ (Budget)"))

        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }
}
