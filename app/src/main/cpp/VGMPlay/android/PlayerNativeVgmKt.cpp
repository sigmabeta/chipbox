extern "C" {
#include "../../VGMPlay/chips/mamedef.h"
#include "../VGMPlay_Intf.h"
#include "../VGMFile.h"
}

#include <string.h>
#include <stdint.h>
#include "net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt.h"
#include <android/log.h>
#include <wchar.h>

const char *g_last_error;

VGM_HEADER g_header;
GD3_TAG g_tag;

#define CHIPBOX_TAG "ChipboxNative"
#define BYTES_PER_W_CHAR 4


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt_loadFileVgm
        (JNIEnv *env, jclass clazz, jstring filename) {

    const char *filename_c_str = env->GetStringUTFChars(filename, NULL);
    GetVGMFileInfo(filename_c_str, &g_header, &g_tag);
    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileVgm] Loading file %s",
                        filename_c_str);

    bool successful_load = false;
    char filename_char_array[260];
    strcpy(filename_char_array, filename_c_str);

    VGMPlay_Init();

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileVgm] Loaded header");

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

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt_readNextSamplesVgm
        (JNIEnv *env, jclass clazz, jshortArray java_array, jint sample_count) {
    jboolean is_copy;
    jshort *target_array = env->GetShortArrayElements(java_array, &is_copy);

    if (target_array != NULL) {
        uint32_t created_samples = FillBuffer((WAVE_16BS *) target_array, (UINT32) sample_count);

        env->ReleaseShortArrayElements(java_array, target_array, 0);

        if (sample_count != created_samples) {
            g_last_error = "Wrote fewer samples than expected.";
        }
    } else {
        g_last_error = "Couldn't write to Java buffer.";
    }
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt_teardownVgm
        (JNIEnv *env, jclass clazz) {
    StopVGM();
    CloseVGMFile();
    VGMPlay_Deinit();
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt_getLastErrorVgm
        (JNIEnv *env, jclass clazz) {
    jstring str = env->NewStringUTF(g_last_error);
    return str;
}

jbyteArray get_java_byte_array(JNIEnv *, wchar_t *);

JNIEXPORT jbyteArray JNICALL
Java_net_sigmabeta_chipbox_util_external_PlayerNativeVgmKt_getFileTitleVgm
        (JNIEnv *env, jclass clazz) {
    return get_java_byte_array(env, g_tag.strTrackNameE);
}
