package com.koval.trainingplanner.data.repository

import com.koval.trainingplanner.data.local.TokenManager
import com.koval.trainingplanner.data.remote.api.AuthApi
import com.koval.trainingplanner.data.remote.dto.DevLoginRequest
import com.koval.trainingplanner.data.remote.dto.UserDto
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.domain.model.UserRole
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenManager: TokenManager,
) {

    suspend fun devLogin(userId: String, displayName: String, role: UserRole): User {
        val response = authApi.devLogin(
            DevLoginRequest(userId, displayName, role.name)
        )
        tokenManager.saveToken(response.token)
        return response.user?.toDomain() ?: fetchCurrentUser()
    }

    suspend fun getStravaAuthUrl(redirectUri: String): String {
        return authApi.getStravaAuthUrl(redirectUri).url
    }

    suspend fun exchangeStravaCode(code: String): User {
        val response = authApi.stravaCallback(code)
        tokenManager.saveToken(response.token)
        return response.user?.toDomain() ?: fetchCurrentUser()
    }

    suspend fun getGoogleAuthUrl(redirectUri: String): String {
        return authApi.getGoogleAuthUrl(redirectUri).url
    }

    suspend fun handleGoogleToken(token: String): User {
        tokenManager.saveToken(token)
        return fetchCurrentUser()
    }

    suspend fun fetchCurrentUser(): User {
        return authApi.getCurrentUser().toDomain()
    }

    fun isLoggedIn(): Boolean = tokenManager.getToken() != null

    fun logout() {
        tokenManager.removeToken()
    }

    private fun UserDto.toDomain() = User(
        id = id,
        displayName = displayName ?: "User",
        profilePicture = profilePicture,
        role = try { UserRole.valueOf(role ?: "ATHLETE") } catch (_: Exception) { UserRole.ATHLETE },
        ftp = ftp,
    )
}
