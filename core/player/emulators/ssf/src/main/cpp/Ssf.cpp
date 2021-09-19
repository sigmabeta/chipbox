#include "Ssf.h"
#include "common/common.h"
#include <byteswap.h>
#include <android/log.h>

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "SSF", __VA_ARGS__);

const char *last_error;

uint8_t *sega_state;
uint16_t sega_state_size;

int16_t *output_buffer;
uint32_t output_buffer_size_samples;
uint32_t output_buffer_size_frames;

int xsf_version;

void loadFile(const char *filename_c_str) {
    teardown();
    
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
            return;
    }

    printf("SSF version: %02x", xsf_version);

    if (sega_init()) {
        last_error = "Sega emulator static initialization failed";
        return;
    }

    printf("Sega_init() success.");

    sega_state_size = sega_get_state_size(xsf_version - 0x10);
    sega_state = static_cast<uint8_t *>(malloc(sega_state_size));

    memset(sega_state, 0, sega_state_size);

    printf("Sega_state size: 0x%04x bytes", sega_state_size);

    sega_clear_state(sega_state, xsf_version - 0x10);
    printf("Sega_clear_state success.");

    sega_enable_dry(sega_state, 0);
    printf("Sega_enable_dry success.");

    sega_enable_dsp(sega_state, 1);
    printf("Sega_enable_dsp success.");

    sega_enable_dsp_dynarec( sega_state, false );
    printf("Sega_enable_dynarec success.");

    sdsf_load_state load_context;

    int ret = psf_load(
            filename_c_str,
            &psf_file_system,
            xsf_version,
            sdsf_load,
            &load_context,
            0,
            0,
            0,
            0,
            0
    );

    if (ret != xsf_version) {
        last_error = "Sega emulator static initialization failed";
        return;
    }

    printf("Psf_load success.");

    uint32_t start = bswap_32(*load_context.state);
    uint32_t length = load_context.state_size;
    uint32_t max_length = (xsf_version == 0x12) ? 0x800000 : 0x80000;

    if (start + (length - 4) > max_length) {
        length = max_length - start + 4;
    }

    bool upload_success = sega_upload_program(sega_state, load_context.state, length);

    if (!upload_success) {
        last_error = "Failed to upload program.";
        return;
    }

    printf("Sega_upload_program success.");
}

int32_t generateBuffer(int16_t *target_array, int32_t output_size_frames) {
    if (output_buffer_size_samples != output_size_frames * 2) {
        delete output_buffer;

        output_buffer_size_frames = output_size_frames;
        output_buffer_size_samples = output_size_frames * 2;
        output_buffer = static_cast<int16_t *>(malloc(output_size_frames * 4));
    }

    int32_t cycles_executed = sega_execute(
            sega_state,
            0x7FFFFFFF,
            output_buffer,
            &output_buffer_size_frames
    );

    printf("Sega_execute success.");

    if (cycles_executed < 0) {
        last_error = "Failed to generate any audio.";
        return 0;
    }

    memcpy(target_array, output_buffer, output_buffer_size_frames * 4);

    return output_buffer_size_frames;
}

void teardown() {
    if (sega_state != nullptr) {
        sega_clear_state(sega_state, xsf_version - 0x10);
        delete sega_state;

        sega_state = nullptr;
        sega_state_size = 0;
    }

    if (output_buffer != nullptr) {
        delete output_buffer;

        output_buffer = nullptr;
        output_buffer_size_samples = 0;
        output_buffer_size_frames = 0;
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

    auto *load_context = (sdsf_load_state *) context;

    uint8_t *dst = load_context->state;

    if (load_context->state_size < 4) {
        load_context->state_size = exe_size;
        load_context->state = static_cast<uint8_t *>(malloc(exe_size));

        memset(load_context->state, 0, load_context->state_size);
        memcpy(load_context->state, exe, exe_size);
        return 0;
    }

    uint32_t dst_start = bswap_32(*dst);
    uint32_t src_start = bswap_32(*exe);

    dst_start &= 0x7FFFFF;
    src_start &= 0x7FFFFF;

    uint32_t dst_len = load_context->state_size - 4;
    uint32_t src_len = exe_size - 4;

    if (dst_len > 0x800000) dst_len = 0x800000;
    if (src_len > 0x800000) src_len = 0x800000;

    if (src_start < dst_start) {
        uint32_t diff = dst_start - src_start;
        load_context->state_size = dst_len + 4 + diff;
        load_context->state = static_cast<uint8_t *>(malloc(exe_size));
        memmove(dst + 4 + diff, dst + 4, dst_len);
        memset(dst + 4, 0, diff);
        dst_len += diff;
        dst_start = src_start;
        *dst = bswap_32(dst_start);
    }

    if ((src_start + src_len) > (dst_start + dst_len)) {
        uint32_t diff = (src_start + src_len) - (dst_start + dst_len);
        load_context->state_size = dst_len + 4 + diff;
        memset(load_context + 4 + dst_len, 0, diff);
    }

    memcpy(dst + 4 + (src_start - dst_start), exe + 4, src_len);

    return 0;
}
