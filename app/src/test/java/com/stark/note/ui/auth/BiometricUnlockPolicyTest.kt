package com.stark.note.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricUnlockPolicyTest {
    @Test
    fun biometricUnlockRequiresPinAndAvailability() {
        assertFalse(BiometricUnlockPolicy.shouldUse(hasPin = false, available = true, enabled = true))
        assertFalse(BiometricUnlockPolicy.shouldUse(hasPin = true, available = false, enabled = true))
        assertFalse(BiometricUnlockPolicy.shouldUse(hasPin = true, available = true, enabled = false))
        assertTrue(BiometricUnlockPolicy.shouldUse(hasPin = true, available = true, enabled = true))
    }
}
