package com.example.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppResult
import com.example.feature.chat.domain.usecase.ConnectToChatUseCase
import com.example.feature.chat.domain.usecase.DisconnectFromChatUseCase
import com.example.feature.chat.domain.usecase.ObserveConnectionStateUseCase
import com.example.feature.chat.domain.usecase.ObserveMessagesUseCase
import com.example.feature.chat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val connectUseCase: ConnectToChatUseCase,
    private val disconnectUseCase: DisconnectFromChatUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val observeMessagesUseCase: ObserveMessagesUseCase,
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
        observeConnectionState()
    }

    private fun observeMessages() {
        observeMessagesUseCase()
            .onEach { message ->
                _uiState.update { it.copy(messages = it.messages + message) }
            }
            .launchIn(viewModelScope)
    }

    private fun observeConnectionState() {
        observeConnectionStateUseCase()
            .onEach { state ->
                _uiState.update { it.copy(connectionState = state, error = null) }
            }
            .launchIn(viewModelScope)
    }

    fun onServerUrlChange(url: String) = _uiState.update { it.copy(serverUrl = url) }

    fun onUsernameChange(name: String) = _uiState.update { it.copy(username = name) }

    fun onInputChange(text: String) = _uiState.update { it.copy(inputText = text) }

    fun connect() {
        val state = _uiState.value
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a username") }
            return
        }
        connectUseCase(state.serverUrl, state.username)
    }

    fun disconnect() = disconnectUseCase()

    fun sendMessage() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return
        _uiState.update { it.copy(inputText = "") }

        viewModelScope.launch {
            val result = sendMessageUseCase(text)
            if (result is AppResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        disconnectUseCase()
    }
}
