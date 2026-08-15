package com.stark.note.platform.storage

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PinCredentialVerifier {
    fun createVerifier(pin: String): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val hash = deriveHash(pin, salt)
        return listOf(
            VERIFIER_VERSION,
            PBKDF2_ITERATIONS.toString(),
            salt.toBase64(),
            hash.toBase64()
        ).joinToString(":")
    }

    fun verify(pin: String, verifier: String): Boolean {
        val parts = verifier.split(":")
        if (parts.size != 4 || parts[0] != VERIFIER_VERSION) return false

        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { parts[2].fromBase64() }.getOrNull() ?: return false
        val expectedHash = runCatching { parts[3].fromBase64() }.getOrNull() ?: return false
        if (iterations != PBKDF2_ITERATIONS || salt.size != SALT_BYTES || expectedHash.size != KEY_LENGTH_BITS / 8) {
            return false
        }
        val actualHash = deriveHash(pin, salt, iterations)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    private fun deriveHash(
        pin: String,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded.also {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)

    private const val VERIFIER_VERSION = "v2"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
}

internal object PinAttemptPolicy {
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_MS = 30_000L

    fun lockedOutResult(nowMs: Long, lockedUntilMs: Long): PinVerifyResult.LockedOut? {
        if (lockedUntilMs <= nowMs) return null
        return PinVerifyResult.LockedOut(((lockedUntilMs - nowMs) + 999L) / 1000L)
    }

    fun failureResult(failedAttempts: Int): PinFailureUpdate {
        val nextAttempts = failedAttempts + 1
        return if (nextAttempts >= MAX_FAILED_ATTEMPTS) {
            PinFailureUpdate(remainingFailedAttempts = 0, lockoutMs = LOCKOUT_MS)
        } else {
            PinFailureUpdate(remainingFailedAttempts = nextAttempts, lockoutMs = null)
        }
    }
}

internal data class PinFailureUpdate(
    val remainingFailedAttempts: Int,
    val lockoutMs: Long?
)
