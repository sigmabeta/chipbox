// okio's published JS bundle (`okio-parent-okio.js`) references Node's built-in `os`, `path`,
// and `fs` modules. Webpack 5 no longer auto-polyfills Node modules and we don't have a Node
// runtime in the browser, so these need explicit handling. `fs` and `path` aren't called at
// class-load (only inside NodeJsFileSystem methods we don't hit) so they can resolve to empty
// modules. `os` IS touched at class-load — `Path.SYSTEM_TEMPORARY_DIRECTORY` calls `os.tmpdir()`
// from a static initializer that runs as soon as FakeFileSystem's parent class is constructed —
// so it gets pointed at `os-browserify` instead (added via `implementation(npm(...))` in
// apps/js/build.gradle.kts).
config.resolve = config.resolve || {};
config.resolve.fallback = config.resolve.fallback || {};
config.resolve.fallback.path = false;
config.resolve.fallback.fs = false;
config.resolve.fallback.os = require.resolve('os-browserify/browser');
