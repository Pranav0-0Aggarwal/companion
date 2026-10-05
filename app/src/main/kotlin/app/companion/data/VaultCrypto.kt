package app.companion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Locked : Exception()

object VaultCrypto {
    private const val BASE = "companion.vault.base"
    private const val AUTH = "companion.vault.auth"
    private const val WINDOW = 30
    private const val IV = 12
    private const val SEALED: Byte = 1
    private const val OPEN: Byte = 0
    private const val DIR = "vault"

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun spec(alias: String) = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)

    private fun key(alias: String, auth: Boolean): SecretKey? {
        (keystore().getKey(alias, null) as? SecretKey)?.let { return it }
        val s = if (auth) {
            spec(alias)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(WINDOW, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .setInvalidatedByBiometricEnrollment(false)
                .setUnlockedDeviceRequired(true)
        } else {
            spec(alias)
        }
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(s.build()) }.generateKey()
        } catch (_: Exception) {
            if (auth) null else throw IllegalStateException()
        }
    }

    private fun seal(k: SecretKey, plain: ByteArray, ver: Byte): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
        return byteArrayOf(ver) + c.iv + c.doFinal(plain)
    }

    private fun unseal(k: SecretKey, b: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, b, 1, IV)) }
        return c.doFinal(b, 1 + IV, b.size - 1 - IV)
    }

    val strong get() = key(AUTH, true) != null

    fun authed(): Boolean {
        val k = key(AUTH, true) ?: return true
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, k)
            true
        } catch (_: UserNotAuthenticatedException) {
            false
        }
    }

    fun seal(plain: ByteArray): ByteArray {
        val a = key(AUTH, true)
        if (a != null) {
            try {
                return seal(a, plain, SEALED)
            } catch (_: UserNotAuthenticatedException) {
            }
        }
        return seal(key(BASE, false)!!, plain, OPEN)
    }

    fun open(b: ByteArray): ByteArray = when (b.firstOrNull()) {
        SEALED -> try {
            unseal(key(AUTH, true) ?: throw IllegalStateException(), b)
        } catch (_: UserNotAuthenticatedException) {
            throw Locked()
        }
        OPEN -> unseal(key(BASE, false)!!, b)
        else -> throw IllegalArgumentException()
    }

    fun weak(b: ByteArray) = b.firstOrNull() == OPEN

    fun dir(c: Context) = File(c.noBackupFilesDir, DIR).also { it.mkdirs() }

    fun newKey() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun lock(k: ByteArray, plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES")) }
        return c.iv + c.doFinal(plain)
    }

    fun unlock(k: ByteArray, b: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, b, 0, IV)) }
        return c.doFinal(b, IV, b.size - IV)
    }

    fun wipe(c: Context) {
        dir(c).deleteRecursively()
        keystore().apply {
            deleteEntry(BASE)
            deleteEntry(AUTH)
        }
    }
}
