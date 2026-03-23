package com.autodroid.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.SecureRandom

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val ENCRYPTED_PREFS_NAME = "secure_boot_config"
    private const val LEGACY_PREFS_NAME = "boot_config"

    fun get(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val prefs = EncryptedSharedPreferences.create(
            ENCRYPTED_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateLegacy(context, prefs)
        return prefs
    }

    fun generateToken(): String {
        val bytes = ByteArray(16) // 128 bits
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun migrateLegacy(context: Context, encrypted: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return

        val editor = encrypted.edit()
        val legacyToken = legacy.getString("api_token", null)
        val legacyPassword = legacy.getString("unlock_password", null)

        if (legacyToken != null && encrypted.getString("api_token", null) == null) {
            editor.putString("api_token", legacyToken)
        }
        if (legacyPassword != null && encrypted.getString("unlock_password", null) == null) {
            editor.putString("unlock_password", legacyPassword)
        }
        editor.apply()

        legacy.edit().clear().apply()
        Log.i(TAG, "Migrated legacy prefs to encrypted storage")
    }
}
