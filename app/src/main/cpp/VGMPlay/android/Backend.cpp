extern "C" {
#include "../../VGMPlay/chips/mamedef.h"
#include "../VGMPlay_Intf.h"
}

#include <string.h>
#include <stdio.h>
#include "net_sigmabeta_chipbox_backend_vgm_BackendImpl.h"
#include <android/log.h>

#define CHIPBOX_TAG "ChipboxVGM"

const char *g_last_error;

long g_sample_count;
int g_sample_rate;

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_loadFile
        (JNIEnv *env, jobject, jstring java_filename, jint track, jint rate, jlong buffer_size,
         jlong fade_time_ms) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, NULL);
    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileVgm] Loading file %s",
                        filename_c_str);

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[ChipboxVGM] Buffer size: %lu shorts",
                        buffer_size);

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[ChipboxVGM] Setting sample rate: %d",
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
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_readNextSamples
        (JNIEnv *env, jobject, jshortArray java_array) {
    jboolean is_copy;
    jshort *target_array = env->GetShortArrayElements(java_array, &is_copy);

    if (target_array != NULL) {
        uint32_t created_samples = FillBuffer((WAVE_16BS *) target_array, (UINT32) g_sample_count);

        env->ReleaseShortArrayElements(java_array, target_array, 0);

        if (g_sample_count != created_samples) {
            g_last_error = "Wrote fewer samples than expected.";
        }
    } else {
        g_last_error = "Couldn't write to Java buffer.";
    }
}


JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getMillisPlayed
        (JNIEnv *env, jobject) {
    return getSamplesPlayed() / g_sample_rate;
}


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_seek
        (JNIEnv *env, jobject, jint time_in_ms) {
    SeekVGM(false, (time_in_ms * 1000) / g_sample_rate);
    return NULL;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_setTempo
        (JNIEnv *env, jobject, jdouble tempo_relative) {
    int rate = tempo_relative * 60;

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[ChipboxVGM] Setting playback rate: %d",
                        rate);
    setPlaybackRate(rate);
}


JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getVoiceCount
        (JNIEnv *env, jobject) {
    return 0;
}


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getVoiceName
        (JNIEnv *env, jobject, jint) {
    return NULL;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_muteVoice
        (JNIEnv *env, jobject, jint, jint) {
    // no-op
}


JNIEXPORT jboolean JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_isTrackOver
        (JNIEnv *env, jobject) {
    // no-op
    return false;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_teardown
        (JNIEnv *env, jobject) {
    StopVGM();
    CloseVGMFile();
    VGMPlay_Deinit();
}


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_BackendImpl_getLastError
        (JNIEnv *env, jobject) {
    jstring str = env->NewStringUTF(g_last_error);
    return str;
}