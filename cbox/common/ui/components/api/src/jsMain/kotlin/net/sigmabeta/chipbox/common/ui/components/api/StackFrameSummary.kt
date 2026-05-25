package net.sigmabeta.chipbox.common.ui.components.api

// JS has no JVM StackTraceElement; fall back to the first non-empty line of the rendered trace.
// (Debug/inspection-only overlay — never exercised at runtime on this enforcement target.)
internal actual fun Throwable.firstFrameSummary(): String =
    stackTraceToString().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
