#include <jni.h>
#include "ncsf.h"

// generateBufferInternal / teardownInternal / getLastError /
// getSampleRateInternal come from the shared bridge.
#define CHIPBOX_JNI_CLASS Java_net_sigmabeta_chipbox_player_emulators_ncsf_NcsfEmulator_
#include <chipbox_jni_bridge.h>

extern "C" {

JNIEXPORT void JNICALL
Java_net_sigmabeta_chipbox_player_emulators_ncsf_NcsfEmulator_loadTrackInternal(
        JNIEnv *env,
        jobject thiz,
        jstring java_filename
) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, nullptr);
    loadFile(filename_c_str);
    env->ReleaseStringUTFChars(java_filename, filename_c_str);
}

}
