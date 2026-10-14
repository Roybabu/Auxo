package com.auxo.pos.core.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the backup passphrase is kept on this phone, so scheduled backups can lock their files without anyone
 * typing it. It is a convenience for making backups only: restoring on another phone always asks for the
 * passphrase.
 */
interface BackupPassphraseStore {
    /** True when backups are meant to be locked, whether or not the passphrase can still be read. */
    fun isSet(): Boolean

    /** The passphrase, or null when none is set or it can no longer be read. */
    fun get(): String?

    fun set(passphrase: String)

    fun clear()
}

/**
 * Keeps the passphrase encrypted with an AES key that lives in the Android Keystore and never leaves it, so the
 * stored text is useless on its own. The preferences file is excluded from phone backups (the app turns them off).
 */
class KeystoreBackupPassphraseStore(context: Context) : BackupPassphraseStore {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isSet(): Boolean = prefs.contains(KEY_BLOB)

    override fun get(): String? {
        val blob = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            }
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    override fun set(passphrase: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_BLOB, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "backup_protection"
        const val KEY_BLOB = "passphrase"
        const val ALIAS = "auxo_backup_passphrase"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
