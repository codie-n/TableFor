package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B5: "you" is the signed-in user, and nothing is read or written without one. */
class IdentityTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository().apply { signInResult = "uid_kent" }

    private fun TableForViewModel.you() = dinnerGroup!!.members.single { it.role == "You" }

    @Test
    fun `your group entry uses the signed-in user's id`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals("uid_kent", vm.you().id)
    }

    @Test
    fun `the rating list is everyone except you`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(listOf("2", "3", "4"), vm.memberRatings.map { it.memberId })
    }

    @Test
    fun `listeners read the signed-in user's documents`() {
        TableForViewModel(repo, TEST_CLOCK)

        assertTrue(repo.calls.toString(), "getUserProfileFlow(uid_kent)" in repo.calls)
        assertTrue(repo.calls.toString(), "getAvailabilityFlow(uid_kent)" in repo.calls)
    }

    @Test
    fun `loading and editing your profile updates your group entry`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_kent").copy(name = "Kent", diets = listOf("Vegan")))
        assertEquals("Kent", vm.you().name)
        assertEquals("Vegan", vm.you().diet)

        vm.updateProfile(vm.profile.copy(diets = listOf("Halal")))
        assertEquals("uid_kent", vm.you().id)
        assertEquals("Halal", vm.you().diet)
    }

    @Test
    fun `sample mode makes no repository calls but still works locally`() {
        val offline = FakeTableForRepository(isFirebaseAvailable = false)
        val vm = TableForViewModel(offline, TEST_CLOCK)

        doEveryWrite(vm)

        assertEquals(emptyList<String>(), offline.calls)
        assertEquals("Sam", vm.profile.name)
        assertEquals(SaveState.Saved, vm.profileSaveState)
        assertEquals(SaveState.Saved, vm.ratingsSubmitState)
        assertEquals("hi", vm.chatMessages.last().text)
    }

    @Test
    fun `in sample mode your entry keeps the local placeholder id`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals(LOCAL_USER_ID, vm.you().id)
        assertEquals(listOf("2", "3", "4"), vm.memberRatings.map { it.memberId })
    }

    @Test
    fun `after a failed sign-in only the sign-in attempt reaches the repository`() {
        repo.signInResult = null
        val vm = TableForViewModel(repo, TEST_CLOCK)

        doEveryWrite(vm)

        assertEquals(listOf("signIn"), repo.calls)
    }

    private fun doEveryWrite(vm: TableForViewModel) {
        vm.toggleAvailability(0, "7:00 PM")
        vm.updateProfile(vm.profile.copy(budget = "$ (Budget)"))
        vm.saveProfile(vm.profile.copy(name = "Sam"))
        vm.sendMessage("hi")
        vm.updateRating("2", 3, 3, 3, "")
        vm.submitRatings()
    }
}
