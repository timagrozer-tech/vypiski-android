package ru.scanner291.core.security

import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM (этап 19): формат «версия(1) | IV(12) | шифртекст+тег(16)». Каждый вызов — новый IV;
 * любое изменение файла обнаруживается тегом (AEADBadTagException). Ключ — из [key]
 * (в приложении — Android Keystore, ключ не покидает аппаратное хранилище; в тестах — программный).
 */
class GcmBox(private val key: () -> SecretKey, private val random: SecureRandom = SecureRandom()) {
    fun encrypt(plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        c.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        aad?.let(c::updateAAD)
        return byteArrayOf(VERSION) + iv + c.doFinal(plain)
    }

    fun decrypt(box: ByteArray, aad: ByteArray? = null): ByteArray {
        require(box.size > 1 + IV_LEN + TAG_BITS / 8 && box[0] == VERSION) { "Неизвестный формат зашифрованных данных" }
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, box, 1, IV_LEN))
        aad?.let(c::updateAAD)
        return c.doFinal(box, 1 + IV_LEN, box.size - 1 - IV_LEN)
    }

    companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
    }
}

/** Ключ AES-256 в Android Keystore (создаётся при первом использовании, не экспортируется). */
object KeystoreKey {
    private const val ALIAS = "scanner291_vault_v1"

    fun get(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }
}

/**
 * Защищённое хранилище файлов (подписи, секреты): filesDir/vault/<name>.bin, атомарная запись.
 * Имя проверяется — выход за пределы папки невозможен.
 */
class SecureVault(private val dir: File, private val box: GcmBox) {
    private fun file(name: String): File {
        require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Недопустимое имя в хранилище" }
        return File(dir, "$name.bin")
    }

    fun put(name: String, data: ByteArray) {
        dir.mkdirs()
        val f = file(name); val tmp = File(dir, ".tmp-$name")
        tmp.writeBytes(box.encrypt(data, aad = name.toByteArray()))
        if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "Не удалось записать в хранилище" } }
    }

    /** null — нет такого; исключение — файл изменён или ключ другой (данные не выдаются). */
    fun get(name: String): ByteArray? = file(name).takeIf { it.isFile }?.let { box.decrypt(it.readBytes(), aad = name.toByteArray()) }

    fun delete(name: String): Boolean = file(name).delete()
}
