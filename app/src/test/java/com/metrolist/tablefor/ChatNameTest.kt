package com.metrolist.tablefor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** B12: you need a name to chat, and others' blank names show as "Group member". */
class ChatNameTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val repo = FakeTableForRepository()

    private fun signedInWithName(name: String): TableForViewModel {
        val vm = TableForViewModel(repo, TEST_CLOCK)
        repo.profileStream.emit(UserProfile.blank("uid_test").copy(name = name))
        return vm
    }

    @Test
    fun `with a blank profile name the chat asks for a name`() {
        val vm = signedInWithName("")

        assertFalse(vm.canChat)
        assertTrue(vm.chatNeedsName)
    }

    @Test
    fun `a message can't be sent with a blank name`() {
        val vm = signedInWithName("  ")
        val shownBefore = vm.chatMessages

        vm.sendMessage("hi")

        assertTrue(repo.sentMessages.isEmpty())
        assertEquals(shownBefore, vm.chatMessages)
    }

    @Test
    fun `after saving a name you can chat under that name`() {
        val vm = signedInWithName("")

        vm.saveProfile(vm.profile.copy(name = "Kent"))
        vm.sendMessage("hi")

        assertTrue(vm.canChat)
        assertFalse(vm.chatNeedsName)
        assertEquals(listOf("Kent" to "hi"), repo.sentMessages)
    }

    @Test
    fun `while the profile loads the chat neither sends nor asks for a name`() {
        val vm = TableForViewModel(repo, TEST_CLOCK)

        assertFalse(vm.canChat)
        assertFalse(vm.chatNeedsName)
    }

    @Test
    fun `messages from others with a blank name show as Group member`() {
        val vm = signedInWithName("Kent")

        repo.chatStream.emit(
            listOf(
                ChatMessage("m1", "", "Hi", "6:00 PM", isMe = false),
                ChatMessage("m2", "   ", "Hello", "6:01 PM", isMe = false),
                ChatMessage("m3", "Sam", "Hey", "6:02 PM", isMe = false),
            )
        )

        assertEquals(listOf("Group member", "Group member", "Sam"), vm.chatMessages.map { it.senderName })
    }

    @Test
    fun `sample mode can chat with the sample name`() {
        val vm = TableForViewModel(FakeTableForRepository(isFirebaseAvailable = false), TEST_CLOCK)

        assertTrue(vm.canChat)
        assertFalse(vm.chatNeedsName)
    }
}
