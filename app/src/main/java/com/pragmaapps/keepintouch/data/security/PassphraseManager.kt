package com.pragmaapps.keepintouch.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.pragmaapps.keepintouch.data.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PassphraseManager"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "keep_in_touch_db_key"
private const val PREFS_FILE_NAME = "keep_in_touch_secure_prefs"
private const val PREF_IV = "db_passphrase_iv_b64"
private const val PREF_CIPHERTEXT = "db_passphrase_ciphertext_b64"
private const val PASSPHRASE_BYTE_LENGTH = 32
private const val GCM_TAG_LENGTH_BITS = 128
private const val TRANSFORMATION = "AES/GCM/NoPadding"

/**
 * Generates and stores the SQLCipher database passphrase, encrypted at rest with an AES-256 key
 * that never leaves the Android Keystore. Only the *encrypted* passphrase blob is persisted (in
 * ordinary SharedPreferences); decrypting it requires the Keystore-resident key, which the OS
 * refuses to export. Nothing here ever touches the network, and nothing here is a hardcoded or
 * shared secret - a compromised APK gives an attacker no passphrase, only ciphertext plus the
 * Keystore's own access-control gate.
 *
 * Deliberately hand-rolled on plain `java.security`/`javax.crypto` framework APIs rather than
 * `androidx.security:security-crypto` (EncryptedSharedPreferences/MasterKey): that library was
 * deprecated by Google in April 2025 with no direct official replacement, so this avoids taking
 * a dependency on an unmaintained library for the one piece of the app that's security-critical.
 */
@Singleton
class PassphraseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Runs during Hilt's singleton graph construction (DatabaseModule.provideAppDatabase), before
     * the app can do anything at all - there's no safe fallback without this passphrase, so a
     * failure here (e.g. a Keystore key invalidated by the OS after a lock-screen/biometric
     * change on some OEMs) is genuinely fatal and must still crash. This only adds a log (which
     * also mirrors to Logcat per AppLogger's own design) before rethrowing, so the failure is at
     * least diagnosable instead of a bare stack trace with no app-level context.
     */
    fun getOrCreatePassphrase(): ByteArray = try {
        getOrCreatePassphraseUnsafe()
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Failed to get or create the DB passphrase - app cannot start without it", t)
        throw t
    }

    private fun getOrCreatePassphraseUnsafe(): ByteArray {
        val existingIv = prefs.getString(PREF_IV, null)
        val existingCiphertext = prefs.getString(PREF_CIPHERTEXT, null)
        if (existingIv != null && existingCiphertext != null) {
            return decrypt(
                iv = Base64.decode(existingIv, Base64.NO_WRAP),
                ciphertext = Base64.decode(existingCiphertext, Base64.NO_WRAP)
            )
        }

        val passphrase = ByteArray(PASSPHRASE_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        val (iv, ciphertext) = encrypt(passphrase)
        prefs.edit()
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
        return passphrase
    }

    private fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv to ciphertext
    }

    private fun decrypt(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
