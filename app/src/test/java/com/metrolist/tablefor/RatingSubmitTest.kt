package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B1: ratings are written once, on Submit, never while a slider moves. */
class RatingSubmitTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    @Test
    fun `moving a slider changes local state only`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        vm.updateRating("2", punctuality = 3, friendliness = 5, reliability = 5, notes = "")
        vm.updateRating("2", punctuality = 2, friendliness = 5, reliability = 5, notes = "")

        assertEquals(2, vm.memberRatings.single { it.memberId == "2" }.punctuality)
        assertTrue("slider moves must not write", repo.ratingSubmissions.isEmpty())
        assertEquals(SaveState.Idle, vm.ratingsSubmitState)
    }

    @Test
    fun `submit writes every rating once and then says thank you`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        vm.updateRating("2", 3, 5, 5, "")
        vm.updateRating("3", 5, 4, 5, "")

        vm.submitRatings()

        assertEquals(1, repo.ratingSubmissions.size)
        val written = repo.ratingSubmissions.single().associateBy { it.memberId }
        assertEquals(setOf("2", "3", "4"), written.keys)
        assertEquals(3, written.getValue("2").punctuality)
        assertEquals(4, written.getValue("3").friendliness)
        assertEquals(SaveState.Saved, vm.ratingsSubmitState)
    }

    @Test
    fun `submit is ignored while a save is in progress`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        val gate = CompletableDeferred<Unit>()
        repo.submitGate = gate

        vm.submitRatings()
        assertEquals(SaveState.Saving, vm.ratingsSubmitState)
        vm.submitRatings()
        gate.complete(Unit)

        assertEquals(1, repo.ratingSubmissions.size)
        assertEquals(SaveState.Saved, vm.ratingsSubmitState)
    }

    @Test
    fun `failed submit shows an error, keeps the ratings and can be retried`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        vm.updateRating("4", 1, 2, 3, "")
        repo.submitError = RuntimeException("UNAVAILABLE")

        vm.submitRatings()

        assertEquals(SaveState.Failed, vm.ratingsSubmitState)
        assertEquals(1, vm.memberRatings.single { it.memberId == "4" }.punctuality)

        repo.submitError = null
        vm.submitRatings()

        assertEquals(SaveState.Saved, vm.ratingsSubmitState)
        assertEquals(1, repo.ratingSubmissions.single().single { it.memberId == "4" }.punctuality)
    }

    @Test
    fun `moving a slider after submitting clears the thank-you message`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        vm.submitRatings()

        vm.updateRating("2", 4, 5, 5, "")

        assertEquals(SaveState.Idle, vm.ratingsSubmitState)
    }

    @Test
    fun `rating document id is group, rater and target`() {
        assertEquals("group_101_uidA_uidB", ratingDocumentId("group_101", "uidA", "uidB"))
    }
}
