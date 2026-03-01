package com.taxiapp.driver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ChatEventBus {
    // ВАЖНО: Вот эта переменная, на которую ругается компилятор
    var isChatScreenOpen = false

    private val _newMessages = MutableSharedFlow<Unit>()
    val newMessages = _newMessages.asSharedFlow()

    suspend fun triggerUpdate() {
        _newMessages.emit(Unit)
    }
}