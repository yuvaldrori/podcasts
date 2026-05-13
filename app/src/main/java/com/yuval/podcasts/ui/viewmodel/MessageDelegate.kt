package com.yuval.podcasts.ui.viewmodel

import com.yuval.podcasts.ui.utils.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface MessageDelegate {
    val errorMessage: StateFlow<UiText?>
    fun showError(message: UiText)
    fun clearError()
}

class DefaultMessageDelegate @Inject constructor() : MessageDelegate {
    private val _errorMessage = MutableStateFlow<UiText?>(null)
    override val errorMessage: StateFlow<UiText?> = _errorMessage.asStateFlow()

    override fun showError(message: UiText) {
        _errorMessage.update { message }
    }

    override fun clearError() {
        _errorMessage.update { null }
    }
}
