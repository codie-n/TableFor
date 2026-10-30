package com.metrolist.tablefor

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * B24: a message you send gets its delivery from its own write as well as from
 * the chat listener, never goes back from "Sent ✓" to "Sending…", and stays in
 * the chat as "Not sent" if the server rejects it.
 */
class SendOutcomeTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()
    private val denied = RuntimeException("PERMISSION_DENIED: Missing or insufficient permissions.")

    /** Signed in with a name, so the chat can send. */
    private fun signedIn(): TableForViewModel {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = "Kent"))
        return vm
    }

    /** Your message as the chat listener reports it. */
    private fun mine(id: String, delivery: Delivery) =
        ChatMessage(id, "Kent", "Hi", "6:00 PM", isMe = true, delivery = delivery)

    private val fromSam = ChatMessage("m2", "Sam", "Anyone there?", "6:01 PM", isMe = false)

    @Test
    fun `the message on screen and the document written share one ID`() {
        val vm = signedIn()

        vm.sendMessage("Hi")

        assertEquals(listOf(vm.chatMessages.single().id), repo.sentMessageIds)
    }

    @Test
    fun `after the chat listener fails, a send the server confirms shows Sent`() {
        val vm = signedIn()
        repo.chatStream.fail(denied)

        vm.sendMessage("Hi")

        val sent = vm.chatMessages.single()
        assertEquals(Delivery.Sent, sent.delivery)
        assertEquals("${sent.timestamp}  Sent ✓", chatTimeLabel(sent))
    }

    @Test
    fun `a rejected send shows Not sent, and stays after a listener update without it`() {
        val vm = signedIn()
        repo.sendGate = CompletableDeferred()
        vm.sendMessage("Hi")
        val id = repo.sentMessageIds.single()
        repo.chatStream.emit(listOf(mine(id, Delivery.Sending))) // Firestore shows the pending write

        repo.sendError = denied
        repo.sendGate!!.complete(Unit) // the server rejects it...
        repo.chatStream.emit(listOf(fromSam)) // ...so Firestore drops it from the next update

        assertEquals(listOf("m2", id), vm.chatMessages.map { it.id })
        val notSent = vm.chatMessages.last()
        assertEquals(Delivery.NotSent, notSent.delivery)
        assertEquals("${notSent.timestamp}  Not sent", chatTimeLabel(notSent))
        assertEquals("the sync strip doesn't change", SyncStatus.Synced, vm.syncStatus)
    }

    @Test
    fun `a send that never completes, as offline, stays Sending`() {
        val vm = signedIn()
        repo.sendGate = CompletableDeferred() // the server never confirms

        vm.sendMessage("Hi")
        assertEquals(Delivery.Sending, vm.chatMessages.single().delivery)

        repo.chatStream.emit(listOf(mine(repo.sentMessageIds.single(), Delivery.Sending)))
        assertEquals(Delivery.Sending, vm.chatMessages.single().delivery)
    }

    @Test
    fun `a listener update showing the write pending after the send completed doesn't go back to Sending`() {
        val vm = signedIn()
        vm.sendMessage("Hi") // the server confirms at once

        repo.chatStream.emit(listOf(mine(repo.sentMessageIds.single(), Delivery.Sending)))

        assertEquals(Delivery.Sent, vm.chatMessages.single().delivery)
    }

    @Test
    fun `once the listener reports Sent, a later pending update doesn't go back to Sending`() {
        val vm = signedIn()
        repo.sendGate = CompletableDeferred()
        vm.sendMessage("Hi")
        val id = repo.sentMessageIds.single()

        repo.chatStream.emit(listOf(mine(id, Delivery.Sent)))
        repo.chatStream.emit(listOf(mine(id, Delivery.Sending)))
        assertEquals(Delivery.Sent, vm.chatMessages.single().delivery)

        repo.sendGate!!.complete(Unit)
        assertEquals(Delivery.Sent, vm.chatMessages.single().delivery)
    }
}
