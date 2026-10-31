package com.huangder.lumibooks.ui.privacy

import com.huangder.lumibooks.privacy.PasscodeCheck
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeGateControllerTest {
    private class FakeBackend(var passcode: String? = "123456", var lockoutAfter: Int = 5) : GateBackend {
        var failures = 0
        override suspend fun verify(passcode: String): PasscodeCheck = when {
            passcode == this.passcode -> PasscodeCheck.Accepted
            ++failures >= lockoutAfter -> PasscodeCheck.LockedOut(untilMillis = 99_000L)
            else -> PasscodeCheck.Rejected(lockoutAfter - failures)
        }
        override suspend fun setPasscode(passcode: String) {
            this.passcode = passcode
        }
    }

    private suspend fun PasscodeGateController.enter(code: String): GateOutcome {
        var outcome = GateOutcome.None
        code.forEach { outcome = type(it) }
        return outcome
    }

    @Test
    fun correctPasscodeUnlocks() = runTest {
        val controller = PasscodeGateController(FakeBackend())
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = true, lockedUntil = 0L)
        assertEquals(GateStage.Enter, controller.stage)
        assertEquals(GateOutcome.Verified, controller.enter("123456"))
        assertEquals(DotTone.Success, controller.tone)
    }

    @Test
    fun wrongPasscodeReportsAttemptsAndIgnoresKeysUntilCleared() = runTest {
        val controller = PasscodeGateController(FakeBackend())
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = true, lockedUntil = 0L)
        assertEquals(GateOutcome.Rejected, controller.enter("000000"))
        assertEquals(GateMessage.Wrong(4), controller.message)
        assertEquals(GateOutcome.None, controller.type('1'))
        assertEquals("000000", controller.input)
        controller.clearAfterError()
        assertEquals("", controller.input)
        assertEquals(GateMessage.Wrong(4), controller.message)
        controller.type('1')
        assertEquals(null, controller.message)
    }

    @Test
    fun lockoutDisablesTheKeypadUntilItExpires() = runTest {
        val controller = PasscodeGateController(FakeBackend(lockoutAfter = 1))
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = true, lockedUntil = 0L)
        assertEquals(GateOutcome.LockedOut, controller.enter("000000"))
        controller.clearAfterError()
        assertTrue(controller.lockedOut)
        assertFalse(controller.acceptsInput)
        controller.expireLockout()
        assertTrue(controller.acceptsInput)
    }

    @Test
    fun firstPasscodeIsCreatedAfterMatchingConfirmation() = runTest {
        val backend = FakeBackend(passcode = null)
        val controller = PasscodeGateController(backend)
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = false, lockedUntil = 0L)
        assertEquals(GateStage.Create, controller.stage)
        assertEquals(GateOutcome.Advanced, controller.enter("135790"))
        assertEquals(GateStage.Confirm, controller.stage)
        assertEquals("", controller.input)
        assertEquals(GateOutcome.Created, controller.enter("135790"))
        assertEquals("135790", backend.passcode)
    }

    @Test
    fun mismatchedConfirmationStartsTheNewPasscodeOver() = runTest {
        val backend = FakeBackend(passcode = null)
        val controller = PasscodeGateController(backend)
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = false, lockedUntil = 0L)
        controller.enter("135790")
        assertEquals(GateOutcome.Rejected, controller.enter("111111"))
        assertEquals(GateMessage.Mismatch, controller.message)
        controller.clearAfterError()
        assertEquals(GateStage.Create, controller.stage)
        assertEquals(null, backend.passcode)
    }

    @Test
    fun changingThePasscodeVerifiesTheOldOneFirst() = runTest {
        val backend = FakeBackend()
        val controller = PasscodeGateController(backend)
        controller.start(PasscodeGatePurpose.ChangePasscode, hasPasscode = true, lockedUntil = 0L)
        assertEquals(GateOutcome.Advanced, controller.enter("123456"))
        assertTrue(controller.verifiedOld)
        assertEquals(GateStage.Create, controller.stage)
        controller.enter("654321")
        assertEquals(GateOutcome.Created, controller.enter("654321"))
        assertEquals("654321", backend.passcode)
    }

    @Test
    fun resetAfterSystemVerificationSkipsTheOldPasscode() = runTest {
        val backend = FakeBackend(lockoutAfter = 1)
        val controller = PasscodeGateController(backend)
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = true, lockedUntil = 99_000L)
        assertFalse(controller.acceptsInput)
        controller.beginReset()
        assertTrue(controller.resetting)
        assertEquals(GateStage.Create, controller.stage)
        assertTrue(controller.acceptsInput)
        controller.enter("222222")
        assertEquals(GateOutcome.Created, controller.enter("222222"))
        assertEquals("222222", backend.passcode)
    }

    @Test
    fun backspaceRemovesTheLastDigit() = runTest {
        val controller = PasscodeGateController(FakeBackend())
        controller.start(PasscodeGatePurpose.Unlock, hasPasscode = true, lockedUntil = 0L)
        controller.enter("12")
        controller.backspace()
        assertEquals("1", controller.input)
    }
}
