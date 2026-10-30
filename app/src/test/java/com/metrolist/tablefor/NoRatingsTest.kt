package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** B11: a member nobody has rated has no score (the Match tab shows "New"), not 0.0. */
class NoRatingsTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    private fun TableForViewModel.you() = dinnerGroup!!.members.single { it.role == "You" }

    @Test
    fun `a signed-in user with no ratings has no score on their group entry`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))

        assertNull(vm.you().rating)
    }

    @Test
    fun `a signed-in user's score shows once they have ratings`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent", trustRating = 4.5f, totalRatings = 3))

        assertEquals(4.5f, vm.you().rating)
    }

    @Test
    fun `the mock members keep their scores`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(null)

        val others = vm.dinnerGroup!!.members.filter { it.role != "You" }

        assertEquals(listOf(4.9f, 4.7f, 5.0f), others.map { it.rating })
    }

    @Test
    fun `sample mode shows the sample score on your entry`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        vm.saveProfile(vm.profile.copy(name = "Sam"))

        assertEquals(4.8f, vm.you().rating)
    }
}
