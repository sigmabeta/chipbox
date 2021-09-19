#include <jni.h>
#include "Usf.h"

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_usf_UsfEmulator_loadTrackInternal(
        JNIEnv *env,
        __unused jobject thiz,
        jstring java_filename
) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, NULL);
    loadFile(filename_c_str);
}

JNIEXPORT jint JNICALL
Java_net_sigmabeta_chipbox_player_emulators_usf_UsfEmulator_generateBufferInternal(
        JNIEnv *env,
        jobject thiz,
        jshortArray java_array,
        jint frames_per_buffer
) {
    jboolean is_copy;
    jshort *target_array = env->GetShortArrayElements(java_array, &is_copy);

    int32_t framesWritten = generateBuffer(target_array, frames_per_buffer);

    env->ReleaseShortArrayElements(java_array, target_array, 0);

    return framesWritten;
}

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_usf_UsfEmulator_teardownInternal(
        JNIEnv *env,
        jobject thiz
) {
    teardown();
}

JNIEXPORT jstring JNICALL
Java_net_sigmabeta_chipbox_player_emulators_usf_UsfEmulator_getLastError(
        JNIEnv *env,
        jobject thiz
) {
    const char *last_error = get_last_error();
    jstring str = env->NewStringUTF(last_error);
    return str;
}

JNIEXPORT jint JNICALL
Java_net_sigmabeta_chipbox_player_emulators_usf_UsfEmulator_getSampleRateInternal(JNIEnv *env,
                                                                                  jobject thiz) {
    return get_sample_rate();
}

#ifdef __cplusplus
}
#endif