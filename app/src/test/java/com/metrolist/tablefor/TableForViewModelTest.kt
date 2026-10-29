package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Fixed "now" for every test: Thursday 2026-10-29, 8:00 AM in Honolulu. */
val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-29T18:00:00Z"), ZoneId.of("Pacific/Honolulu"))

class TableForViewModelTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    @Test
    fun `starts with a seven-day grid of eleven slots per day`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(7 * 11, vm.availabilityGrid.size)
        assertEquals(7, vm.availabilityGrid.map { it.dayLabel }.distinct().size)
    }

    @Test
    fun `rating list has every group member except you`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(listOf("Sarah Jenkins", "Marcus Vance", "Elena Rostova"), vm.memberRatings.map { it.memberName })
    }

    @Test
    fun `chat messages from the repository replace the sample messages`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        val cloud = listOf(ChatMessage("m1", "Sam", "Hi", "6:00 PM", isMe = false))

        repo.chatStream.emit(cloud)

        assertEquals(cloud, vm.chatMessages)
    }
}
