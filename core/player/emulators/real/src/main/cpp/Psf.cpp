#include "Psf.h"
#include "aopsf/psf2fs.h"

uint8_t *pEmu;

bool isPs2Track;

const char *last_error;

void *psf2fs;

void loadFile(const char *filename_c_str) {
//    if (started)
//    {
//        if (psf_version == 2) psf2_stop((PSX_STATE *)psx_state.get_ptr());
//        else psf_stop((PSX_STATE *)psx_state.get_ptr());
//    }

    char psf_version = psf_load(filename_c_str, &psf_file_system, 0, 0, 0, 0, 0, 0, 0, 0);

    if (psf_version < 0) last_error = "Not a PSF file";
    if (psf_version != 1 && psf_version != 2) last_error = "Not a PSF1 or PSF2 file";
    uint64_t psx_state_size = psx_get_state_size(psf_version);
    uint8_t *psx_state = static_cast<uint8_t *>(malloc(psx_state_size));

    memset(psx_state, 0, psx_state_size);

    pEmu = psx_state;

    if (psf_version == 1) {
        psf1_load_state state{};

        state.emu = pEmu;
        state.first = true;
        state.refresh = 0;

        int ret = psf_load(
                filename_c_str,
                &psf_file_system,
                1,
                psf1_load,
                &state,
                psf1_info,
                &state,
                1,
                0,
                0
        );

        if (ret < 0) last_error = "Invalid PSF1 file";

        if (state.refresh) {
            psx_set_refresh((PSX_STATE *) pEmu, state.refresh);
        }

        isPs2Track = false;
        psf_start((PSX_STATE *) pEmu);
    } else if (psf_version == 2) {
        if (psf2fs) psf2fs_delete(psf2fs);

        psf2fs = psf2fs_create();
        if (!psf2fs) last_error = "Failed to allocate PS2 FS.";

        psf1_load_state state;

        state.refresh = 0;

        int ret = psf_load(
                filename_c_str,
                &psf_file_system,
                2,
                psf2fs_load_callback,
                psf2fs,
                psf1_info,
                &state,
                1,
                0,
                0);

        if (ret < 0) last_error = "Invalid PSF2 file";

        if (state.refresh)
            psx_set_refresh((PSX_STATE *) pEmu, state.refresh);

        isPs2Track = true;

        psf2_register_readfile((PSX_STATE *) pEmu, psf2fs_virtual_readfile, psf2fs);
        psf2_start((PSX_STATE *) pEmu);
    }
}

int32_t generateBuffer(int16_t *target_array, int32_t buffer_size_shorts) {

    int32_t written = 0;

    int32_t samples = buffer_size_shorts;

    if (isPs2Track)
        written = psf2_gen((PSX_STATE *) pEmu, target_array, samples);
    else
        written = psf_gen((PSX_STATE *) pEmu, target_array, samples);

//    const char *errmsg = psx_get_last_error((PSX_STATE *) psx_state.get_ptr());
//    if (errmsg) console::print(errmsg);
//    if (err != AO_SUCCESS) console::print("Execution halted with an error.");
//    if (!written) throw exception_io_data();
//    if (err != AO_SUCCESS) eof = true;

    return written;
}

void teardown() {
    if (isPs2Track) {
        psf2_stop((PSX_STATE *) pEmu);
        if (psf2fs) psf2fs_delete(psf2fs);
        psf2fs = nullptr;
    } else {
        psf_stop((PSX_STATE *) pEmu);
    }

    delete pEmu;
}

const char *get_last_error() {
    return last_error;
}

static void *psf_file_fopen(void *context, const char *uri) {
    try {
        return fopen(uri, "r");
    }
    catch (...) {
        return NULL;
    }
}

static size_t psf_file_fread(void *buffer, size_t size, size_t count, void *handle) {
    try {
        auto file = (FILE *) handle;
        return fread(buffer, size, count, file);
    }
    catch (...) {
        return 0;
    }
}

static int psf_file_fseek(void *handle, int64_t offset, int whence) {
    try {
        auto file = (FILE *) handle;
        return fseek(file, offset, whence);
    }
    catch (...) {
        return -1;
    }
}

static int psf_file_fclose(void *handle) {
    try {
        auto file = (FILE *) handle;
        fclose(file);
        return 0;
    }
    catch (...) {
        return -1;
    }
}

static long psf_file_ftell(void *handle) {
    try {
        auto file = (FILE *) handle;
        return ftell(file);
    }
    catch (...) {
        return -1;
    }
}

static int psf1_info(void *context, const char *name, const char *value) {
    psf1_load_state *state = (psf1_load_state *) context;

    if (!state->refresh && !strcasecmp(name, "_refresh")) {
        state->refresh = atoi(value);
    }

    return 0;
}

int psf1_load(void *context, const uint8_t *exe, size_t exe_size,
              const uint8_t *reserved, size_t reserved_size) {
    psf1_load_state *state = (psf1_load_state *) context;

    if (reserved && reserved_size)
        return -1;

    if (psf_load_section((PSX_STATE *) state->emu, exe, exe_size, state->first))
        return -1;

    state->first = false;

    return 0;
}
