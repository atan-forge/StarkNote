package com.stark.note.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import com.stark.note.domain.lock.NoteBodyCipher

class CryptoService : NoteBodyCipher {

    private val provider = "AndroidKeyStore"
    private val keyAlias = "stark_note_master_key"
    private val transformation = "AES/GCM/NoPadding"

    @Synchronized
    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(provider).apply { load(null) }
        keyStore.getKey(keyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return encryptedString to iv
    }

    override fun decrypt(encryptedText: String, iv: String): String {
        val cipher = Cipher.getInstance(transformation)
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        val decodedBytes = Base64.decode(encryptedText, Base64.NO_WRAP)
        val decryptedBytes = cipher.doFinal(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
