package com.huangder.lumibooks.privacy

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HiddenLibraryPasscodeStoreTest {
    private var now = 1_000_000L
    private lateinit var store: HiddenLibraryPasscodeStore

    @Before
    fun setUp() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("hidden_library_lock_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = HiddenLibraryPasscodeStore(preferences) { now }
    }

    @Test
    fun correctPasscodeIsAcceptedAndOnlyAHashIsStored() {
        assertFalse(store.state.value.hasPasscode)
        store.setPasscode("246810")
        assertTrue(store.state.value.hasPasscode)
        assertEquals(PasscodeCheck.Accepted, store.verify("246810"))
        val stored = RuntimeEnvironment.getApplication()
            .getSharedPreferences("hidden_library_lock_test", Context.MODE_PRIVATE).all.values
        assertTrue(stored.none { it.toString().contains("246810") })
    }

    @Test
    fun wrongAttemptsCountDownThenLockOut() {
        store.setPasscode("123456")
        repeat(4) { index ->
            assertEquals(PasscodeCheck.Rejected(4 - index), store.verify("000000"))
        }
        assertEquals(PasscodeCheck.LockedOut(now + 30_000L), store.verify("000000"))
        // The right passcode is refused too until the lockout ends.
        assertTrue(store.verify("123456") is PasscodeCheck.LockedOut)
        now += 30_001L
        assertEquals(PasscodeCheck.Accepted, store.verify("123456"))
        assertEquals(0, store.state.value.failedAttempts)
    }

    @Test
    fun lockoutsGrowAfterEachFurtherFailure() {
        store.setPasscode("123456")
        repeat(5) { store.verify("000000") }
        now += 30_001L
        assertEquals(PasscodeCheck.LockedOut(now + 60_000L), store.verify("000000"))
        now += 60_001L
        assertEquals(PasscodeCheck.LockedOut(now + 5 * 60_000L), store.verify("000000"))
        now += 5 * 60_001L
        store.verify("000000")
        now += 15 * 60_001L
        assertEquals(PasscodeCheck.LockedOut(now + 15 * 60_000L), store.verify("000000"))
    }

    @Test
    fun movingTheClockBackCannotExtendALockout() {
        store.setPasscode("123456")
        repeat(5) { store.verify("000000") }
        now -= 24 * 60 * 60_000L
        assertTrue(store.lockedUntil() <= now + 30_000L)
    }

    @Test
    fun clearingFailuresEndsTheLockout() {
        store.setPasscode("123456")
        repeat(5) { store.verify("000000") }
        store.clearFailures()
        assertEquals(0L, store.lockedUntil())
        assertEquals(PasscodeCheck.Accepted, store.verify("123456"))
    }

    @Test
    fun newPasscodeReplacesTheOldOneAndResetsFailures() {
        store.setPasscode("123456")
        store.verify("000000")
        store.setPasscode("654321")
        assertEquals(0, store.state.value.failedAttempts)
        assertEquals(PasscodeCheck.Accepted, store.verify("654321"))
        assertEquals(PasscodeCheck.Rejected(4), store.verify("123456"))
    }

    @Test
    fun biometricSwitchIsPublished() {
        store.setBiometricEnabled(true)
        assertTrue(store.state.value.biometricEnabled)
        store.setBiometricEnabled(false)
        assertFalse(store.state.value.biometricEnabled)
    }

    @Test
    fun onlySixDigitPasscodesAreValid() {
        assertTrue(HiddenLibraryPasscodeStore.isValidPasscode("012345"))
        assertFalse(HiddenLibraryPasscodeStore.isValidPasscode("12345"))
        assertFalse(HiddenLibraryPasscodeStore.isValidPasscode("12345a"))
        assertFalse(HiddenLibraryPasscodeStore.isValidPasscode("1234567"))
    }
}
