#include "Ssf.h"

const char *last_error;

int xsf_version;

sdsf_loader_state * sdsf_state;

// Loader callback handed to psflib; only this translation unit needs it.
static int sdsf_load(
        void *context,
        const uint8_t *exe, size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
);

void loadFile(const char *filename_c_str) {
    teardown();

    // Clear any error left by a previous (failed) load: neither this nor teardown() resets it,
    // so a stale string would otherwise make a following good load look failed. Matches the other
    // cores (PSF/USF/NCSF clear here, GME/2SF overwrite on outcome).
    last_error = nullptr;

    sdsf_loader_state image;
    memset(&image, 0, sizeof(image));

    // Owned by this function until each is either transferred into the live state or released
    // by the single cleanup path below. `image.data` is psflib's merged program image (up to
    // 8 MiB) and must outlive every sdsf_load() callback, so it is only freed once upload has
    // consumed it.
    sdsf_loader_state *sdsfinfo = nullptr;
    void *sega_state = nullptr;
    uint32_t start = 0;
    uint32_t length = 0;
    uint32_t max_length = 0;
    int upload_status = 0;

    xsf_version = psf_load(
            filename_c_str,
            &psf_file_system,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0
    );

    switch (xsf_version) {
        case 0x11:
        case 0x12:
            break;
        default:
            last_error = "Not a valid SSF file or DSF file.";
            goto cleanup;
    }

    if (sega_init()) {
        last_error = "Sega emulator static initialization failed";
        goto cleanup;
    }

    if (psf_load(
            filename_c_str,
            &psf_file_system,
            xsf_version,
            sdsf_load,
            &image,
            0,
            0,
            0,
            0,
            0
    ) != xsf_version) {
        last_error = "Failed to load PSF file.";
        goto cleanup;
    }

    if (image.data == nullptr || image.data_size < 4) {
        last_error = "Loaded file contained no program image.";
        goto cleanup;
    }

    sega_state = malloc(sega_get_state_size(xsf_version - 0x10));

    if (sega_state == nullptr) {
        last_error = "Failed to allocate Sega emulator state.";
        goto cleanup;
    }

    sega_clear_state(sega_state, xsf_version - 0x10);

    sega_enable_dsp_dynarec( sega_state, 0);

    start = *(uint32_t*) image.data;
    length = image.data_size;
    max_length = ( xsf_version == 0x12 ) ? 0x800000 : 0x80000;

    if (start + (length - 4) > max_length) {
        length = max_length - start + 4;
    }

    upload_status = sega_upload_program(sega_state, image.data, length);

    // sega_upload_program copies the program into emulator RAM and resets the CPU, so the
    // loader image is dead from here on regardless of the result.
    free(image.data);
    image.data = nullptr;
    image.data_size = 0;

    if (upload_status == -1) {
        last_error = "Failed to upload program.";
        goto cleanup;
    }

    sdsfinfo = static_cast<sdsf_loader_state *>(calloc(1, sizeof(sdsf_loader_state)));

    if (sdsfinfo == nullptr) {
        last_error = "Failed to allocate loader state.";
        goto cleanup;
    }

    sdsfinfo->emu = sega_state;
    sdsfinfo->version = xsf_version;
    sdsf_state = sdsfinfo;

    // Transferred to sdsf_state; keep the cleanup path from freeing them.
    sdsfinfo = nullptr;
    sega_state = nullptr;

cleanup:
    free(image.data);
    free(sega_state);
    free(sdsfinfo);
}

int32_t generateBuffer(int16_t *target_array, int32_t output_size_frames) {
    if (sdsf_state == nullptr || sdsf_state->emu == nullptr) {
        last_error = "No track loaded.";
        return 0;
    }

    if (target_array == nullptr || output_size_frames <= 0) {
        last_error = "Invalid output buffer.";
        return 0;
    }

    // Render straight into the caller's array: sega_execute never writes more than the
    // requested frame count (it caps *sound_samples and tracks the remaining budget).
    uint32_t frames_produced = static_cast<uint32_t>(output_size_frames);

    int32_t cycles_executed = sega_execute(
            sdsf_state->emu,
            0x7FFFFFFF,
            target_array,
            &frames_produced
    );

    if (cycles_executed < 0) {
        last_error = "Failed to generate any audio.";
        return 0;
    }

    return static_cast<int32_t>(frames_produced);
}

void teardown() {
    if (sdsf_state != nullptr) {
        free(sdsf_state->emu);
        free(sdsf_state);

        sdsf_state = nullptr;
    }
}

const char *get_last_error() {
    return last_error;
}

int32_t get_sample_rate() {
    return 44100;
}

static int sdsf_load(
        void *context,
        const uint8_t *exe,
        size_t exe_size,
        const uint8_t *reserved,
        size_t reserved_size
) {
    if (exe_size < 4) return -1;

    auto *state = (struct sdsf_loader_state *) context;

    uint8_t *dst = state->data;

    if (state->data_size < 4) {
        state->data = dst = (uint8_t *) malloc(exe_size);
        state->data_size = exe_size;
        memcpy(dst, exe, exe_size);
        return 0;
    }

    uint32_t dst_start = get_le32(dst);
    uint32_t src_start = get_le32(exe);

    dst_start &= 0x7fffff;
    src_start &= 0x7fffff;

    uint32_t dst_len = state->data_size - 4;
    uint32_t src_len = exe_size - 4;

    if (dst_len > 0x800000) dst_len = 0x800000;
    if (src_len > 0x800000) src_len = 0x800000;

    if (src_start < dst_start) {
        uint32_t diff = dst_start - src_start;
        state->data_size = dst_len + 4 + diff;
        state->data = dst = (uint8_t *) realloc(dst, state->data_size);
        memmove(dst + 4 + diff, dst + 4, dst_len);
        memset(dst + 4, 0, diff);
        dst_len += diff;
        dst_start = src_start;
        set_le32(dst, dst_start);
    }
    if ((src_start + src_len) > (dst_start + dst_len)) {
        uint32_t diff = (src_start + src_len) - (dst_start + dst_len);
        state->data_size = dst_len + 4 + diff;
        state->data = dst = (uint8_t *) realloc(dst, state->data_size);
        memset(dst + 4 + dst_len, 0, diff);
    }

    memcpy(dst + 4 + (src_start - dst_start), exe + 4, src_len);

    return 0;
}
