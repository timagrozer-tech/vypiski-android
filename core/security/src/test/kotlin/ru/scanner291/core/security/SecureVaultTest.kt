package ru.scanner291.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

/** Этап 19: AES-256-GCM — обратимость, новый IV, обнаружение подмены, чужой ключ, привязка к имени. */
class SecureVaultTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val k = key()
    private val box = GcmBox({ k })
    private val secret = "Подпись: Волков И.И.".toByteArray()

    @Test fun roundTripWithFreshIv() {
        val a = box.encrypt(secret); val b = box.encrypt(secret)
        assertArrayEquals(secret, box.decrypt(a))
        assertFalse("каждый раз новый IV", a.contentEquals(b))
        assertEquals(1 + 12 + secret.size + 16, a.size)
        assertFalse("открытого текста в шифре нет", String(a, Charsets.ISO_8859_1).contains("Волков"))
    }

    @Test fun tamperingAndWrongKeyAreDetected() {
        val a = box.encrypt(secret)
        val t = a.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertTrue(runCatching { box.decrypt(t) }.exceptionOrNull() is AEADBadTagException)
        assertTrue(runCatching { GcmBox({ key() }).decrypt(a) }.isFailure)
        assertTrue(runCatching { box.decrypt(byteArrayOf(9, 9, 9)) }.isFailure)
    }

    @Test fun vaultBindsDataToNameAndRejectsPaths() {
        val dir = Files.createTempDirectory("vault").toFile()
        val v = SecureVault(dir, box)
        v.put("sig_1", secret)
        assertArrayEquals(secret, v.get("sig_1"))
        assertNull(v.get("missing"))
        // Подмена файла другим (переименование) обнаруживается: имя входит в AAD
        v.put("sig_2", "другое".toByteArray())
        dir.resolve("sig_2.bin").copyTo(dir.resolve("sig_1.bin"), overwrite = true)
        assertTrue(runCatching { v.get("sig_1") }.isFailure)
        assertTrue(runCatching { v.put("../escape", secret) }.isFailure)
        assertTrue(v.delete("sig_2"))
        dir.deleteRecursively()
    }
}
