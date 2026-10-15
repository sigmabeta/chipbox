# Chipbox

Chipbox is a chiptune jukebox that plays raw sound-chip program/data dumps by
emulating the original hardware in real time. This is the shared language for
talking about its performance measurement.

## Language

**Corpus**:
The user-supplied set of audio files, rooted at a single directory, that a
performance benchmark renders or loads as its fixed input.
_Avoid_: library, test data.

**Library snapshot**:
A serialized record of scanned games and tracks that can be ingested into the
app's library without rescanning. Used as the injectable database for device
benchmarks.
_Avoid_: fixture, seed data, DB dump.

**Scenario**:
One named, reproducible unit of measurement with a fixed input and a defined
metric — for the audio axis, a single corpus file rendered through the raw
emulator core; for the UI axis, one screen interaction.
_Avoid_: test, case, workload.

**Time to first content**:
The wall-clock time from a launch intent until the library grid is showing its
first items. The startup scenarios (cold and warm) measure it; it is the point at
which the app reports itself fully drawn, not the first frame.
_Avoid_: startup time, launch time, TTID.

**Real-time factor (RTF)**:
The ratio of a track's audio duration to the wall-clock time taken to render
it; a value above one means faster than real time. The benchmark's
machine-independent audio metric.
_Avoid_: speed, throughput.

**Fingerprint**:
The machine facts (host or device) recorded alongside a result so that two
results are known to be comparable. A fingerprint class groups mutually
comparable results; comparisons across classes are refused.
_Avoid_: profile, spec, environment.

**Sustained load**:
A fixed-duration run of playback and scrolling used to measure memory and
thermal behaviour over time.
_Avoid_: soak test, stress test.
