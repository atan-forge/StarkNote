package com.stark.note.platform.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    object Failure : PinVerifyResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerifyResult()
}

enum class CredentialFailureReason {
    CORRUPT_DATA,
    KEY_UNAVAILABLE,
    PERSISTENCE_FAILED
}

sealed class CredentialResult<out T> {
    data class Success<T>(val value: T) : CredentialResult<T>()
    data class Failure(
        val reason: CredentialFailureReason,
        val cause: Throwable? = null
    ) : CredentialResult<Nothing>()
}

interface PinCredentialStore {
    suspend fun savePin(pin: String): CredentialResult<Unit>
    suspend fun verifyPin(pin: String): CredentialResult<PinVerifyResult>
    suspend fun hasPin(): CredentialResult<Boolean>
    suspend fun beginPinReset(): CredentialResult<Unit>
    suspend fun finishPinReset(): CredentialResult<Unit>
    suspend fun isPinResetPending(): CredentialResult<Boolean>
}

interface BiometricPreferenceStore {
    suspend fun isBiometricUnlockEnabled(defaultEnabled: Boolean): CredentialResult<Boolean>
    suspend fun setBiometricUnlockEnabled(enabled: Boolean): CredentialResult<Unit>
}

class SecurePreferences(context: Context) : PinCredentialStore, BiometricPreferenceStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun savePin(pin: String): CredentialResult<Unit> = updateState { state ->
        state.copy(
            pinVerifier = PinCredentialVerifier.createVerifier(pin),
            failedAttempts = 0,
            lockedUntilMs = 0L
        )
    }

    override suspend fun hasPin(): CredentialResult<Boolean> = readState { it.pinVerifier != null }

    override suspend fun verifyPin(pin: String): CredentialResult<PinVerifyResult> = ioLocked {
        when (val loaded = loadState()) {
            is CredentialResult.Failure -> loaded
            is CredentialResult.Success -> {
                val state = loaded.value
                val now = System.currentTimeMillis()
                val lockedOut = PinAttemptPolicy.lockedOutResult(now, state.lockedUntilMs)
                if (lockedOut != null) {
                    CredentialResult.Success(lockedOut)
                } else {
                    val matches = state.pinVerifier?.let { PinCredentialVerifier.verify(pin, it) } == true
                    if (matches) {
                        persistVerificationResult(
                            state.copy(failedAttempts = 0, lockedUntilMs = 0L),
                            PinVerifyResult.Success
                        )
                    } else {
                        val update = PinAttemptPolicy.failureResult(state.failedAttempts)
                        val updatedState = state.copy(
                            failedAttempts = update.remainingFailedAttempts,
                            lockedUntilMs = update.lockoutMs?.let { now + it } ?: 0L
                        )
                        val result = update.lockoutMs?.let { PinVerifyResult.LockedOut(it / 1000L) }
                            ?: PinVerifyResult.Failure
                        persistVerificationResult(updatedState, result)
                    }
                }
            }
        }
    }

    override suspend fun beginPinReset(): CredentialResult<Unit> = withContext(Dispatchers.IO) {
        if (preferences.edit().putBoolean(KEY_RESET_PENDING, true).commit()) {
            CredentialResult.Success(Unit)
        } else {
            CredentialResult.Failure(CredentialFailureReason.PERSISTENCE_FAILED)
        }
    }

    override suspend fun finishPinReset(): CredentialResult<Unit> {
        val cleared = updateState {
        it.copy(
            pinVerifier = null,
            failedAttempts = 0,
            lockedUntilMs = 0L,
            biometricUnlockEnabled = null
        )
        }
        if (cleared is CredentialResult.Failure) return cleared
        return withContext(Dispatchers.IO) {
            if (preferences.edit().putBoolean(KEY_RESET_PENDING, false).commit()) {
                CredentialResult.Success(Unit)
            } else {
                CredentialResult.Failure(CredentialFailureReason.PERSISTENCE_FAILED)
            }
        }
    }

    override suspend fun isPinResetPending(): CredentialResult<Boolean> = withContext(Dispatchers.IO) {
        CredentialResult.Success(preferences.getBoolean(KEY_RESET_PENDING, false))
    }

    override suspend fun isBiometricUnlockEnabled(defaultEnabled: Boolean): CredentialResult<Boolean> =
        readState { it.biometricUnlockEnabled ?: defaultEnabled }

    override suspend fun setBiometricUnlockEnabled(enabled: Boolean): CredentialResult<Unit> = updateState {
        it.copy(biometricUnlockEnabled = enabled)
    }

    private suspend fun <T> readState(transform: (CredentialState) -> T): CredentialResult<T> = ioLocked {
        when (val result = loadState()) {
            is CredentialResult.Failure -> result
            is CredentialResult.Success -> CredentialResult.Success(transform(result.value))
        }
    }

    private suspend fun updateState(transform: (CredentialState) -> CredentialState): CredentialResult<Unit> = ioLocked {
        when (val result = loadState()) {
            is CredentialResult.Failure -> result
            is CredentialResult.Success -> persistState(transform(result.value))
        }
    }

    private suspend fun <T> ioLocked(block: () -> CredentialResult<T>): CredentialResult<T> =
        withContext(Dispatchers.IO) {
            mutex.withLock { block() }
        }

    private fun persistVerificationResult(
        state: CredentialState,
        result: PinVerifyResult
    ): CredentialResult<PinVerifyResult> {
        return when (val persisted = persistState(state)) {
            is CredentialResult.Failure -> persisted
            is CredentialResult.Success -> CredentialResult.Success(result)
        }
    }

    private fun loadState(): CredentialResult<CredentialState> {
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null)
        val encodedIv = preferences.getString(KEY_IV, null)
        if (encodedCiphertext == null && encodedIv == null) {
            return CredentialResult.Success(CredentialState())
        }
        if (encodedCiphertext == null || encodedIv == null) {
            return CredentialResult.Failure(CredentialFailureReason.CORRUPT_DATA)
        }
        return try {
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            if (iv.size != GCM_IV_BYTES) {
                return CredentialResult.Failure(CredentialFailureReason.CORRUPT_DATA)
            }
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val json = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            CredentialResult.Success(CredentialState.fromJson(json))
        } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
            CredentialResult.Failure(CredentialFailureReason.KEY_UNAVAILABLE, e)
        } catch (e: java.security.GeneralSecurityException) {
            CredentialResult.Failure(CredentialFailureReason.CORRUPT_DATA, e)
        } catch (e: Exception) {
            CredentialResult.Failure(CredentialFailureReason.CORRUPT_DATA, e)
        }
    }

    private fun persistState(state: CredentialState): CredentialResult<Unit> {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val ciphertext = cipher.doFinal(state.toJson().toByteArray(Charsets.UTF_8))
            val saved = preferences.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .commit()
            if (saved) {
                CredentialResult.Success(Unit)
            } else {
                CredentialResult.Failure(CredentialFailureReason.PERSISTENCE_FAILED)
            }
        } catch (e: Exception) {
            CredentialResult.Failure(CredentialFailureReason.KEY_UNAVAILABLE, e)
        }
    }

    @Synchronized
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "credential_state"
        private const val KEY_CIPHERTEXT = "state"
        private const val KEY_IV = "state_iv"
        private const val KEY_RESET_PENDING = "pin_reset_pending"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "starknote_credential_state_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }
}

private data class CredentialState(
    val pinVerifier: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntilMs: Long = 0L,
    val biometricUnlockEnabled: Boolean? = null
) {
    fun toJson(): String = JSONObject()
        .put("version", VERSION)
        .put("pinVerifier", pinVerifier)
        .put("failedAttempts", failedAttempts)
        .put("lockedUntilMs", lockedUntilMs)
        .put("biometricUnlockEnabled", biometricUnlockEnabled)
        .toString()

    companion object {
        private const val VERSION = 1

        fun fromJson(json: String): CredentialState {
            val value = JSONObject(json)
            require(value.getInt("version") == VERSION)
            val failedAttempts = value.optInt("failedAttempts", 0)
            val lockedUntilMs = value.optLong("lockedUntilMs", 0L)
            require(failedAttempts in 0 until PinAttemptPolicy.MAX_FAILED_ATTEMPTS)
            require(lockedUntilMs >= 0L)
            return CredentialState(
                pinVerifier = value.optString("pinVerifier").takeIf { it.isNotBlank() && it != "null" },
                failedAttempts = failedAttempts,
                lockedUntilMs = lockedUntilMs,
                biometricUnlockEnabled = if (value.isNull("biometricUnlockEnabled")) {
                    null
                } else {
                    value.getBoolean("biometricUnlockEnabled")
                }
            )
        }
    }
}
