package net.sigmabeta.chipbox.scanner.real

/**
 * CPU count used to size scan parallelism. JVM/Android read [java.lang.Runtime]; the
 * enforcement-only JS target (which never runs a scan) returns 1.
 */
internal expect fun availableProcessors(): Int
