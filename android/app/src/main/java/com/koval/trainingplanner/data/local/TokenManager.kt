package com.koval.trainingplanner.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.File

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    private companion object {
        const val PREFS_NAME = "koval_secure_prefs"
        const val KEY_JWT = "koval_jwt"
        const val KEY_FCM = "koval_fcm_token"
        const val TAG = "TokenManager"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                buildEncryptedPrefs(context)
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences corrupted, resetting", e)
                // Delete the corrupted prefs file and retry
                File(context.filesDir.parent, "shared_prefs/$PREFS_NAME.xml").delete()
                buildEncryptedPrefs(context)
            }
        }

        private fun buildEncryptedPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                PREFS_NAME,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }

    fun getToken(): String? = prefs.getString(KEY_JWT, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_JWT, token).apply()
    }

    fun removeToken() {
        prefs.edit().remove(KEY_JWT).apply()
    }

    fun getFcmToken(): String? = prefs.getString(KEY_FCM, null)

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM, token).apply()
    }
}
