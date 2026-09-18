package com.sync.service

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val ALGO = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * تشفير نص باستخدام AES-256-GCM
     * الناتج: Base64(IV + ciphertext)
     */
    fun encrypt(plain: String, keyBase64: String): String {
        return try {
            val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
            val key = SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

            // دمج IV + ciphertext
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plain // fallback
        }
    }

    /**
     * فك تشفير نص مشفر بـ AES-256-GCM
     */
    fun decrypt(encoded: String, keyBase64: String): String {
        return try {
            val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
            val key = SecretKeySpec(keyBytes, "AES")

            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH) return encoded

            val iv = ByteArray(IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH)

            val ciphertext = ByteArray(combined.size - IV_LENGTH)
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            encoded // fallback
        }
    }
}
