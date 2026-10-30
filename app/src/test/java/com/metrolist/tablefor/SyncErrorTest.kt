package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B3: a failing Firestore listener is contained; the app keeps running on the data it has. */
class SyncErrorTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()
    private val denied = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

    @Test
    fun `a denied chat listener does not stop profile and availability sync`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.chatStream.fail(denied)
        repo.profileStream.emit(UserProfile(id = "uid_test", name = "Kent"))
        repo.availabilityStream.emit(mapOf("2026-10-30_19:00" to true))

        assertEquals("Kent", vm.profile.name)
        assertTrue(vm.availabilityGrid.single { it.slotKey == "2026-10-30_19:00" }.isAvailable)
    }

    @Test
    fun `a failed listener keeps the data already shown`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        val cloud = listOf(ChatMessage("m1", "Sam", "Hi", "6:00 PM", isMe = false))
        repo.chatStream.emit(cloud)

        repo.chatStream.fail(denied)

        assertEquals(cloud, vm.chatMessages)
    }

    @Test
    fun `status is synced when signed in and every listener works`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(null)
        repo.chatStream.emit(emptyList())

        assertEquals(SyncStatus.Synced, vm.syncStatus)
    }

    @Test
    fun `status shows a sync error after any listener fails`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.availabilityStream.fail(denied)

        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `status is sample data when Firebase is not available`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals(SyncStatus.SampleData, vm.syncStatus)
    }

    @Test
    fun `status is sample data when sign-in fails or throws`() {
        repo.signInResult = null
        assertEquals(SyncStatus.SampleData, TableForViewModel(repo, TEST_CLOCK).syncStatus)

        val throwing = FakeTableForRepository().apply { signInError = RuntimeException("network error") }
        assertEquals(SyncStatus.SampleData, TableForViewModel(throwing, TEST_CLOCK).syncStatus)
    }
}
