# Coding standards

Judgement calls the reviewer enforces that no linter can. Tooling-enforced style
lives in the linter configs; this file is for rules a machine can't check. The
review skill searches for this file automatically and applies it alongside its
built-in smell baseline.

## Hardware and release gates are done when they have run

A device axis, browser audio/UI, WASM in a real browser, a container image build,
or a multi-arch runtime is **done when it has run on the target**, not when the
build is green. A green build, lint, and unit tests are necessary but not
sufficient.

When a change touches such a gate, the reviewer asks whether it has run and
reports it as unverified until it has. A ticket with an unrecorded hardware or
release gate stays `ready-for-human` (or carries an explicit `Manual gates:` line),
even after its code is merged. The ticket's `## Answer` records what was run and
what a human still has to run.

## User-facing strings go through the string resources

Every user-facing string is a `ChipboxStringId` backed by a `strings-*.xml`
resource — screens, snackbars, errors, empty states. Don't hardcode English in a
composable, event, or ViewModel; add the resource and resolve it through
`StringProvider`. A message that embeds a path or an exception keeps the English
in the resource with a `%1$s` placeholder. Log messages and diagnostic text are
exempt.

Judgement decides what is user-facing: anything a person reads on screen (or
hears through a screen reader) is user-facing, even in a debug-only tool.

The mechanical part is `scripts/ci/check-user-facing-strings.sh`, run by CI's
`static-analysis` job and by `scripts/ci/verify.sh static-analysis`. It fails on a
literal first argument to `ChipboxEvent.ShowSnackbar(...)` in a main source set
and reports `file:line`; the script header lists its exclusions (tests,
screenshots/previews, logs). Other user-facing shapes (`Notif(description = …)`,
error strings, empty states) are not checked mechanically yet — the rule still
applies to them.

## A regression test must be red on the bug

When fixing a bug, revert the production fix locally and confirm the new test
fails. A test that passes with and without the fix guards nothing. This applies
to boundary/ordering tests too: check that the fixture actually exercises the
broken case rather than passing by accident.

## Module READMEs track the public surface

Every Gradle module has a `README.md`. Update it when the module's public API,
dependencies, build plugins, or role change, and add one when adding a module.
The README is the orientation surface, not a changelog.
