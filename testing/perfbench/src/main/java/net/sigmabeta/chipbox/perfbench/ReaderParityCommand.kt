package net.sigmabeta.chipbox.perfbench

import java.io.File
import net.sigmabeta.chipbox.readers.Reader
import net.sigmabeta.chipbox.readers.Readers
import net.sigmabeta.sage.logging.BluntHatchet

/** How many offending rows to print before collapsing the rest into a count. */
internal const val PARITY_DETAIL_LIMIT = 20

/**
 * Runs the requested registered readers over the reference rows or corpus directory, compares each
 * length against the stored value (plus padding), prints a report, and returns it for tests.
 *
 * Formats without a registered reader (or whose reader rejects a file) show up as unparsed, which is
 * how a partly-ported Tier-0 reference set is expected to look.
 */
internal fun runReaderParity(args: ReaderParityArgs): ParitySummary {
    val readers = Readers(BluntHatchet())
    val cases = if (args.referenceFile != null) {
        referenceCases(args, readers)
    } else {
        directoryCases(args, readers)
    }
    val summary = summarizeParity(cases, args.paddingMs)
    printParity(summary, args)
    return summary
}

private fun referenceCases(args: ReaderParityArgs, readers: Readers): List<ParityCase> {
    val reference = requireNotNull(args.referenceFile)
    val probe = ParityProbe(readers)
    return parseReferenceTsv(reference.readText())
        .filter { args.readerExtension == null || it.extension == args.readerExtension }
        .filter { args.corpusDir == null || isInside(File(it.path), args.corpusDir) }
        .map { row ->
            val measured = probe.measure(row.path, row.extension, row.trackNumber)
            ParityCase(
                path = row.path,
                extension = row.extension,
                trackNumber = row.trackNumber,
                expectedLengthMs = row.storedLengthMs,
                readerLengthMs = measured.lengthMs,
                note = measured.note,
            )
        }
}

private fun directoryCases(args: ReaderParityArgs, readers: Readers): List<ParityCase> {
    val corpusDir = requireNotNull(args.corpusDir)
    val probe = ParityProbe(readers)
    // An explicit --reader includes that extension even without a registered reader (so a Tier-0
    // smoke run reports it unparsed); otherwise only extensions this build can actually read.
    return corpusFiles(corpusDir, args.readerExtension)
        .filter { args.readerExtension != null || readers.forExtension(it.extension.lowercase()) != null }
        .map { file ->
            val extension = file.extension.lowercase()
            val measured = probe.measure(file.path, extension, trackNumber = null)
            ParityCase(
                path = file.path,
                extension = extension,
                trackNumber = 1,
                expectedLengthMs = null,
                readerLengthMs = measured.lengthMs,
                note = measured.note,
            )
        }
}

/** Recursively lists files under [dir], optionally only [extension] (case-insensitive), sorted. */
internal fun corpusFiles(dir: File, extension: String?): List<File> = dir.walkTopDown()
    .filter { it.isFile }
    .filter { extension == null || it.extension.equals(extension, ignoreCase = true) }
    .sortedBy { it.path }
    .toList()

private fun isInside(file: File, dir: File): Boolean {
    val root = dir.absoluteFile.normalize().path + File.separator
    return file.absoluteFile.normalize().path.startsWith(root)
}

private data class MeasuredTrack(val lengthMs: Long?, val note: String?)

/** Runs one reader over one file and picks the track matching the reference row's number. */
private class ParityProbe(private val readers: Readers) {
    fun measure(path: String, extension: String, trackNumber: Int?): MeasuredTrack {
        val reader = readers.forExtension(extension)
        val file = File(path)
        return when {
            reader == null -> MeasuredTrack(null, "no reader registered for .$extension")
            !file.isFile -> MeasuredTrack(null, "file not found")
            else -> measureFile(reader, file, path, trackNumber)
        }
    }

    private fun measureFile(
        reader: Reader,
        file: File,
        path: String,
        trackNumber: Int?,
    ): MeasuredTrack {
        val bytes = try {
            file.readBytes()
        } catch (e: java.io.IOException) {
            return MeasuredTrack(null, "cannot read file: ${e.message}")
        }
        // Readers never throw by contract; a throw here is a bug worth surfacing with a stack trace.
        val tracks = reader.readTracksFromFile(bytes, path)
        return when {
            tracks == null -> MeasuredTrack(null, "reader returned null")

            tracks.isEmpty() -> MeasuredTrack(null, "reader yielded no tracks")

            trackNumber == null -> MeasuredTrack(tracks.first().length, null)

            else -> tracks.firstOrNull { it.trackNumber == trackNumber }
                ?.let { MeasuredTrack(it.length, null) }
                ?: MeasuredTrack(null, "reader yielded ${tracks.size} track(s), none #$trackNumber")
        }
    }
}

private fun printParity(summary: ParitySummary, args: ReaderParityArgs) {
    val source = args.referenceFile?.let { "reference=$it" } ?: "dir=${args.corpusDir}"
    val filter = args.readerExtension?.let { ", reader=$it" }.orEmpty()
    println("reader-parity: $source$filter, padding=${args.paddingMs}ms")

    val totals = summary.totals
    println("rows=${totals.rows} ${totals.format()}")
    for ((extension, counts) in summary.perExtension) {
        println("  $extension: ${counts.format()}")
    }

    summary.problems.take(PARITY_DETAIL_LIMIT).forEach {
        println("  ${it.result} ${it.extension} ${it.path}#${it.trackNumber} — ${it.detail}")
    }
    val hidden = summary.problems.size - PARITY_DETAIL_LIMIT
    if (hidden > 0) println("  ... and $hidden more")
}

private fun ParityCounts.format(): String {
    val base = "exact=$exact mismatch=$mismatch unparsed=$unparsed"
    return if (parsed > 0) "$base parsed=$parsed" else base
}
