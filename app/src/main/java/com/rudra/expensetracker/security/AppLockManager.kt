package com.rudra.expensetracker.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the app-lock PIN.
 *
 * The PIN is never written in the clear: a per-install random salt is combined
 * with the PIN and hashed, and only the salt and digest are kept -- inside
 * EncryptedSharedPreferences, which is itself backed by a Keystore master key.
 * Verification is constant-time so a wrong PIN reveals nothing by how long it
 * took to reject.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "app_lock",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isPinSet: Boolean get() = prefs.contains(KEY_DIGEST)

    fun setPin(pin: String) {
        require(pin.length >= MIN_PIN_LENGTH) { "PIN too short" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_DIGEST, digest(pin, salt).toHex())
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.fromHex() ?: return false
        val expected = prefs.getString(KEY_DIGEST, null)?.fromHex() ?: return false
        return MessageDigest.isEqual(digest(pin, salt), expected)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_DIGEST).apply()
    }

    private fun digest(pin: String, salt: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray(Charsets.UTF_8))
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        const val MIN_PIN_LENGTH = 4
        private const val SALT_BYTES = 16
        private const val KEY_SALT = "pin_salt"
        private const val KEY_DIGEST = "pin_digest"
    }
}
