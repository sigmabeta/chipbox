package net.sigmabeta.chipbox.common.ui.components.api.subs

import androidx.compose.runtime.Composable
import net.sigmabeta.chipbox.perf.traceBeginSection
import net.sigmabeta.chipbox.perf.traceEndSection

/**
 * Runs [content] inside a Perfetto / systrace section named [label], so the labelled composable work
 * is visible to a `TraceSectionMetric` in the device macrobenchmark. Nesting is allowed (a section's
 * time includes any sections opened inside it).
 *
 * A plain begin/end pair rather than a `try`/`finally`: the Compose compiler rejects a try block
 * around composable invocations, and composition does not throw in practice, so the exception-safety
 * is not worth the restriction.
 *
 * Off Android the underlying trace functions are no-ops, so this costs nothing on desktop and JS.
 * A composable that Compose skips never enters its body, so it emits no section — the metric sums
 * only the frames that actually composed.
 */
@Composable
fun TraceSection(label: String, content: @Composable () -> Unit) {
    traceBeginSection(label)
    content()
    traceEndSection()
}
