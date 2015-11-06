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

Chipbox is written using the Kotlin programming language, and uses the game-music-emu library for playback, Dagger for dependency injection, and RxJava for asynchronous operations. 

Although the IntelliJ Kotlin plugin is required to be able to modify the codebase, Gradle will get the necessary Kotlin dependencies at build-time independently of the plugin.

A pre-built native binary is included with this repo, for now; the native code is built using CMake, and the plan is to add support for this into the app's Gradle script soon.

## Roadmap

- Enable building native code from Gradle
- Finish main UI features (only a bare minimum is implemented at this time)
- Add support for more consoles (currently, only Sega Genesis and Super NES are supported)
- Standalone playback support for Android Wear (playback on a phone can be controlled from Wear)
- Bespoke UI for Android TV
- Android Auto control support