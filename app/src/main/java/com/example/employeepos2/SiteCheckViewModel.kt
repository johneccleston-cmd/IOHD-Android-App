package com.example.employeepos2.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.employeepos2.model.DoorState
import com.example.employeepos2.model.SiteCheckConfig
import com.example.employeepos2.repository.SiteCheckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SubmitState {
    object Idle : SubmitState()
    object Loading : SubmitState()
    object Success : SubmitState()
    data class Error(val message: String) : SubmitState()
}

class SiteCheckViewModel : ViewModel() {

    private val repository = SiteCheckRepository()

    // Holds the current state of our network request
    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState

    fun submit(context: Context, config: SiteCheckConfig, doors: List<DoorState>) {
        // Launching in viewModelScope guarantees the request survives Activity rotations
        viewModelScope.launch {
            _submitState.value = SubmitState.Loading

            val result = repository.submitSiteCheck(context, config, doors)

            result.fold(
                onSuccess = {
                    _submitState.value = SubmitState.Success
                },
                onFailure = { error ->
                    _submitState.value = SubmitState.Error(error.message ?: "Network error - check your connection")
                }
            )
        }
    }
}