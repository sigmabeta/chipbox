#include <jni.h>
#include "Psf.h"

// generateBufferInternal / teardownInternal / getLastError /
// getSampleRateInternal come from the shared bridge.
#define CHIPBOX_JNI_CLASS Java_net_sigmabeta_chipbox_player_emulators_psf_PsfEmulator_
#include <chipbox_jni_bridge.h>

extern "C" {

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_psf_PsfEmulator_loadTrackInternal(
        JNIEnv *env,
        jobject thiz,
        jstring java_filename
) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, nullptr);
    loadFile(filename_c_str);
    env->ReleaseStringUTFChars(java_filename, filename_c_str);
}

JNIEXPORT jstring JNICALL
Java_net_sigmabeta_chipbox_player_emulators_psf_PsfEmulator_getDiagnostics(
        JNIEnv *env,
        jobject thiz
) {
    const char *diagnostics = get_diagnostics();
    if (!diagnostics) return nullptr;
    return env->NewStringUTF(diagnostics);
}

}
