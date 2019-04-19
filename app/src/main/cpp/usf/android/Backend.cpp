#include <stdio.h>
#include "net_sigmabeta_chipbox_backend_usf_BackendImpl.h"
#include <fstream>
#include <android/log.h>
#include <sstream>

#include "../../psflib/psflib.h"
#include "../../libsamplerate/src/samplerate.h"
#include "../../usf/usf/usf.h"

#define CHIPBOX_TAG "BackendUSF"

#define FLOATS_PER_SAMPLE 2
#define SHORTS_PER_SAMPLE 2
#define BYTES_PER_SAMPLE 4
#define FADE_TIME 7000

bool g_initialized;
bool g_stop_seek;

const char *g_filename_c_str;
const char *g_last_error;

unsigned int g_sample_rate_device;
unsigned int g_sample_rate_track;

uint64_t g_buffer_length_bytes_device;
uint64_t g_buffer_length_shorts_device;
uint64_t g_buffer_length_floats_device;
uint64_t g_buffer_length_bytes_track;
uint64_t g_buffer_length_shorts_track;
uint64_t g_buffer_length_floats_track;

uint64_t g_buffer_as_float_device_in_bytes;
uint64_t g_buffer_as_float_track_in_bytes;

uint64_t g_played_sample_count_track;
uint64_t g_track_length_ms;
uint64_t g_fade_start_threshold_samples_track;
uint64_t g_fade_length_samples_track;

uint64_t g_buffer_length_samples_device;
uint64_t g_buffer_length_ms_device;
uint64_t g_buffer_length_samples_track;
uint64_t g_buffer_length_ms_track;

float *g_buffer_as_float_track;
float *g_buffer_as_float_device;

short *g_buffer_as_short_track;

SRC_DATA g_src_config;

uint8_t *g_emu_state;

int g_enable_compare;

int g_enable_fifo_full;

/**
 * Callbacks
 */

static void *stdio_fopen(const char *path) {
    return fopen(path, "rb");
}

static size_t stdio_fread(void *p, size_t size, size_t count, void *f) {
    return fread(p, size, count, (FILE *) f);
}

static int stdio_fseek(void *f, int64_t offset, int whence) {
    return fseek((FILE *) f, offset, whence);
}

static int stdio_fclose(void *f) {
    return fclose((FILE *) f);
}

static long stdio_ftell(void *f) {
    return ftell((FILE *) f);
}

static psf_file_callbacks stdio_callbacks =
        {
                "\\/:",
                stdio_fopen,
                stdio_fread,
                stdio_fseek,
                stdio_fclose,
                stdio_ftell
        };

static int usf_loader(void *context, const uint8_t *exe, size_t exe_size,
                      const uint8_t *reserved, size_t reserved_size) {
    if (exe && exe_size > 0) return -1;

    return usf_upload_section(g_emu_state, reserved, reserved_size);
}

static int usf_info(void *context, const char *name, const char *value) {
    if (!strcasecmp(name, "_enablecompare") && *value)
        g_enable_compare = 1;
    else if (!strcasecmp(name, "_enablefifofull") && *value)
        g_enable_fifo_full = 1;

    return 0;
}

static void print_message(void *unused, const char *message) {
    __android_log_print(ANDROID_LOG_WARN, CHIPBOX_TAG, "%s", message);
}

/**
 * Fifo Buffer Management
 */
struct fifo_buffer {
    uint64_t write_pos;
    uint64_t read_pos;
    uint64_t size;
    uint8_t *buffer; // the size must be power of 2
};

struct fifo_buffer g_fifo_from_lib;


uint64_t fifo_free_space(const fifo_buffer *fifo);

int64_t generateAudio(uint64_t bytes_requested);

void fifo_init(struct fifo_buffer *buf, uint64_t length) {
    buf->write_pos = 0;
    buf->read_pos = 0;
    buf->size = length;
    buf->buffer = static_cast<uint8_t *>(malloc(length));
}

/**
 * Returns the smaller of two values.
 */
inline static uint64_t min(uint64_t a, uint64_t b) {
    return a <= b ? a : b;
}

uint64_t fifo_free_space(const fifo_buffer *fifo) {
    return fifo->size - fifo->write_pos + fifo->read_pos;
}

uint64_t fifo_put(struct fifo_buffer *fifo, const uint8_t *input_buffer, uint64_t input_length) {
    uint64_t free_space_in_buffer = fifo_free_space(fifo);
    uint64_t bytes_to_actually_copy = min(input_length, free_space_in_buffer);

    uint64_t actual_write_pos = fifo->write_pos % fifo->size;
    uint64_t bytes_before_end = fifo->size - actual_write_pos;
    uint64_t bytes_to_copy_before_end = min(bytes_to_actually_copy, bytes_before_end);

    // Fill in available data from the write position, until the buffer ends. (It might not!)
    memcpy(fifo->buffer + actual_write_pos,
           input_buffer,
           bytes_to_copy_before_end);

    // Any remaining data should fill in from the beginning of the buffer.
    memcpy(fifo->buffer,
           input_buffer + bytes_to_copy_before_end,
           bytes_to_actually_copy - bytes_to_copy_before_end);

    fifo->write_pos += bytes_to_actually_copy;
//    __android_log_print(ANDROID_LOG_DEBUG, CHIPBOX_TAG,
//                        "FIFO Input %llu bytes. Free space %llu bytes. Actually wrote %llu bytes.",
//                        input_length,
//                        free_space_in_buffer,
//                        bytes_to_actually_copy);

    return bytes_to_actually_copy;
}

uint64_t fifo_get(struct fifo_buffer *fifo, uint8_t *target_buffer, uint64_t bytes_requested) {
    uint64_t bytes_available = fifo->write_pos - fifo->read_pos;
    uint64_t bytes_to_actually_read = min(bytes_requested, bytes_available);
    uint64_t actual_read_pos = fifo->read_pos % fifo->size;
    uint64_t bytes_to_read_before_end = min(bytes_to_actually_read, fifo->size - actual_read_pos);

    // Read any bytes available, until the buffer ends. (It might not!)
    memcpy(target_buffer,
           fifo->buffer + actual_read_pos,
           bytes_to_read_before_end);

    // Read any available data left at the beginning of the buffer.
    memcpy(target_buffer + bytes_to_read_before_end,
           fifo->buffer,
           bytes_to_actually_read - bytes_to_read_before_end);

    fifo->read_pos += bytes_to_actually_read;

//    __android_log_print(ANDROID_LOG_DEBUG, CHIPBOX_TAG,
//                        "FIFO Requested %llu bytes. Available %llu bytes. Actually read %llu bytes.",
//                        bytes_requested,
//                        bytes_available,
//                        bytes_to_actually_read);

    return bytes_to_actually_read;
}
/**
 * End Fifo
 */


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_loadFile
        (JNIEnv *env, jobject, jstring java_filename, jint track, jint rate, jlong buffer_size,
         jlong fade_time_ms) {
    g_filename_c_str = env->GetStringUTFChars(java_filename, NULL);
    loadFileInternal(g_filename_c_str, rate, buffer_size, fade_time_ms);
}

void JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_readNextSamples
        (JNIEnv *env, jobject, jshortArray java_array) {
    if (g_sample_rate_track <= 0) {
        g_last_error = "Initialization failed.";
        return;
    }

    jboolean is_copy;
    jshort *target_array = env->GetShortArrayElements(java_array, &is_copy);
    if (target_array != NULL) {
        uint64_t remaining_samples =
                g_fade_start_threshold_samples_track + g_fade_length_samples_track -
                g_played_sample_count_track;
        uint64_t samples_to_read_track;

        if (remaining_samples < g_buffer_length_samples_track) {
            samples_to_read_track = remaining_samples;
        } else {
            samples_to_read_track = g_buffer_length_samples_track;
        }

        uint64_t leftover_bytes = fifo_get(&g_fifo_from_lib,
                                           reinterpret_cast<uint8_t *>(g_buffer_as_short_track),
                                           g_buffer_length_bytes_track);

//        if (leftover_bytes > 0) {
//            __android_log_print(ANDROID_LOG_DEBUG, CHIPBOX_TAG,
//                                "Copied left-over audio from previous generation: %llu bytes.",
//                                leftover_bytes);
//        }
        uint64_t remaining_bytes_to_fill = g_buffer_length_bytes_track - leftover_bytes;

        int64_t generated_bytes = generateAudio(remaining_bytes_to_fill);
        if (generated_bytes < 0) {
            return;
        }

        fifo_get(&g_fifo_from_lib,
                 reinterpret_cast<uint8_t *>(g_buffer_as_short_track) + leftover_bytes,
                 remaining_bytes_to_fill);

//        __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG,
//                            "Done copying from Sexyusf. Copied %lu bytes",
//                            bytes_copied_from_sexyusf);

        processFadeOut(g_buffer_as_short_track,
                       g_buffer_length_samples_track,
                       g_played_sample_count_track,
                       g_fade_start_threshold_samples_track,
                       g_fade_length_samples_track);

// BEGIN RESAMPLE
        if (g_sample_rate_track != g_sample_rate_device) {
            src_short_to_float_array(g_buffer_as_short_track, g_buffer_as_float_track,
                                     g_buffer_length_shorts_track);

            g_src_config.data_in = g_buffer_as_float_track;
            g_src_config.data_out = g_buffer_as_float_device;
            g_src_config.input_frames = samples_to_read_track;
            g_src_config.output_frames = g_buffer_length_samples_device;

            int error_src = src_simple(&g_src_config, SRC_LINEAR, 2);

            if (error_src != 0) {
                __android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "Resample error code: %d ",
                                    error_src);

                g_last_error = src_strerror(error_src);
                return;
            }
//            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Used input samples: %lld ", g_src_config.input_frames_used);
//            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Generated   output: %lld",  g_src_config.output_frames_gen);

            src_float_to_short_array(g_buffer_as_float_device, target_array,
                                     g_buffer_length_shorts_device);
// END RESAMPLE
        } else {
            memcpy(target_array, g_buffer_as_short_track, g_buffer_length_bytes_track);
        }

        env->ReleaseShortArrayElements(java_array, target_array, 0);

        g_played_sample_count_track += samples_to_read_track;
    } else {
        g_last_error = "Couldn't write to Java buffer.";
    }
}

int64_t generateAudio(uint64_t bytes_requested) {
    const char *error_string = usf_render_resampled(g_emu_state,
                                                    g_buffer_as_short_track,
                                                    g_buffer_length_samples_track,
                                                    g_sample_rate_track);
    if (error_string) {
        g_last_error = error_string;
        return -1;
    } else {
        return bytes_requested;
    }
}

jlong JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_getMillisPlayed
        (JNIEnv *env, jobject) {
    return getMillisPlayedInternal();
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_seek
        (JNIEnv *env, jobject, jlong time_in_ms) {
    g_stop_seek = false;

    int64_t target_sample_count_track = convert_ms_to_samples(g_sample_rate_track, time_in_ms);

    if (target_sample_count_track <= g_played_sample_count_track) {
        teardownInternal();
        loadFileInternal(g_filename_c_str, g_sample_rate_device, g_buffer_length_shorts_device,
                         g_track_length_ms);
    }

    uint64_t samples_remaining_track = target_sample_count_track - g_played_sample_count_track;

    while (samples_remaining_track > 0) {
        if (g_stop_seek) {
            return NULL;
        }

        uint64_t samples_to_read_track;
        if (samples_remaining_track > g_buffer_length_samples_track) {
            samples_to_read_track = g_buffer_length_samples_track;
        } else {
            samples_to_read_track = samples_remaining_track;
        }

        generateAudio(samples_to_read_track * BYTES_PER_SAMPLE);

        samples_remaining_track -= samples_to_read_track;
        g_played_sample_count_track += samples_to_read_track;
    }

    return NULL;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_setTempo
        (JNIEnv *env, jobject, jdouble tempo_relative) {
}

JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_getVoiceCount
        (JNIEnv *env, jobject) {
    return 0;
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_getVoiceName
        (JNIEnv *env, jobject, jint voice_number) {
    return nullptr;
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_muteVoice
        (JNIEnv *env, jobject, jint channel_number, jint enabled) {
}


JNIEXPORT jboolean JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_isTrackOver
        (JNIEnv *env, jobject) {
    return static_cast<jboolean>(getMillisPlayedInternal() >= g_track_length_ms);
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_teardown
        (JNIEnv *env, jobject) {
    teardownInternal();
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_usf_BackendImpl_getLastError
        (JNIEnv *env, jobject) {
    jstring str = env->NewStringUTF(g_last_error);
    return str;
}

const char *read_whole_file(const char *path, int64_t *size) {
    int64_t file_size = -1;
    std::ifstream inputStream(path, std::ifstream::binary);
    if (inputStream) {
        // get length of file:
        inputStream.seekg(0, inputStream.end);
        file_size = inputStream.tellg();
        inputStream.seekg(0, inputStream.beg);

        char *buffer = new char[file_size];

        __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Reading file %s of size %lu",
                            path, file_size);
        // read data as a block:
        inputStream.read(buffer, file_size);

        if (inputStream) {
            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "File read success!");
        } else {
            __android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "File read failure!");
        }
        inputStream.close();

        *size = file_size;
        return buffer;
    }
    *size = file_size;
    return nullptr;
}

uint64_t convert_samples_to_ms(int sample_rate, uint64_t samples) {
    uint64_t result = 1000 * samples / sample_rate;
    return result;
}

uint64_t convert_ms_to_samples(int sample_rate, uint64_t ms) {
    uint64_t result = sample_rate * ms / 1000;
    return result;
}

uint64_t getMillisPlayedInternal() {
    return convert_samples_to_ms(g_sample_rate_track, g_played_sample_count_track);
}

void loadFileInternal(const char *filename_c_str, jint rate, jlong buffer_size,
                      jlong fade_time_ms) {
    g_track_length_ms = fade_time_ms;
    g_sample_rate_device = rate;
    g_buffer_length_shorts_device = static_cast<uint64_t>(buffer_size);
    g_buffer_length_bytes_device = g_buffer_length_shorts_device * sizeof(short);
    g_buffer_length_samples_device = g_buffer_length_shorts_device / SHORTS_PER_SAMPLE;
    g_buffer_length_floats_device = g_buffer_length_samples_device * FLOATS_PER_SAMPLE;
    g_buffer_as_float_device_in_bytes = g_buffer_length_floats_device * sizeof(float);
    g_buffer_length_ms_device = g_buffer_length_samples_device * 1000 / g_sample_rate_device;

    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Device sample rate: %d",
                        g_sample_rate_device);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Device Buffer (as shorts) length: %llu shorts",
                        g_buffer_length_shorts_device);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Device Buffer (as shorts) length: %llu bytes",
                        g_buffer_length_bytes_device);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Device Buffer (as floats) length: %llu floats",
                        g_buffer_length_floats_device);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Device Buffer (as floats) length: %llu bytes",
                        g_buffer_as_float_device_in_bytes);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Device Buffer length: %llu samples",
                        g_buffer_length_samples_device);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Device Buffer length: %llu ms",
                        g_buffer_length_ms_device);

    if (g_initialized) {
        __android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG,
                            "Previously loaded emulator instance not cleared. Clearing...");
        teardownInternal();
    }

    // Initialize emulator.
    g_emu_state = (unsigned char *) malloc(usf_get_state_size());
    usf_clear(g_emu_state);

    int file_version = psf_load(filename_c_str,
                                &stdio_callbacks,
                                0x21,
                                usf_loader,
                                0,
                                usf_info,
                                0,
                                1,
                                print_message,
                                0);

    usf_set_compare(g_emu_state, g_enable_compare);
    usf_set_fifo_full(g_emu_state, g_enable_fifo_full);
    usf_set_hle_audio(g_emu_state, true);

    // Check for init success. If not, set error string and return.
    if (file_version != 0x21) {
        g_last_error = "File load failed.";
        free(g_emu_state);
        return;
    }

    g_initialized = true;

    // For now, match device sample rate
    g_sample_rate_track = g_sample_rate_device;

    g_buffer_length_samples_track = static_cast<uint64_t>((g_buffer_length_samples_device) *
                                                          (double) g_sample_rate_track /
                                                          g_sample_rate_device);
    g_buffer_length_bytes_track = g_buffer_length_samples_track * BYTES_PER_SAMPLE;
    g_buffer_length_shorts_track = g_buffer_length_samples_track * SHORTS_PER_SAMPLE;
    g_buffer_length_floats_track = g_buffer_length_samples_track * FLOATS_PER_SAMPLE;
    g_buffer_length_ms_track = g_buffer_length_samples_track * 1000 / g_sample_rate_track;
    g_buffer_as_float_track_in_bytes = g_buffer_length_floats_track * sizeof(float);
    g_src_config.src_ratio = (double) g_sample_rate_device / (double) g_sample_rate_track;
    g_fade_start_threshold_samples_track = convert_ms_to_samples(g_sample_rate_track,
                                                                 g_track_length_ms -
                                                                 (int64_t) FADE_TIME);
    g_fade_length_samples_track = convert_ms_to_samples(g_sample_rate_track, FADE_TIME);

    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track sample rate: %d",
                        g_sample_rate_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Track Buffer (as shorts) length: %lld shorts",
                        g_buffer_length_shorts_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Track Buffer (as shorts) length: %lld bytes", g_buffer_length_bytes_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Track Buffer (as floats) length: %lld floats",
                        g_buffer_length_floats_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG,
                        "Track Buffer (as floats) length: %lld bytes",
                        g_buffer_as_float_track_in_bytes);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track Buffer length: %lld samples",
                        g_buffer_length_samples_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track Buffer length: %lld ms",
                        g_buffer_length_ms_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track length: %lld ms", g_track_length_ms);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track fade length: %d ms", FADE_TIME);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track fade starts: %lld samples",
                        g_fade_start_threshold_samples_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Track fade length: %lld samples",
                        g_fade_length_samples_track);
    __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Converter sample ratio: %f",
                        g_src_config.src_ratio);

    fifo_init(&g_fifo_from_lib, g_buffer_length_bytes_track * 2);

    g_buffer_as_short_track = static_cast<short *>(malloc(g_buffer_length_bytes_track));
    g_buffer_as_float_track = static_cast<float *>(malloc(g_buffer_as_float_track_in_bytes));
    g_buffer_as_float_device = static_cast<float *>(malloc(g_buffer_as_float_device_in_bytes));
}

void teardownInternal() {
    usf_shutdown(g_emu_state);

    free(g_emu_state);
    g_emu_state = nullptr;

    free(g_fifo_from_lib.buffer);
    free(g_buffer_as_short_track);
    free(g_buffer_as_float_track);
    free(g_buffer_as_float_device);

    g_buffer_as_short_track = nullptr;
    g_buffer_as_float_track = nullptr;
    g_buffer_as_float_device = nullptr;

    g_played_sample_count_track = 0;

    g_initialized = false;
}

void processFadeOut(short *buffer_to_fade,
                    uint64_t buffer_length_samples,
                    uint64_t first_sample_index,
                    uint64_t fade_start_threshold_samples,
                    uint64_t fade_time_samples) {
    if (first_sample_index > fade_start_threshold_samples) {
        int64_t remaining_samples =
                fade_start_threshold_samples + fade_time_samples - first_sample_index;
        if (remaining_samples <= 0 && buffer_length_samples > 0) {
            __android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG,
                                "Track should be over with %lld remaining samples but still have %lld to fade out",
                                remaining_samples, buffer_length_samples);
        } else {
//            __android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Fading out: %lld samples remain. First sample %lld; Threshold sample %lld",
//                                remaining_samples,
//                                first_sample_index,
//                                fade_start_threshold_samples);
        }
    }

    for (uint64_t sample_index = 0; sample_index < buffer_length_samples; sample_index++) {
        uint64_t current_sample_index = first_sample_index + sample_index;
        if (current_sample_index < fade_start_threshold_samples) {
            break;
        }

        uint64_t short_offset = sample_index * SHORTS_PER_SAMPLE;
        uint64_t remaining_samples =
                (fade_start_threshold_samples + fade_time_samples) - current_sample_index;

        float sample_scaling_factor;

        if (remaining_samples > 0) {
            sample_scaling_factor = (float) remaining_samples / fade_time_samples;
        } else {
            sample_scaling_factor = 0.0f;
        }
//        __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Sample scaling factor: %f",
//                            sample_scaling_factor);

        *(buffer_to_fade + short_offset) = (short) (*(buffer_to_fade + short_offset) *
                                                    sample_scaling_factor);
        *(buffer_to_fade + short_offset + 1) = (short) (*(buffer_to_fade + short_offset + 1) *
                                                        sample_scaling_factor);
    }
}