package com.koval.trainingplanner.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenManager(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "koval_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

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

    companion object {
        private const val KEY_JWT = "koval_jwt"
        private const val KEY_FCM = "koval_fcm_token"
    }
}
