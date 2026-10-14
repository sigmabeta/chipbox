# Library-snapshot ingest is a real app capability

The benchmark needs to seed a library without a scan. It could do so through a
benchmark-build-type-only hack, but that path would be invisible, impossible to
exercise by hand, and useless for debugging. Instead, snapshot ingest is a
**repository-level capability** (`importLibrarySnapshot`), exposed through a
debug-only Settings action and consumed by the benchmark's startup seed. The
trade-off is a small production surface — a JSON import entry point — in
exchange for a debuggable, reusable mechanism with one code path. Import is
upsert-merge and idempotent, with no file-existence check at import time
(playback resolves paths lazily); the benchmark is responsible for having the
audio present.
