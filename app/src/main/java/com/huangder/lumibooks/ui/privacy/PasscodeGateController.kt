package com.huangder.lumibooks.ui.privacy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.privacy.HiddenLibraryPasscodeStore
import com.huangder.lumibooks.privacy.PasscodeCheck
import kotlinx.coroutines.delay

interface GateBackend {
    suspend fun verify(passcode: String): PasscodeCheck
    suspend fun setPasscode(passcode: String)
}

enum class PasscodeGatePurpose {
    /** Open the hidden library; creates the passcode first when there is none. */
    Unlock,
    /** Confirm the current passcode before a sensitive setting changes. */
    Verify,
    ChangePasscode
}

internal enum class GateStage { Enter, Create, Confirm, OfferBiometric }

internal sealed interface GateMessage {
    data class Wrong(val attemptsLeft: Int) : GateMessage
    data object Mismatch : GateMessage
    data object ResetUnavailable : GateMessage
    data class Text(val text: CharSequence) : GateMessage
}

internal enum class GateOutcome { None, Advanced, Rejected, LockedOut, Verified, Created }

/** Stage machine of the passcode gate. The composable owns motion; this owns the rules. */
internal class PasscodeGateController(private val backend: GateBackend) {
    var purpose by mutableStateOf(PasscodeGatePurpose.Unlock); private set
    var stage by mutableStateOf(GateStage.Enter); private set
    var input by mutableStateOf(""); private set
    var tone by mutableStateOf(DotTone.Normal); private set
    var message by mutableStateOf<GateMessage?>(null); private set
    var lockedUntil by mutableLongStateOf(0L); private set
    /** Set after the system lock screen confirmed the owner ("forgot passcode"). */
    var resetting by mutableStateOf(false); private set
    var verifiedOld by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    private var firstEntry = ""

    val lockedOut: Boolean get() = lockedUntil > 0L
    val acceptsInput: Boolean get() = !busy && !lockedOut && stage != GateStage.OfferBiometric

    fun start(purpose: PasscodeGatePurpose, hasPasscode: Boolean, lockedUntil: Long) {
        this.purpose = purpose
        stage = if (hasPasscode) GateStage.Enter else GateStage.Create
        this.lockedUntil = lockedUntil
        input = ""
        firstEntry = ""
        tone = DotTone.Normal
        message = null
        resetting = false
        verifiedOld = false
        busy = false
    }

    suspend fun type(digit: Char): GateOutcome {
        if (!acceptsInput || tone != DotTone.Normal || input.length >= LENGTH) return GateOutcome.None
        message = null
        input += digit
        if (input.length < LENGTH) return GateOutcome.None
        busy = true
        return try {
            submit(input)
        } finally {
            busy = false
        }
    }

    fun backspace() {
        if (!busy && tone == DotTone.Normal && input.isNotEmpty()) input = input.dropLast(1)
    }

    /** Runs after the rejection shake. A mismatch starts the new passcode over. */
    fun clearAfterError() {
        input = ""
        tone = DotTone.Normal
        if (stage == GateStage.Confirm) stage = GateStage.Create
    }

    fun beginReset() {
        resetting = true
        stage = GateStage.Create
        lockedUntil = 0L
        input = ""
        firstEntry = ""
        tone = DotTone.Normal
        message = null
    }

    fun offerBiometric() {
        stage = GateStage.OfferBiometric
        input = ""
        tone = DotTone.Normal
    }

    fun markVerified() {
        tone = DotTone.Success
    }

    fun showMessage(value: GateMessage) {
        message = value
    }

    fun expireLockout() {
        lockedUntil = 0L
        message = null
    }

    private suspend fun submit(code: String): GateOutcome = when (stage) {
        GateStage.Enter -> when (val result = backend.verify(code)) {
            PasscodeCheck.Accepted -> if (purpose == PasscodeGatePurpose.ChangePasscode) {
                verifiedOld = true
                advanceTo(GateStage.Create)
                GateOutcome.Advanced
            } else {
                tone = DotTone.Success
                GateOutcome.Verified
            }
            is PasscodeCheck.Rejected -> {
                tone = DotTone.Error
                message = GateMessage.Wrong(result.attemptsBeforeLockout)
                GateOutcome.Rejected
            }
            is PasscodeCheck.LockedOut -> {
                tone = DotTone.Error
                lockedUntil = result.untilMillis
                GateOutcome.LockedOut
            }
        }
        GateStage.Create -> {
            firstEntry = code
            advanceTo(GateStage.Confirm)
            GateOutcome.Advanced
        }
        GateStage.Confirm -> if (code == firstEntry) {
            backend.setPasscode(code)
            tone = DotTone.Success
            GateOutcome.Created
        } else {
            firstEntry = ""
            tone = DotTone.Error
            message = GateMessage.Mismatch
            GateOutcome.Rejected
        }
        GateStage.OfferBiometric -> GateOutcome.None
    }

    /** Lets the sixth dot land before the row empties for the next step. */
    private suspend fun advanceTo(next: GateStage) {
        delay(ADVANCE_DELAY_MS)
        stage = next
        input = ""
        tone = DotTone.Normal
    }

    companion object {
        const val LENGTH = HiddenLibraryPasscodeStore.PASSCODE_LENGTH
        const val ADVANCE_DELAY_MS = 160L
    }
}
