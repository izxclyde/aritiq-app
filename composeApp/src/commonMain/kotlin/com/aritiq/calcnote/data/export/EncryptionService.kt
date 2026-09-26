package com.aritiq.calcnote.data.export

/**
 * The file is not a well-formed Aritiq export: truncated, or missing the header entirely.
 * Distinct from a failed decryption, which almost always means the password is wrong -- AEAD
 * cannot tell a wrong key from a tampered payload, so that case stays reported as a password
 * problem. Named rather than an IllegalArgumentException so the import UI can say which it hit
 * instead of showing "Wrong password" for a file no password could ever open.
 */
class CorruptExportException(message: String) : Exception(message)

class EncryptionService {    fun encrypt(plaintext: String, password: String): ByteArray = encryptImpl(plaintext, password)
    fun decrypt(ciphertext: ByteArray, password: String): String = decryptImpl(ciphertext, password)
    fun encryptWithKey(plaintext: String, key: ByteArray): ByteArray = encryptWithKeyImpl(plaintext, key)
    fun decryptWithKey(ciphertext: ByteArray, key: ByteArray): String = decryptWithKeyImpl(ciphertext, key)
    fun encryptWithKeyAndSalt(plaintext: String, key: ByteArray, salt: ByteArray): ByteArray =
        encryptWithKeyAndSaltImpl(plaintext, key, salt)
    fun isEncrypted(data: ByteArray): Boolean = isEncryptedImpl(data)
    fun deriveKey(password: String, salt: ByteArray): ByteArray = deriveKeyImpl(password, salt)
    fun hashPassword(password: String): String = hashPasswordImpl(password)
    fun verifyPassword(password: String, hash: String): Boolean = verifyPasswordImpl(password, hash)
    fun generateSalt(): ByteArray = generateSaltImpl()
}

expect fun encryptImpl(plaintext: String, password: String): ByteArray
expect fun decryptImpl(ciphertext: ByteArray, password: String): String
expect fun encryptWithKeyImpl(plaintext: String, key: ByteArray): ByteArray
expect fun encryptWithKeyAndSaltImpl(plaintext: String, key: ByteArray, salt: ByteArray): ByteArray
expect fun decryptWithKeyImpl(ciphertext: ByteArray, key: ByteArray): String
expect fun isEncryptedImpl(data: ByteArray): Boolean
expect fun deriveKeyImpl(password: String, salt: ByteArray): ByteArray
expect fun hashPasswordImpl(password: String): String
expect fun verifyPasswordImpl(password: String, hash: String): Boolean
expect fun generateSaltImpl(): ByteArray

fun decodeHex(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
