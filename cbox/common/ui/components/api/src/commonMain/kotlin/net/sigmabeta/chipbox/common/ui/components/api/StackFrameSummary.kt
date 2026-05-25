package net.sigmabeta.chipbox.common.ui.components.api

/**
 * One-line "file: line (method)" summary of this error's top stack frame, shown only in the
 * debug / inspection error overlay (see [EmptyListIndicator]). JVM/Android read the JVM
 * StackTraceElement directly; the enforcement-only JS target falls back to the first stack line.
 */
internal expect fun Throwable.firstFrameSummary(): String
