package com.stark.note.platform.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCredentialVerifierTest {
    @Test
    fun verifierAcceptsCorrectPinAndRejectsWrongPin() {
        val verifier = PinCredentialVerifier.createVerifier("1234")

        assertTrue(PinCredentialVerifier.verify("1234", verifier))
        assertFalse(PinCredentialVerifier.verify("4321", verifier))
    }

    @Test
    fun verifierAcceptsLongerPasscode() {
        val verifier = PinCredentialVerifier.createVerifier("123456789012")

        assertTrue(PinCredentialVerifier.verify("123456789012", verifier))
        assertFalse(PinCredentialVerifier.verify("123456789011", verifier))
    }

    @Test
    fun failurePolicyLocksAfterFiveWrongAttempts() {
        assertEquals(PinFailureUpdate(remainingFailedAttempts = 1, lockoutMs = null), PinAttemptPolicy.failureResult(0))
        assertEquals(
            PinFailureUpdate(remainingFailedAttempts = 0, lockoutMs = PinAttemptPolicy.LOCKOUT_MS),
            PinAttemptPolicy.failureResult(4)
        )
    }

    @Test
    fun lockoutRemainingSecondsRoundsUp() {
        val result = PinAttemptPolicy.lockedOutResult(nowMs = 1_000L, lockedUntilMs = 31_001L)

        assertEquals(PinVerifyResult.LockedOut(31), result)
    }

    @Test
    fun verifierRejectsUnsupportedWorkFactorBeforeDerivation() {
        val verifier = PinCredentialVerifier.createVerifier("1234")
        val unsupported = verifier.replaceFirst(":120000:", ":999999999:")

        assertFalse(PinCredentialVerifier.verify("1234", unsupported))
    }

    @Test
    fun verifierRejectsInvalidSaltAndHashSizes() {
        assertFalse(PinCredentialVerifier.verify("1234", "v2:120000:AA==:AA=="))
    }
}
