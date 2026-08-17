package com.aritiq.calcnote.data.export

import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

private const val MAGIC = "ARITIQ_ENC_V1\u0000"
private const val SALT_SIZE = 32
private const val IV_SIZE = 12
private const val TAG_SIZE = 128
private const val PBKDF2_ITERATIONS = 310_000

actual fun encryptImpl(plaintext: String, password: String): ByteArray {
    val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
    val key = deriveKeyFromPassword(password, salt)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

    val magic = MAGIC.toByteArray(Charsets.US_ASCII)
    return magic + salt + iv + ciphertext
}

actual fun decryptImpl(ciphertext: ByteArray, password: String): String {
    val magicLen = MAGIC.length
    val salt = ciphertext.sliceArray(magicLen until magicLen + SALT_SIZE)
    val iv = ciphertext.sliceArray(magicLen + SALT_SIZE until magicLen + SALT_SIZE + IV_SIZE)
    val encrypted = ciphertext.sliceArray(magicLen + SALT_SIZE + IV_SIZE until ciphertext.size)

    val key = deriveKeyFromPassword(password, salt)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE, iv))
    val plainBytes = cipher.doFinal(encrypted)
    return String(plainBytes, Charsets.UTF_8)
}

actual fun encryptWithKeyImpl(plaintext: String, key: ByteArray): ByteArray {
    val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
    val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
    val aesKey = SecretKeySpec(key, "AES")

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE, iv))
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

    val magic = MAGIC.toByteArray(Charsets.US_ASCII)
    return magic + salt + iv + ciphertext
}

actual fun decryptWithKeyImpl(ciphertext: ByteArray, key: ByteArray): String {
    val magicLen = MAGIC.length
    val iv = ciphertext.sliceArray(magicLen + SALT_SIZE until magicLen + SALT_SIZE + IV_SIZE)
    val encrypted = ciphertext.sliceArray(magicLen + SALT_SIZE + IV_SIZE until ciphertext.size)

    val aesKey = SecretKeySpec(key, "AES")
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(TAG_SIZE, iv))
    val plainBytes = cipher.doFinal(encrypted)
    return String(plainBytes, Charsets.UTF_8)
}

actual fun isEncryptedImpl(data: ByteArray): Boolean {
    val magic = MAGIC.toByteArray(Charsets.US_ASCII)
    return data.size > magic.size && data.sliceArray(0 until magic.size).contentEquals(magic)
}

actual fun deriveKeyImpl(password: String, salt: ByteArray): ByteArray {
    return deriveKeyFromPassword(password, salt).encoded
}

actual fun hashPasswordImpl(password: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

actual fun verifyPasswordImpl(password: String, hash: String): Boolean {
    return hashPasswordImpl(password) == hash
}

actual fun generateSaltImpl(): ByteArray {
    return ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
}

private fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    spec.clearPassword()
    return SecretKeySpec(hash, "AES")
}
