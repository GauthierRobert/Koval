package com.koval.trainingplanner.ui.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.repository.AuthRepository
import com.koval.trainingplanner.data.repository.NotificationRepository
import com.koval.trainingplanner.domain.model.User
import com.koval.trainingplanner.domain.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val user: User? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkExistingSession()
    }

    private fun checkExistingSession() {
        viewModelScope.launch {
            try {
                if (authRepository.isLoggedIn()) {
                    val user = authRepository.fetchCurrentUser()
                    _authState.update { it.copy(user = user, isLoading = false) }
                    notificationRepository.registerFcmToken()
                } else {
                    _authState.update { it.copy(isLoading = false) }
                }
            } catch (_: Exception) {
                authRepository.logout()
                _authState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun devLogin(userId: String, role: UserRole) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = authRepository.devLogin(userId, userId, role)
                _authState.update { it.copy(user = user, isLoading = false) }
                notificationRepository.registerFcmToken()
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loginWithStrava(context: Context) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = authRepository.getStravaAuthUrl("koval://auth/callback")
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            try {
                val url = authRepository.getGoogleAuthUrl("mobile")
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun handleStravaCallback(code: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = authRepository.exchangeStravaCode(code)
                _authState.update { it.copy(user = user, isLoading = false) }
                notificationRepository.registerFcmToken()
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun handleGoogleCallback(token: String) {
        viewModelScope.launch {
            _authState.update { it.copy(isLoading = true, error = null) }
            try {
                val user = authRepository.handleGoogleToken(token)
                _authState.update { it.copy(user = user, isLoading = false) }
                notificationRepository.registerFcmToken()
            } catch (e: Exception) {
                _authState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            notificationRepository.unregisterFcmToken()
            authRepository.logout()
            _authState.update { AuthState(isLoading = false) }
        }
    }
}
