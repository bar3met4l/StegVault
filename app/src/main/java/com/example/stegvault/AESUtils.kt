package com.example.stegvault

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESUtils {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private val IV = ByteArray(16) // 16-byte IV for AES (initialized to zeroes for simplicity)

    // Function to hash a password using SHA-256
    private fun sha256(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    fun encrypt(data: String, password: String): String {
        val keyHash = sha256(password)
        val keySpec = SecretKeySpec(keyHash.copyOf(16), "AES") // Use the first 16 bytes of the hash
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, IvParameterSpec(IV))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }

    fun decrypt(data: String, password: String): String {
        val keyHash = sha256(password)
        val keySpec = SecretKeySpec(keyHash.copyOf(16), "AES") // Use the first 16 bytes of the hash
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(IV))
        val decodedValue = Base64.decode(data, Base64.DEFAULT)
        val decryptedValue = cipher.doFinal(decodedValue)
        return String(decryptedValue, Charsets.UTF_8)
    }
}
