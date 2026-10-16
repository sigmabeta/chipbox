package net.sigmabeta.chipbox.perfbench

/** The base name of the whole-run result a host invocation writes, e.g. `results.json`. */
const val RESULTS_FILE_NAME = "results.json"

private const val AXIS_RESULT_PREFIX = "results-"

/**
 * The artifact file name one measured [axis] is written under, e.g. `results-ui.json`.
 *
 * A device benchmark run holds one `@Test` per axis, and every test in the run writes into the same
 * additional-test-output directory — so each axis must write its own file rather than all sharing
 * [RESULTS_FILE_NAME], or the last test to run would silently overwrite the others. [axis] is the
 * key the axis carries under `PerfResult.axes` (`"ui"`, `"startup"`), so a file's name names the
 * axis inside it.
 */
fun axisResultFileName(axis: String): String = "$AXIS_RESULT_PREFIX$axis.json"
