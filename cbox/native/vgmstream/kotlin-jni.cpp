#include <jni.h>
#include <cstdio>
#include <cstring>
#include "chipbox_vgmstream.h"

extern "C" {
#include "libvgmstream.h"
}

// generateBufferInternal / teardownInternal / getLastError /
// getSampleRateInternal come from the shared bridge.
#define CHIPBOX_JNI_CLASS Java_net_sigmabeta_chipbox_player_emulators_vgmstream_VgmstreamEmulator_
#include <chipbox_jni_bridge.h>

extern "C" {

// vgmstream formats can carry multiple subsongs; the subsong index rides in as the track number
// (mirrors GmeEmulator's loadTrackInternalWithNumber).
JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgmstream_VgmstreamEmulator_loadTrackInternalWithNumber(
        JNIEnv *env,
        jobject thiz,
        jstring java_filename,
        jint subsong
) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, nullptr);
    loadFile(filename_c_str, subsong);
    env->ReleaseStringUTFChars(java_filename, filename_c_str);
}

// Returns vgmstream's supported extensions minus the "common" ones (wav/ogg/mp3/...), so the
// emulator's provider gate is sourced from vgmstream itself. Used to pick this emulator (kept last
// in the provider list, so existing chiptune emulators win shared extensions). Scanning does not
// use this: the scanner is pure Kotlin and ships its own vendored-list mirror in
// `readers/StreamedExtensions.kt`.
JNIEXPORT jobjectArray JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgmstream_VgmstreamEmulator_getSupportedExtensionsInternal(
        JNIEnv *env,
        jobject thiz
) {
    int all_count = 0;
    const char *const *all = get_supported_extensions(&all_count);
    int common_count = 0;
    const char *const *common = get_common_extensions(&common_count);

    jclass string_class = env->FindClass("java/lang/String");
    // Over-allocate to all_count; trailing nulls are fine for callers that filter, but we compact.
    int kept = 0;
    for (int i = 0; i < all_count; ++i) {
        bool is_common = false;
        for (int j = 0; j < common_count; ++j) {
            if (strcmp(all[i], common[j]) == 0) { is_common = true; break; }
        }
        if (!is_common) ++kept;
    }

    jobjectArray result = env->NewObjectArray(kept, string_class, nullptr);
    int out = 0;
    for (int i = 0; i < all_count; ++i) {
        bool is_common = false;
        for (int j = 0; j < common_count; ++j) {
            if (strcmp(all[i], common[j]) == 0) { is_common = true; break; }
        }
        if (is_common) continue;
        jstring s = env->NewStringUTF(all[i]);
        env->SetObjectArrayElement(result, out++, s);
        env->DeleteLocalRef(s);
    }
    return result;
}

}
