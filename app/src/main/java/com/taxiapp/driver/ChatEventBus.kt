package com.taxiapp.driver

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ChatEventBus {
    @Volatile
    var isChatScreenOpen: Boolean = false

    private val _newMessages = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val newMessages = _newMessages.asSharedFlow()

    private val _cancelledOrders = MutableSharedFlow<String?>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cancelledOrders = _cancelledOrders.asSharedFlow()

    suspend fun triggerUpdate() {
        _newMessages.emit(Unit)
    }

    fun tryTriggerUpdate() {
        _newMessages.tryEmit(Unit)
    }

    fun triggerOrderCancelled(orderUuid: String?) {
        _cancelledOrders.tryEmit(orderUuid)
    }
}