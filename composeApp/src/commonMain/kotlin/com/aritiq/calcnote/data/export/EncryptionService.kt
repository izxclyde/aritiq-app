package com.aritiq.calcnote.data.export

class EncryptionService {
    fun encrypt(plaintext: String, password: String): ByteArray = encryptImpl(plaintext, password)
    fun decrypt(ciphertext: ByteArray, password: String): String = decryptImpl(ciphertext, password)
    fun encryptWithKey(plaintext: String, key: ByteArray): ByteArray = encryptWithKeyImpl(plaintext, key)
    fun decryptWithKey(ciphertext: ByteArray, key: ByteArray): String = decryptWithKeyImpl(ciphertext, key)
    fun isEncrypted(data: ByteArray): Boolean = isEncryptedImpl(data)
    fun deriveKey(password: String, salt: ByteArray): ByteArray = deriveKeyImpl(password, salt)
    fun hashPassword(password: String): String = hashPasswordImpl(password)
    fun verifyPassword(password: String, hash: String): Boolean = verifyPasswordImpl(password, hash)
    fun generateSalt(): ByteArray = generateSaltImpl()
}

expect fun encryptImpl(plaintext: String, password: String): ByteArray
expect fun decryptImpl(ciphertext: ByteArray, password: String): String
expect fun encryptWithKeyImpl(plaintext: String, key: ByteArray): ByteArray
expect fun decryptWithKeyImpl(ciphertext: ByteArray, key: ByteArray): String
expect fun isEncryptedImpl(data: ByteArray): Boolean
expect fun deriveKeyImpl(password: String, salt: ByteArray): ByteArray
expect fun hashPasswordImpl(password: String): String
expect fun verifyPasswordImpl(password: String, hash: String): Boolean
expect fun generateSaltImpl(): ByteArray
