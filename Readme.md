# A Chiptune Jukebox for Android

Chipbox is a music player for Android. However, unlike most music players, it does not use MP3s, but rather the raw contents of old video game console sound chips' RAM. This enables near-exact replication of the original sound tracks from a file as small as 65KiB. 

It is designed to take advantage of the latest Android features, and requires a phone or tablet running Android 5.0+. Support for Android Wear, TV, and Auto are planned for the future. 

## Building
You can build the app by executing:

```
$ git clone git@github.com:sigmabeta/chipbox.git
$ cd chipbox
$ ./gradlew assembleDebug
```

Chipbox is 100% written using Kotlin, and uses the game-music-emu and VGMPlay libraries for playback, Dagger for dependency injection, and RxJava for asynchronous operations.

## Supported Music Formats
- SPC (Super NES)
- NSFE (NES)
- GBS (Game Boy)
- VGM (Genesis / Mega Drive, 32X, Arcade, numerous others)

## Roadmap

- Add support for more consoles
- Standalone playback support for Android Wear (playback on a phone can be controlled from Wear)
- Bespoke UI for Android TV
- Android Auto control support