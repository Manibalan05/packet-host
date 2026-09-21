package com.alan.app.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class ProcessLogger(private val maxLines: Int = 2000) {
    private val deque = ConcurrentLinkedDeque<String>()
    private val _flow = MutableStateFlow<List<String>>(emptyList())
    val flow: StateFlow<List<String>> = _flow

    fun append(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val entry = "[$ts] $line"
        deque.addLast(entry)
        while (deque.size > maxLines) deque.removeFirst()
        _flow.value = deque.toList()
    }

    fun clear() {
        deque.clear()
        _flow.value = emptyList()
    }

    fun snapshot(): List<String> = deque.toList()
    fun tail(n: Int): List<String> = deque.toList().takeLast(n)
}
