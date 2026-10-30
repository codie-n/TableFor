package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B14: signed-in users see only real chat messages; the sample ones are for sample mode. */
class EmptyChatTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    @Test
    fun `a signed-in user sees no sample messages while the chat loads`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(emptyList<ChatMessage>(), vm.chatMessages)
        assertFalse(vm.chatLoaded)
    }

    @Test
    fun `an empty group chat stays empty for a signed-in user`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.chatStream.emit(emptyList())

        assertEquals(emptyList<ChatMessage>(), vm.chatMessages)
        assertTrue(vm.chatLoaded)
    }

    @Test
    fun `a chat that empties out shows as empty`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.chatStream.emit(listOf(ChatMessage("m1", "Sam", "Hi", "6:00 PM", isMe = false)))

        repo.chatStream.emit(emptyList())

        assertEquals(emptyList<ChatMessage>(), vm.chatMessages)
    }

    @Test
    fun `a chat error before the first messages shows no sample messages`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.chatStream.fail()

        assertEquals(emptyList<ChatMessage>(), vm.chatMessages)
        assertEquals(SyncStatus.SyncError, vm.syncStatus)
    }

    @Test
    fun `sample mode shows the sample messages`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertEquals(3, vm.chatMessages.size)
        assertTrue(vm.chatLoaded)
    }

    @Test
    fun `a failed sign-in shows the sample messages`() {
        repo.signInResult = null

        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertEquals(3, vm.chatMessages.size)
        assertTrue(vm.chatLoaded)
    }
}
