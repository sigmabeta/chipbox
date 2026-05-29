// WASM-target wrapper for vgmstream. Same path-based loadFile pattern as the PSF-family
// emulators, plus a subsong parameter (vgmstream containers like FSB/AWB pack many subsongs
// per file). vgmstream's `libstreamfile_open_from_stdio` calls plain `fopen`, which resolves
// against Emscripten MEMFS once the Kotlin layer has mirrored the staged track dir into it.
//
// Also exposes the runtime supported-extension list as a single comma-separated string so the
// JS-side `WasmVgmstreamEmulator` can derive `supportedFileExtensions` from vgmstream itself
// (matching the JVM `VgmstreamEmulator` which queries the JNI for the same list) instead of
// hand-maintaining a list that would drift from the C library.

#include <emscripten.h>
#include <cstring>
#include <string>
#include "chipbox_vgmstream.h"

extern "C" {

EMSCRIPTEN_KEEPALIVE
int chipbox_vgmstream_load_file(const char* path, int subsong) {
    loadFile(path, subsong);
    const char* err = get_last_error();
    return err ? 1 : 0;
}

EMSCRIPTEN_KEEPALIVE
int chipbox_vgmstream_play(int16_t* target, int frames) {
    return generateBuffer(target, frames);
}

EMSCRIPTEN_KEEPALIVE
int chipbox_vgmstream_sample_rate() { return get_sample_rate(); }

EMSCRIPTEN_KEEPALIVE
const char* chipbox_vgmstream_last_error() {
    const char* err = get_last_error();
    return err ? err : "";
}

EMSCRIPTEN_KEEPALIVE
void chipbox_vgmstream_teardown() { teardown(); }

// vgmstream's supported extensions minus the "common" ones (wav/ogg/mp3/...). Returned as a
// single comma-separated string so the JS binding can split on `,` rather than marshalling an
// array out of WASM. The result is built once into a static std::string — same lifetime as the
// module, safe to return its `.c_str()` to the caller.
EMSCRIPTEN_KEEPALIVE
const char* chipbox_vgmstream_supported_extensions() {
    static std::string cached;
    if (!cached.empty()) return cached.c_str();

    int all_count = 0;
    const char *const *all = get_supported_extensions(&all_count);
    int common_count = 0;
    const char *const *common = get_common_extensions(&common_count);

    for (int i = 0; i < all_count; ++i) {
        bool is_common = false;
        for (int j = 0; j < common_count; ++j) {
            if (strcmp(all[i], common[j]) == 0) { is_common = true; break; }
        }
        if (is_common) continue;
        if (!cached.empty()) cached.push_back(',');
        cached.append(all[i]);
    }
    return cached.c_str();
}

}  // extern "C"
