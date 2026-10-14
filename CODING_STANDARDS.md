# Coding standards

Judgement calls the reviewer enforces that no linter can. Tooling-enforced style
lives in the linter configs; this file is for rules a machine can't check. The
review skill searches for this file automatically and applies it alongside its
built-in smell baseline.

## On-device behaviour is done when it has run on hardware

A device axis — or anything that only exercises on real hardware — is **done when
it has run on a real device**, not when the build is green. A green build, lint,
and unit tests are necessary but not sufficient; the benchmark or instrumentation
run is the check.

When a change touches device-only code, the reviewer asks whether it has been run
on hardware and reports it as unverified until it has. An enabled connected run on
an attached device counts; so does the ticket's `ready-for-human` device step.
