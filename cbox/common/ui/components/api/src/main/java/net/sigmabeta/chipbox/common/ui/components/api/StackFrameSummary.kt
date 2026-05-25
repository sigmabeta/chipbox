package net.sigmabeta.chipbox.common.ui.components.api

internal actual fun Throwable.firstFrameSummary(): String {
    val frame = stackTrace.first()
    return "${frame.fileName}: ${frame.lineNumber} (${frame.methodName})"
}
