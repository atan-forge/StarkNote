package com.stark.note.domain.export

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal object SecureBackupCipher {
    private const val VERSION = 1
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATIONS = 200_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAX_CIPHERTEXT_BYTES = 10 * 1024 * 1024

    fun encrypt(plaintext: String, password: CharArray): BackupEnvelope {
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return BackupEnvelope(
            version = VERSION,
            kdf = BackupKdf(
                algorithm = KDF_ALGORITHM,
                iterations = ITERATIONS,
                keyLengthBits = KEY_LENGTH_BITS
            ),
            salt = salt.toBase64(),
            iv = iv.toBase64(),
            ciphertext = ciphertext.toBase64()
        )
    }

    fun decrypt(envelope: BackupEnvelope, password: CharArray): String {
        if (envelope.version != VERSION) {
            throw UnsupportedBackupEnvelopeException()
        }
        if (
            envelope.kdf.algorithm != KDF_ALGORITHM ||
            envelope.kdf.iterations != ITERATIONS ||
            envelope.kdf.keyLengthBits != KEY_LENGTH_BITS
        ) {
            throw UnsupportedBackupFormatException()
        }

        val salt = envelope.salt.decodeBase64OrThrow()
        val iv = envelope.iv.decodeBase64OrThrow()
        val ciphertext = envelope.ciphertext.decodeBase64OrThrow()
        if (salt.size != SALT_BYTES || iv.size != IV_BYTES || ciphertext.size < GCM_TAG_LENGTH_BITS / 8) {
            throw CorruptBackupEnvelopeException()
        }
        if (ciphertext.size > MAX_CIPHERTEXT_BYTES) {
            throw CorruptBackupEnvelopeException()
        }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
            try {
                SecretKeySpec(bytes.copyOf(), "AES")
            } finally {
                bytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.decodeBase64OrThrow(): ByteArray {
        if (isBlank()) throw CorruptBackupEnvelopeException()
        return runCatching { Base64.getDecoder().decode(this) }
            .getOrElse { throw CorruptBackupEnvelopeException() }
    }
}

internal class UnsupportedBackupEnvelopeException : IllegalStateException()
internal class UnsupportedBackupFormatException : IllegalStateException()
internal class CorruptBackupEnvelopeException : IllegalStateException()
