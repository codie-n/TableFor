package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** B8: your own chat messages show whether the server has them, never a made-up "Read". */
class DeliveryStatusTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    private fun own(delivery: Delivery?) =
        ChatMessage("m1", "Kent", "Hi", "6:00 PM", isMe = true, delivery = delivery)

    @Test
    fun `a message only on this phone shows Sending`() {
        val message = own(ownMessageDelivery(hasPendingWrites = true))

        assertEquals("6:00 PM  Sending…", chatTimeLabel(message))
    }

    @Test
    fun `a message the server has shows Sent`() {
        val message = own(ownMessageDelivery(hasPendingWrites = false))

        assertEquals("6:00 PM  Sent ✓", chatTimeLabel(message))
    }

    @Test
    fun `other people's messages show only the time`() {
        val message = ChatMessage("m2", "Sam", "Hi", "6:00 PM", isMe = false)

        assertEquals("6:00 PM", chatTimeLabel(message))
    }

    @Test
    fun `in sample mode your own messages show only the time`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        vm.sendMessage("See you there")

        val mine = vm.chatMessages.filter { it.isMe }
        assertEquals(2, mine.size) // the sample one and the new one
        mine.forEach { assertEquals(it.timestamp, chatTimeLabel(it)) }
    }

    @Test
    fun `a signed-in user's new message shows Sending while the server hasn't confirmed it`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))
        repo.sendGate = CompletableDeferred() // since B24, a send returns only once the server confirms it

        vm.sendMessage("Hi")

        assertEquals(Delivery.Sending, vm.chatMessages.last().delivery)
    }

    @Test
    fun `the chat shows Sending, then Sent, as the listener reports them`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        repo.chatStream.emit(listOf(own(Delivery.Sending)))
        assertEquals("6:00 PM  Sending…", chatTimeLabel(vm.chatMessages.single()))

        repo.chatStream.emit(listOf(own(Delivery.Sent)))
        assertEquals("6:00 PM  Sent ✓", chatTimeLabel(vm.chatMessages.single()))
    }
}
