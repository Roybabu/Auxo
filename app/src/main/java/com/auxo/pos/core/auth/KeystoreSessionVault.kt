package com.auxo.pos.core.auth

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Keeps the saved session in a file the phone does not back up. The person's number is encrypted with the public half of a
 * key pair in the phone's secure key store; the private half can only be used after a fingerprint check, and is lost for
 * good if the phone's fingerprints change. Saving needs no fingerprint (it uses the public half); opening always does.
 */
class KeystoreSessionVault(context: Context) : SavedSessionVault {

    private val file = File(context.noBackupFilesDir, FILE_NAME)

    override fun savedName(): String? = runCatching {
        if (!file.exists() || !keyStore().containsAlias(ALIAS)) return null
        String(Base64.decode(file.readLines()[0], Base64.NO_WRAP), Charsets.UTF_8).takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun save(userId: Long, name: String): Boolean = try {
        val publicKey = publicKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP)
        val locked = cipher.doFinal(userId.toString().toByteArray(Charsets.UTF_8))
        file.writeText(
            Base64.encodeToString(name.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\n" +
                Base64.encodeToString(locked, Base64.NO_WRAP),
        )
        true
    } catch (e: Exception) {
        // The phone could not protect it, so nothing is kept: the person signs in with the password as before.
        clear()
        false
    }

    override fun clear() {
        runCatching { file.delete() }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    override fun unlockCipher(): Cipher? = try {
        val key = keyStore().getKey(ALIAS, null) as? PrivateKey
        if (key == null || !file.exists()) {
            clear()
            null
        } else {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key) }
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        clear()
        null
    } catch (e: Exception) {
        clear()
        null
    }

    override fun open(cipher: Cipher): Long? = runCatching {
        val locked = Base64.decode(file.readLines()[1], Base64.NO_WRAP)
        String(cipher.doFinal(locked), Charsets.UTF_8).toLongOrNull()
    }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    /** The public half, made again as a plain key so that encrypting needs no fingerprint. */
    private fun publicKey(): java.security.PublicKey {
        val store = keyStore()
        if (!store.containsAlias(ALIAS)) createKeyPair()
        val key = store.getCertificate(ALIAS).publicKey
        return KeyFactory.getInstance(key.algorithm).generatePublic(X509EncodedKeySpec(key.encoded))
    }

    private fun createKeyPair() {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).apply { initialize(spec) }.generateKeyPair()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "auxo_saved_session"
        const val FILE_NAME = "saved_session.vault"
        const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        val OAEP = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    }
}
