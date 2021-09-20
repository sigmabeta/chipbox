#include "Ssf.h"
#include "common/common.h"
#include <byteswap.h>
#include <android/log.h>

#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, "SSF", __VA_ARGS__);

const char *last_error;

int16_t *output_buffer;
uint32_t output_buffer_size_samples;
uint32_t output_buffer_size_frames;

int xsf_version;

sdsf_loader_state * sdsf_state;

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

    sdsf_loader_state * sdsfinfo = (sdsf_loader_state*)malloc(sizeof(sdsf_loader_state));

    if (sega_init()) {
        last_error = "Sega emulator static initialization failed";
        return;
    }

    printf("Sega_init() success.");

    sdsf_loader_state state;

    memset( &state, 0, sizeof(state) );

    int ret = psf_load(
            filename_c_str,
            &psf_file_system,
            xsf_version,
            sdsf_load,
            &state,
            0,
            0,
            0,
            0,
            0
    );

    if (ret != xsf_version) {
        last_error = "Failed to load PSF file.";
        return;
    }

    printf("Psf_load success.");

    uint32_t sega_state_size = sega_get_state_size(xsf_version - 0x10);
    void * sega_state = static_cast<uint8_t *>(malloc(sega_state_size));

    printf("Sega_state size: 0x%04x bytes", sega_state_size);

    sega_clear_state(sega_state, xsf_version - 0x10);
    printf("Sega_clear_state success.");

    sega_enable_dry(sega_state, 1);
    printf("Sega_enable_dry success.");

    sega_enable_dsp(sega_state, 0);
    printf("Sega_enable_dsp success.");

    sega_enable_dsp_dynarec( sega_state, 1);
    printf("Sega_enable_dynarec success.");

    void *yam;

    if (xsf_version == 0x12) {
        void *dcsound = sega_get_dcsound_state(sega_state);
        yam = dcsound_get_yam_state(dcsound);
    } else {
        void *satsound = sega_get_satsound_state(sega_state);
        yam = satsound_get_yam_state(satsound);
    }
    if (yam) yam_prepare_dynacode(yam);


    uint32_t start = *(uint32_t*) state.data;
    uint32_t length = state.data_size;
    const uint32_t max_length = ( xsf_version == 0x12 ) ? 0x800000 : 0x80000;

    if (start + (length - 4) > max_length) {
        length = max_length - start + 4;
    }

    int upload_status = sega_upload_program(sega_state, state.data, length);

    if (upload_status == -1) {
        last_error = "Failed to upload program.";
        return;
    }

    sdsfinfo->emu = sega_state;
    sdsfinfo->yam = yam;
    sdsfinfo->version = xsf_version;

    sdsf_state = sdsfinfo;

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
            sdsf_state->emu,
            0x7FFFFFFF,
            output_buffer,
            &output_buffer_size_frames
    );

    if (cycles_executed < 0) {
        last_error = "Failed to generate any audio.";
        return 0;
    }

    memcpy(target_array, output_buffer, output_buffer_size_frames * 4);

    return output_buffer_size_frames;
}

void teardown() {
    if (sdsf_state != nullptr) {
        if (sdsf_state->yam != nullptr) {
            yam_unprepare_dynacode(sdsf_state->yam);
        }

        free(sdsf_state->yam);
        free(sdsf_state->emu);
        free(sdsf_state);

        sdsf_state = nullptr;
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
    if ( exe_size < 4 ) return -1;

    struct sdsf_loader_state * state = ( struct sdsf_loader_state * ) context;

    uint8_t * dst = state->data;

    if ( state->data_size < 4 ) {
        state->data = dst = ( uint8_t * ) malloc( exe_size );
        state->data_size = exe_size;
        memcpy( dst, exe, exe_size );
        return 0;
    }

    uint32_t dst_start = get_le32( dst );
    uint32_t src_start = get_le32( exe );

    dst_start &= 0x7fffff;
    src_start &= 0x7fffff;

    uint32_t dst_len = state->data_size - 4;
    uint32_t src_len = exe_size - 4;

    if ( dst_len > 0x800000 ) dst_len = 0x800000;
    if ( src_len > 0x800000 ) src_len = 0x800000;

    if ( src_start < dst_start )
    {
        uint32_t diff = dst_start - src_start;
        state->data_size = dst_len + 4 + diff;
        state->data = dst = ( uint8_t * ) realloc( dst, state->data_size );
        memmove( dst + 4 + diff, dst + 4, dst_len );
        memset( dst + 4, 0, diff );
        dst_len += diff;
        dst_start = src_start;
        set_le32( dst, dst_start );
    }
    if ( ( src_start + src_len ) > ( dst_start + dst_len ) )
    {
        uint32_t diff = ( src_start + src_len ) - ( dst_start + dst_len );
        state->data_size = dst_len + 4 + diff;
        state->data = dst = ( uint8_t * ) realloc( dst, state->data_size );
        memset( dst + 4 + dst_len, 0, diff );
    }

    memcpy( dst + 4 + ( src_start - dst_start ), exe + 4, src_len );

    return 0;
}
