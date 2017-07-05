extern "C" {
#include "../../VGMPlay/chips/mamedef.h"
#include "../VGMPlay_Intf.h"
}

#include <string>
#include <sstream>
#include "net_sigmabeta_chipbox_backend_vgm_BackendImpl.h"
#include <android/log.h>
#include <math.h>

#define CHIPBOX_TAG "BackendVGM"
#define MAX_ACTIVE_CHIPS 10 // Probably won't have more than this many chips running at a time.

const char *g_last_error;

long g_sample_count;
int g_sample_rate;

int g_channel_count;
int g_active_chip_count;
int g_active_chip_ids[MAX_ACTIVE_CHIPS];

extern VGM_HEADER VGMHead;

extern CHIPS_OPTION ChipOpts[0x02];

CHIP_OPTS *g_mute_opts_array[MAX_ACTIVE_CHIPS];

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_loadFile
        (JNIEnv *env, jobject, jstring java_filename, jint track, jint rate, jlong buffer_size,
         jlong fade_time_ms) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, NULL);
    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Loading file %s",
                        filename_c_str);

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Buffer size: %lu shorts",
                        buffer_size);

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "Setting sample rate: %d",
                        rate);

    g_sample_count = buffer_size / 2;
    g_sample_rate = rate;

    bool successful_load = false;
    char filename_char_array[260];
    strcpy(filename_char_array, filename_c_str);

    VGMPlay_Init(g_sample_rate);

    // load configuration file here
    VGMPlay_Init2();

    successful_load = OpenVGMFile(filename_char_array);
    if (!successful_load) {
        g_last_error = "Failed to load file.";
        __android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
        return;
    }

    PlayVGM();

    for (int chip_index = 0; chip_index < CHIP_COUNT; chip_index++) {
        UINT32 clock = GetChipClock(&VGMHead, chip_index, NULL);

        if (clock > 0) {
            const char *chip_name = GetAccurateChipName(chip_index, 0);
            int channel_count = getChannelCountForChipId(chip_index);

            g_channel_count += channel_count;
            g_active_chip_ids[g_active_chip_count] = chip_index;
            g_mute_opts_array[g_active_chip_count] = (CHIP_OPTS *) &ChipOpts[0] + chip_index;

            g_active_chip_count++;

            __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG,
                                "Chip name: %s Channels: %d", chip_name, channel_count);
        }
    }

}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_readNextSamples
        (JNIEnv *env, jobject, jshortArray java_array) {
    jboolean is_copy;
    jshort *target_array = env->GetShortArrayElements(java_array, &is_copy);

    if (target_array != NULL) {
        uint32_t created_samples = FillBuffer((WAVE_16BS *) target_array, (UINT32) g_sample_count);

        env->ReleaseShortArrayElements(java_array, target_array, 0);

        if (g_sample_count != created_samples && !isTrackOver()) {
            g_last_error = "Wrote fewer samples than expected.";
        }
    } else {
        g_last_error = "Couldn't write to Java buffer.";
    }
}

jlong JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getMillisPlayed
        (JNIEnv *env, jobject) {
    return getMillisPlayed();
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_seek
        (JNIEnv *env, jobject, jlong time_in_ms) {
    UINT32 samples = CalcSampleMSec(time_in_ms, MSEC_TO_SAMPLES_RATE_CURRENT);
    SeekVGM(false, samples);
    return NULL;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_setTempo
        (JNIEnv *env, jobject, jdouble tempo_relative) {
    double rateDouble = tempo_relative * 100.0;
    UINT32 rateInt = (UINT32) round(rateDouble);
    setPlaybackRate(rateInt);
}


JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getVoiceCount
        (JNIEnv *env, jobject) {
    return g_channel_count;
}


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getVoiceName
        (JNIEnv *env, jobject, jint voice_number) {
    int channel_number = voice_number;

    const char *voice_name = NULL;
    for (int chip_index = 0; chip_index < g_active_chip_count; ++chip_index) {
        int chip_id = g_active_chip_ids[chip_index];
        int channel_count = getChannelCountForChipId(chip_id);

        const char *chip_name = GetAccurateChipName(chip_id, 0);

        if (channel_number >= channel_count) {
            channel_number -= channel_count;
        } else {
            if (channel_count > 1) {
                std::ostringstream stream;
                stream << chip_name << " Voice " << channel_number + 1;

                voice_name = stream.str().c_str();
            } else {
                voice_name = chip_name;
            }
            break;
        }
    }

    if (voice_name != NULL) {
        return env->NewStringUTF(voice_name);
    } else {
        return NULL;
    }
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_muteVoice
        (JNIEnv *env, jobject, jint channel_number, jint enabled) {

    for (int chip_index = 0; chip_index < g_active_chip_count; ++chip_index) {
        int chip_id = g_active_chip_ids[chip_index];
        int channel_count = getChannelCountForChipId(chip_id);

        if (channel_number >= channel_count) {
            channel_number -= channel_count;
        } else {
            CHIP_OPTS *mute_options = g_mute_opts_array[chip_index];
            setMutingData(chip_id, mute_options, channel_number, enabled != 0);
            break;
        }
    }
}


JNIEXPORT jboolean JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_isTrackOver
        (JNIEnv *env, jobject) {
    return isTrackOver();
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_teardown
        (JNIEnv *env, jobject) {
    g_channel_count = 0;
    g_active_chip_count = 0;

    for (int chip_index = 0; chip_index < MAX_ACTIVE_CHIPS; ++chip_index) {
        g_active_chip_ids[chip_index] = -1;
        g_mute_opts_array[chip_index] = NULL;
    }

    StopVGM();
    CloseVGMFile();
    VGMPlay_Deinit();
}


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getLastError
        (JNIEnv *env, jobject) {
    jstring str = env->NewStringUTF(g_last_error);
    return str;
}

/**
 * Private Methods
 */

bool isTrackOver() {
    return getMillisPlayed() >= getTrackLengthMillis();
}

long getMillisPlayed() {
    long samples = getSamplesPlayed();
    return CalcSampleMSec(samples, SAMPLES_TO_MSEC_RATE_CURRENT);
}

long getTrackLengthMillis() {
    return CalcSampleMSecExt(VGMHead.lngTotalSamples, SAMPLES_TO_MSEC_RATE_DEFAULT, &VGMHead);
}

void setMutingData(int mute_chip_id, CHIP_OPTS *mute_options, int channel_number, bool muted) {
    int current_channel;
    int special_modes;
    int current_mode;

    special_modes = 0;
    switch (mute_chip_id) {
        case 0x06:    // YM2203
            special_modes = 2;
            break;
        case 0x07:    // YM2608
        case 0x08:    // YM2610
            special_modes = 3;
            break;
        case 0x11:    // PWM
        case 0x16:    // UPD7759
        case 0x17:    // OKIM6258
            mute_options->Disabled = muted;
            RefreshMuting();
            return;
        default:
            break; // no-op
    }

    if (!special_modes) {
        current_mode = 0;
        if (mute_chip_id == 0x0D) {
            current_mode = 1;
        }
        current_channel = channel_number;
    } else {
        current_mode = channel_number / 8;
        current_channel = channel_number % 8;
    }

    switch (current_mode) {
        case 0:
            mute_options->ChnMute1 &= ~(1 << current_channel);
            mute_options->ChnMute1 |= (muted << current_channel);
            break;
        case 1:
            mute_options->ChnMute2 &= ~(1 << current_channel);
            mute_options->ChnMute2 |= (muted << current_channel);
            break;
        case 2:
            mute_options->ChnMute3 &= ~(1 << current_channel);
            mute_options->ChnMute3 |= (muted << current_channel);
            break;
    }

    RefreshMuting();
    return;
}

int getChannelCountForChipId(int chip_id) {
    switch (chip_id) {
        case 0x00:    // SN76496
            return 4;
        case 0x01:    // YM2413
        case 0x09:    // YM3812
        case 0x0A:    // YM3526
        case 0x0B:    // Y8950
            return 14;
        case 0x02:    // YM2612
            return 7;
        case 0x03:    // YM2151
            return 8;
        case 0x04:    // Sega PCM
            return 16;
        case 0x05:    // RF5C68
        case 0x10:    // RF5C164
            return 8;
        case 0x06:    // YM2203
            return 6;    // 3 FM + 3 AY8910
        case 0x07:    // YM2608
        case 0x08:    // YM2610
            return 16;    // 6 FM + 6 ADPCM + 1 DeltaT + 3 AY8910
        case 0x0C:    // YMF262
            return 23;    // 18 + 5
        case 0x0D:    // YMF278B
            return 24;
        case 0x0E:    // YMF271
            return 12;
        case 0x0F:    // YMZ280B
            return 8;
        case 0x11:    // PWM
            return 1;
        case 0x12:    // AY8910
            return 3;
        case 0x13:    // GB DMG
            return 4;
        case 0x14:    // NES APU
            return 6;
        case 0x15:    // Multi PCM
            return 28;
        case 0x16:    // UPD7759
            return 1;
        case 0x17:    // OKIM6258
            return 1;
        case 0x18:    // OKIM6295
            return 4;
        case 0x19:    // K051649
            return 5;
        case 0x1A:    // K054539
            return 8;
        case 0x1B:    // HuC6280
            return 6;
        case 0x1C:    // C140
            return 24;
        case 0x1D:    // K053260
            return 4;
        case 0x1E:    // Pokey
            return 4;
        case 0x1F:    // Q-Sound
            return 16;
        case 0x20:    // SCSP
            return 32;
        case 0x21:    // WonderSwan
            return 4;
        case 0x22:    // VSU
            return 6;
        case 0x23:    // SAA1099
            return 6;
        case 0x24:    // ES5503
            return 32;
        case 0x25:    // ES5506
            return 32;
        case 0x26:    // X1-010
            return 16;
        case 0x27:    // C352
            return 32;
        case 0x28:    // GA20
            return 4;
        default:
            return -1;
    }
}