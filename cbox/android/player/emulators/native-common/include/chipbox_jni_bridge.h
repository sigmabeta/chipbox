// Shared JNI bridge for Chipbox emulators.
//
// Every emulator's native lib exposes the same wrapper contract and the same
// four JNI entry points (buffer generation, teardown, last-error, sample
// rate). This header is the single definition of both. Each emulator's
// kotlin-jni.cpp includes its own <Name>.h, defines CHIPBOX_JNI_CLASS to its
// fully-qualified JNI prefix (trailing underscore included), then includes
// this header. The per-emulator track-load entry point (and psf's
// diagnostics) stays hand-written because its signature varies.

#ifndef CHIPBOX_JNI_BRIDGE_H
#define CHIPBOX_JNI_BRIDGE_H

#include <jni.h>
#include <cstdint>

#ifndef CHIPBOX_JNI_CLASS
#error "Define CHIPBOX_JNI_CLASS (e.g. Java_..._VgmEmulator_) before including chipbox_jni_bridge.h"
#endif

#define CHIPBOX_JNI_PASTE_(a, b) a##b
#define CHIPBOX_JNI_PASTE(a, b) CHIPBOX_JNI_PASTE_(a, b)
#define CHIPBOX_JNI_FN(suffix) CHIPBOX_JNI_PASTE(CHIPBOX_JNI_CLASS, suffix)

// Shared native wrapper contract implemented by every emulator's <Name>.cpp.
// Declared with default (C++) linkage to match every emulator's <Name>.h,
// which is included before this header.
int32_t generateBuffer(int16_t *target, int32_t frames);
void teardown();
const char *get_last_error();
int32_t get_sample_rate();

extern "C" {

JNIEXPORT jint JNICALL CHIPBOX_JNI_FN(generateBufferInternal)(
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

JNIEXPORT void JNICALL CHIPBOX_JNI_FN(teardownInternal)(
        JNIEnv *env,
        jobject thiz
) {
    teardown();
}

JNIEXPORT jstring JNICALL CHIPBOX_JNI_FN(getLastError)(
        JNIEnv *env,
        jobject thiz
) {
    const char *last_error = get_last_error();
    if (last_error == nullptr) {
        return nullptr;
    }
    return env->NewStringUTF(last_error);
}

JNIEXPORT jint JNICALL CHIPBOX_JNI_FN(getSampleRateInternal)(
        JNIEnv *env,
        jobject thiz
) {
    return get_sample_rate();
}

}

#endif //CHIPBOX_JNI_BRIDGE_H
