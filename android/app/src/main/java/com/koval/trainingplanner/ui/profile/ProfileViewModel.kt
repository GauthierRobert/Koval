package com.koval.trainingplanner.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.koval.trainingplanner.data.remote.api.ClubApi
import com.koval.trainingplanner.data.remote.dto.RedeemInviteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val clubApi: ClubApi,
) : ViewModel() {

    private val _joinState = MutableStateFlow(JoinState())
    val joinState: StateFlow<JoinState> = _joinState.asStateFlow()

    fun redeemInvite(code: String) {
        viewModelScope.launch {
            _joinState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            try {
                val response = clubApi.redeemInvite(RedeemInviteRequest(code.trim().uppercase()))
                val msg = response.message ?: when (response.type) {
                    "CLUB" -> "Joined club successfully"
                    "GROUP" -> "Joined training group"
                    else -> "Joined successfully"
                }
                _joinState.update { it.copy(isLoading = false, successMessage = msg) }
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("400") == true -> "Invalid invite code"
                    e.message?.contains("404") == true -> "Invite code not found"
                    e.message?.contains("409") == true -> "Already a member"
                    else -> "Failed to join. Check the code and try again."
                }
                _joinState.update { it.copy(isLoading = false, errorMessage = msg) }
            }
        }
    }

    fun clearJoinState() {
        _joinState.update { JoinState() }
    }
}
