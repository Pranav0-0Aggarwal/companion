package app.companion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.Room
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object Vault {
    private const val ALIAS = "companion.vault"
    private const val PREFS = "vault"
    private const val DB = "companion.db"
    private const val IV = 12

    fun open(c: Context): Db {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(c, Db::class.java, DB)
            .openHelperFactory(SupportOpenHelperFactory(passphrase(c)))
            .addMigrations(Migrate1to2, Migrate2to3, Migrate3to4, Migrate4to5, Migrate5to6, Migrate6to7, Migrate7to8, Migrate8to9)
            .build()
    }

    fun wipe(c: Context) {
        c.deleteDatabase(DB)
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        keystore().deleteEntry(ALIAS)
        VaultCrypto.wipe(c)
    }

    private fun keystore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(): SecretKey {
        (keystore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return g.generateKey()
    }

    private fun passphrase(c: Context): ByteArray {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        prefs.getString("p", null)?.let {
            val b = Base64.decode(it, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, IV))
            return cipher.doFinal(b, IV, b.size - IV)
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(raw)
        prefs.edit().putString("p", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
        return raw
    }
}
