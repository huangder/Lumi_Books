package com.huangder.lumibooks.ui.privacy

import androidx.lifecycle.ViewModel
import com.huangder.lumibooks.privacy.HiddenLibraryLockState
import com.huangder.lumibooks.privacy.HiddenLibraryPasscodeStore
import com.huangder.lumibooks.privacy.HiddenLibrarySession
import com.huangder.lumibooks.privacy.PasscodeCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

@HiltViewModel
class HiddenLibraryLockViewModel @Inject constructor(
    private val passcodeStore: HiddenLibraryPasscodeStore,
    private val session: HiddenLibrarySession
) : ViewModel(), GateBackend {
    val lockState: StateFlow<HiddenLibraryLockState> = passcodeStore.state
    val unlocked: StateFlow<Boolean> = session.unlocked

    // PBKDF2 and the synchronous preference commits stay off the main thread.
    override suspend fun verify(passcode: String): PasscodeCheck =
        withContext(Dispatchers.Default) { passcodeStore.verify(passcode) }

    override suspend fun setPasscode(passcode: String) =
        withContext(Dispatchers.Default) { passcodeStore.setPasscode(passcode) }

    suspend fun clearFailures() = withContext(Dispatchers.IO) { passcodeStore.clearFailures() }

    fun setBiometricEnabled(enabled: Boolean) = passcodeStore.setBiometricEnabled(enabled)

    fun lockedUntil(): Long = passcodeStore.lockedUntil()

    fun unlockSession() = session.unlock()

    fun lockSession() = session.lock()
}
