package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B4: the profile form starts from the saved profile and never overwrites it with placeholders. */
class ProfileTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    @Test
    fun `a signed-in user with no profile document starts with a blank profile`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.profileStream.emit(null)

        assertEquals("", vm.profile.name)
        assertEquals("", vm.profile.bio)
        assertTrue(vm.profile.diets.isEmpty())
        assertEquals(0, vm.profile.totalRatings)
    }

    @Test
    fun `a profile save never writes trustRating or totalRatings`() {
        val fields = profileToFirestore("uid_test", UserProfile(name = "Kent", trustRating = 4.2f, totalRatings = 7))

        assertEquals("Kent", fields["name"])
        assertFalse(fields.containsKey("trustRating"))
        assertFalse(fields.containsKey("totalRatings"))
    }

    @Test
    fun `a stored profile with missing fields reads as blank, not as sample values`() {
        val profile = profileFromFirestore("uid_test", mapOf("name" to "Kent"))

        assertEquals("Kent", profile.name)
        assertEquals("", profile.budget)
        assertEquals(0f, profile.trustRating)
        assertEquals(0, profile.totalRatings)
    }

    @Test
    fun `sample mode keeps the Alex Chen sample profile`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals("Alex Chen", vm.profile.name)
        assertTrue(vm.profileLoaded)
    }

    @Test
    fun `failed sign-in loads the sample profile`() {
        repo.signInResult = null

        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals("Alex Chen", vm.profile.name)
        assertTrue(vm.profileLoaded)
    }

    @Test
    fun `the profile is not loaded until the saved one arrives`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        assertFalse(vm.profileLoaded)
        assertEquals("", vm.profile.name)

        repo.profileStream.emit(kent)

        assertTrue(vm.profileLoaded)
        assertEquals(kent, vm.profile)
    }

    @Test
    fun `saving before the profile has loaded writes nothing`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        vm.saveProfile(UserProfile(name = "Placeholder"))
        vm.updateProfile(UserProfile(name = "Placeholder"))

        assertTrue(repo.savedProfiles.isEmpty())
        assertEquals(SaveState.Idle, vm.profileSaveState)
    }

    @Test
    fun `profile saved shows only after the write succeeds`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(kent)
        val gate = CompletableDeferred<Unit>()
        repo.saveProfileGate = gate

        vm.saveProfile(kent.copy(bio = "Hungry"))
        assertEquals(SaveState.Saving, vm.profileSaveState)
        gate.complete(Unit)

        assertEquals(SaveState.Saved, vm.profileSaveState)
        assertEquals("Hungry", repo.savedProfiles.single().bio)
    }

    @Test
    fun `a failed save shows an error`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(kent)
        repo.saveProfileError = RuntimeException("UNAVAILABLE")

        vm.saveProfile(kent.copy(bio = "Hungry"))

        assertEquals(SaveState.Failed, vm.profileSaveState)
    }

    @Test
    fun `a preference tap saves the loaded profile without the saved message`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(kent)

        vm.updateProfile(vm.profile.copy(budget = "$ (Budget)"))

        assertEquals(kent.copy(budget = "$ (Budget)"), repo.savedProfiles.single())
        assertEquals("$ (Budget)", vm.profile.budget)
        assertEquals(SaveState.Idle, vm.profileSaveState)
    }

    private val kent = UserProfile.blank("uid_test").copy(name = "Kent", budget = "$$$ (Upscale)", cuisinePreferences = listOf("Thai"))
}
