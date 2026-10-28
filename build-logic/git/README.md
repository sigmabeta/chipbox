# build-logic/git

The `chipbox.build.identity` plugin: build branch and commit stamps (Android
`BuildConfig.BUILD_BRANCH`, the benchmark's `chipboxCommit`/`sageCommit`
instrumentation args) read from the working copy's `.git` files.

It lives in its own included build rather than in `:convention` so that applying
it does not drag paparazzi/metro/AGP onto the build script classpath of
`:apps:android` and `:benchmark` — both of which need nothing else from
chipbox's convention layer. It therefore declares **no dependencies**.

## Use

```kotlin
plugins {
    id("chipbox.build.identity")
}

buildConfigField("String", "BUILD_BRANCH", "\"${buildIdentity.branch()}\"")
```

- `buildIdentity.branch()` — current branch, or the short commit on a detached HEAD.
- `buildIdentity.shortCommit(dir = rootDir)` — abbreviated commit of that repository
  (pass `File(rootDir, "sage")` for a submodule).

Values come from `HEAD`, loose refs and `packed-refs`, following the `gitdir:` /
`commondir` indirection of linked worktrees and submodules. **No `git` process is
launched**, and a tree without `.git` (Docker build context, source tarball)
reports `unknown`. `-Pchipbox.build.branch` / `-Pchipbox.build.commit` (or
`CHIPBOX_BUILD_BRANCH` / `CHIPBOX_BUILD_COMMIT`) override the checkout identity.

Files are read with `providers.fileContents`, so they are configuration-cache
inputs: switching branches invalidates the entry without a process to compare
against.

## Tests

The `.git` resolution is split into a pure resolver (`GitMetadata.branch(repoDir,
readFile)` / `shortCommit(repoDir, readFile)`) and the thin Gradle-provider
wrapper the plugin calls. `src/test/kotlin/GitMetadataTest.kt` drives the resolver
over real temp directories: loose refs, `packed-refs`, detached HEAD, the
`gitdir:`/`commondir` linked-worktree shape, a malformed `.git`, and a missing
`.git`. It needs no Gradle test fixture, only kotlin-test/JUnit 4, so it stays out
of the build path:

```sh
./gradlew -p build-logic :git:test
```
