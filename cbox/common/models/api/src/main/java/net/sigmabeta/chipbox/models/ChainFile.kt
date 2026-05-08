package net.sigmabeta.chipbox.models

data class ChainFile(
    val filename: String,
    val uri: String,
)

fun encodeChainFiles(files: List<ChainFile>): String =
    files.joinToString("\n") { "${it.filename}\t${it.uri}" }

fun decodeChainFiles(s: String): List<ChainFile> {
    if (s.isEmpty()) return emptyList()
    return s.split("\n").mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab < 1) null
        else ChainFile(line.substring(0, tab), line.substring(tab + 1))
    }
}
