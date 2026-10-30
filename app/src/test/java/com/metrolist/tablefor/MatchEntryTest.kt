package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B15: your Match entry always comes from your current profile, never from the sample defaults. */
class MatchEntryTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    private fun TableForViewModel.you() = dinnerGroup!!.members.single { it.role == "You" }

    @Test
    fun `while a signed-in user's profile loads, your entry is You with no score or badge`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals("You", vm.you().name)
        assertNull(vm.you().rating)
        assertTrue("no badge while loading", vm.you().isLoading)
    }

    @Test
    fun `once the profile loads, your entry shows your name and score`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent", trustRating = 4.5f, totalRatings = 3))

        assertEquals("Kent", vm.you().name)
        assertEquals(4.5f, vm.you().rating)
        assertFalse(vm.you().isLoading)
    }

    @Test
    fun `once the profile loads with no ratings, your entry shows the New badge`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))

        assertNull(vm.you().rating)
        assertFalse("loaded, so the New badge shows", vm.you().isLoading)
    }

    @Test
    fun `if the profile can't load, your entry stays You and the sync strip shows`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.fail()

        assertEquals("You", vm.you().name)
        assertNull(vm.you().rating)
        assertTrue(vm.you().isLoading)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `sample mode shows the sample you, matching the sample profile`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals(GroupMember(LOCAL_USER_ID, "Steve Brown", "You", 4.8f, "None"), vm.you())
        assertEquals("Steve Brown", vm.profile.name)
    }

    @Test
    fun `a failed sign-in shows the sample you`() {
        repo.signInResult = null

        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals("Steve Brown", vm.you().name)
        assertEquals(4.8f, vm.you().rating)
        assertFalse(vm.you().isLoading)
    }
}
