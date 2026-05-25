package net.sigmabeta.chipbox.scanner.real

internal actual fun availableProcessors(): Int = Runtime.getRuntime().availableProcessors()
