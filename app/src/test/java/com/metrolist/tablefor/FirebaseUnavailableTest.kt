package com.metrolist.tablefor

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * B21: starting without Firebase config (sample mode) is expected. The real
 * repository reports Firebase as unavailable without throwing, and prints no
 * stack trace. These JVM tests run with no Firebase app set up, as in a build
 * without google-services.json.
 */
class FirebaseUnavailableTest {
    @get:Rule val mainDispatcher = MainDispatcherRule()

    /** Runs [block] and returns what it printed to standard error, where printStackTrace writes. */
    private inline fun stderrOf(block: () -> Unit): String {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return captured.toString()
    }

    @Test
    fun `without Firebase config the repository reports Firebase unavailable`() {
        var available = true

        val printed = stderrOf { available = FirebaseTableForRepository().isFirebaseAvailable }

        assertFalse(available)
        assertEquals("no stack trace", "", printed)
    }

    @Test
    fun `sample-mode startup runs on sample data without a stack trace`() {
        lateinit var vm: TableForViewModel

        val printed = stderrOf { vm = TableForViewModel(FirebaseTableForRepository(), TEST_CLOCK) }

        assertEquals(SyncStatus.SampleData, vm.syncStatus)
        assertEquals("Steve Brown", vm.profile.name)
        assertEquals("no stack trace", "", printed)
    }

    @Test
    fun `without Firebase config sign-in returns no user`() = runTest {
        var userId: String? = "not called"

        val printed = stderrOf { userId = FirebaseTableForRepository().signIn() }

        assertNull(userId)
        assertEquals("no stack trace", "", printed)
    }
}
