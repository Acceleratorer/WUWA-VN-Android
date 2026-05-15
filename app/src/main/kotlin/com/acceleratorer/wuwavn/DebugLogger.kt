package com.acceleratorer.wuwavn

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugLogger {
    fun interface Listener {
        fun onLogChanged(text: String)
    }

    private val lines = mutableListOf<String>()
    private var listener: Listener? = null

    @Synchronized
    fun setListener(listener: Listener?) {
        this.listener = listener
        notifyChanged()
    }

    @Synchronized
    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lines.add("[$time] $message")
        notifyChanged()
    }

    @Synchronized
    fun text(): String = lines.joinToString("\n")

    private fun notifyChanged() {
        listener?.onLogChanged(text())
    }
}
