---
hide:
  - navigation
  - toc
---

<h1 class="chipbox-sr-only">Chipbox</h1>

<div class="chipbox-hero">
  <p class="chipbox-hero__text">A music player that plays raw program dumps of old console sound chips, emulating them in real time. Authentic playback from files a few dozen KiB in size.</p>
  <figure class="chipbox-demo">
    <video autoplay muted loop playsinline poster="assets/chipbox-demo-poster.jpg">
      <source src="assets/chipbox-demo.mp4" type="video/mp4">
      Your browser doesn’t support HTML video.
    </video>
  </figure>
</div>

---

## Emulated Audio

Every track is the actual sound-chip program, run through an emulator core on your
device.

<div class="grid cards" markdown>

-   :material-cellphone-link:{ .lg .middle } &nbsp; __Android &amp; desktop__

    ---

    One Kotlin Multiplatform codebase with a shared Compose UI runs on Android
    8.0+ and as a native desktop (JVM) application.

-   :material-feather:{ .lg .middle } &nbsp; __Tiny files, full soundtracks__

    ---

    A whole game&rsquo;s music can live in a handful of kilobytes.

-   :material-music-box-multiple:{ .lg .middle } &nbsp; __A proper library__

    ---

    Browse by game and artist, search, favorites and playlists, with rich
    metadata pulled straight from the dumps.

</div>

## Supported formats

Dozens of single-file and archive formats across two decades of console and
arcade hardware.

| Format | System |
| --- | --- |
| **SPC** | Super Nintendo |
| **NSF / NSFE** | Nintendo (NES) |
| **VGM / VGZ** | Genesis · 32X · Arcade |
| **GBS** | Game Boy |
| **GSF** | Game Boy Advance |
| **PSF / miniPSF** | PlayStation |
| **USF / miniUSF** | Nintendo 64 |
| **SSF** | Sega Saturn |
| **2SF** | Nintendo DS |
| **NCSF / miniNCSF** | Nintendo DS (Nitro Composer) |
| **AY · HES · KSS** | ZX Spectrum · PC Engine · MSX |
| **vgmstream** | ADX · HCA · DSP · STRM and many more |

---

<p style="text-align: center; color: var(--md-default-fg-color--light);">
Chipbox is an open-source chiptune jukebox by
<a href="https://github.com/sigmabeta">sigmabeta</a>.
</p>
