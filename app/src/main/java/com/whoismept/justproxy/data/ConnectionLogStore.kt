package com.whoismept.justproxy.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionLogStore {
    private const val MAX_ENTRIES = 500

    private val _logs = MutableStateFlow<List<ConnectionLog>>(emptyList())
    val logs: StateFlow<List<ConnectionLog>> = _logs.asStateFlow()

    fun add(entry: ConnectionLog) {
        _logs.value = (listOf(entry) + _logs.value).take(MAX_ENTRIES)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
