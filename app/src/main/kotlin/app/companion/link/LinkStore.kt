package app.companion.link

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.companion.core.LinkCrypto
import app.companion.core.LinkKeys
import app.companion.core.LinkPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LinkStore {
    private const val ALIAS = "companion.link"
    private const val IV = 12

    private fun p(c: Context) = c.getSharedPreferences("link", Context.MODE_PRIVATE)

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keystore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    private fun seal(b: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return LinkCrypto.b64(c.iv + c.doFinal(b))
    }

    private fun open(s: String): ByteArray {
        val b = LinkCrypto.unb64(s)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV))
            doFinal(b, IV, b.size - IV)
        }
    }

    fun on(c: Context) = p(c).getBoolean("on", false)

    fun setOn(c: Context, on: Boolean) = p(c).edit().putBoolean("on", on).apply()

    fun pair(c: Context): LinkPair? = runCatching {
        p(c).getString("pair", null)?.let(::open)?.let { LinkPair(it.copyOfRange(0, 32), it.copyOfRange(32, 64), it.decodeToString(64)) }
    }.getOrNull()

    fun setPair(c: Context, x: LinkPair) = p(c).edit().putString("pair", seal(x.mac + x.secret + x.name.encodeToByteArray())).putBoolean("on", true).apply()

    fun unpair(c: Context) = p(c).edit().remove("pair").putBoolean("on", false).apply()

    fun keys(c: Context): LinkKeys {
        val have = runCatching { p(c).getString("priv", null)?.let(::open) }.getOrNull()
        if (have != null) return LinkKeys(have, LinkCrypto.pub(have))
        return LinkCrypto.keys().also { p(c).edit().putString("priv", seal(it.priv)).apply() }
    }

    fun shares(c: Context): Set<String> = p(c).getStringSet("share", null).orEmpty()

    fun setShare(c: Context, k: String, on: Boolean) = p(c).edit().putStringSet("share", if (on) shares(c) + k else shares(c) - k).apply()

    fun apps(c: Context): Set<String> = p(c).getStringSet("apps", null).orEmpty()

    fun setApp(c: Context, pkg: String, on: Boolean) = p(c).edit().putStringSet("apps", if (on) apps(c) + pkg else apps(c) - pkg).apply()

    fun wipe(c: Context) {
        p(c).edit().clear().commit()
        runCatching { keystore().deleteEntry(ALIAS) }
    }
}
