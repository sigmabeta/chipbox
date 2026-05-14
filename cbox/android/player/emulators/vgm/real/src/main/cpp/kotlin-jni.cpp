#include <jni.h>
#include "Vgm.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgm_VgmEmulator_loadTrackInternalNative(
        JNIEnv *env,
        jobject thiz,
        jstring java_filename
) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, nullptr);
    loadFile(filename_c_str);
    env->ReleaseStringUTFChars(java_filename, filename_c_str);
}

JNIEXPORT jint JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgm_VgmEmulator_generateBufferInternal(
        JNIEnv *env,
        jobject thiz,
        jshortArray java_array,
        jint frames_per_buffer
) {
    jshort *target_array = env->GetShortArrayElements(java_array, nullptr);
    int32_t framesWritten = generateBuffer(target_array, frames_per_buffer);
    env->ReleaseShortArrayElements(java_array, target_array, 0);
    return framesWritten;
}

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgm_VgmEmulator_teardownInternal(
        JNIEnv *env,
        jobject thiz
) {
    teardown();
}

JNIEXPORT jstring JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgm_VgmEmulator_getLastError(
        JNIEnv *env,
        jobject thiz
) {
    const char *last_error = get_last_error();
    if (last_error == nullptr) {
        return nullptr;
    }
    return env->NewStringUTF(last_error);
}

JNIEXPORT jint JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgm_VgmEmulator_getSampleRateInternal(
        JNIEnv *env,
        jobject thiz
) {
    return get_sample_rate();
}

#ifdef __cplusplus
}
#endif
