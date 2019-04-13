# A Chiptune Jukebox for Android

Chipbox is a music player for Android. However, unlike most music players, it does not use MP3s, but rather the raw contents of old video game console sound chips' RAM. This enables near-exact replication of the original sound tracks from a file as small as 65KiB. 

It is designed to take advantage of the latest Android features, and requires a phone or tablet running Android 5.0+. Support for Android Wear, TV, and Auto are planned for the future. 

## Building
You can build the app by executing:

```
$ git clone git@github.com:sigmabeta/chipbox.git
$ cd chipbox
$ ./gradlew assemble<platform>Debug
```

Where `<platform>` is one of the following, depending on the type of device you plan to deploy to:

- `arm` for 32-bit ARM processors
- `arm_64` for 64-bit ARM processors
- `x86` for 32-bit x86 processors
- `x86_64` for 64-bit x86 processors

Chipbox is 100% written using Kotlin (aside from JNI integration), and uses the game-music-emu, VGMPlay, and SexyPSF libraries for playback, Dagger for dependency injection, and RxJava for asynchronous operations.

## Supported Music Formats
- SPC (Super NES)
- NSFE (NES)
- GBS (Game Boy)
- VGM (Genesis / Mega Drive, 32X, Arcade, numerous others)
- PSF (Sony Playstation)

## Roadmap

- Add support for more consoles
- Convert JNI code to Kotlin/Native, maybe
- Bespoke UI for Android TV
- Android Auto control support