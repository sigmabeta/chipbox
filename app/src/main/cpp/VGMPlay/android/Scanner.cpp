
extern "C" {
#include "../../VGMPlay/chips/mamedef.h"
#include "../VGMPlay_Intf.h"
#include "../VGMFile.h"
}

#include <string.h>
#include <stdint.h>
#include "net_sigmabeta_chipbox_backend_vgm_ScannerImpl.h"
#include <wchar.h>

const char *g_last_error;

VGM_HEADER g_header;
GD3_TAG g_tag;

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getPlatform
        (JNIEnv *env, jobject, jstring);


JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_fileInfoSetup
        (JNIEnv *env, jobject, jstring java_filename) {
    const char *filename_c_str = env->GetStringUTFChars(java_filename, NULL);
    int fileSize = GetVGMFileInfo(filename_c_str, &g_header, &g_tag);

    if (fileSize == 0) {
        g_last_error = "Failed to load file.";
        return env->NewStringUTF(g_last_error);
    }

    return NULL;
}


JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_fileInfoGetTrackCount
        (JNIEnv *env, jobject) {
    return 1;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_fileInfoSetTrackNumber
        (JNIEnv *env, jobject, jint) {
    // no-op
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_fileInfoTeardown
        (JNIEnv *env, jobject) {
    g_last_error = NULL;
}


JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileTrackLength
        (JNIEnv *env, jobject) {
    // All vgm files are processed at this sample rate
    return g_header.lngTotalSamples / 44100;
}


JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileIntroLength
        (JNIEnv *env, jobject) {
    return 0;
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileLoopLength
        (JNIEnv *env, jobject) {
    return 0;
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileTitle
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_tag.strTrackNameE);
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileGameTitle
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_tag.strGameNameE);
}


JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFilePlatform
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_tag.strSystemNameE);
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_vgm_ScannerImpl_getFileArtist
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_tag.strAuthorNameE);
}

/**
 * Private Methods
 */

jbyteArray get_java_byte_array(JNIEnv *env, wchar_t *source) {
    if (source != NULL) {
        int length_in_wchars = wcslen(source);

        char char_array[length_in_wchars];

        for (int index = 0; index < length_in_wchars; index++) {
            // Should truncate all the extra 0's.
            char current = *(source + index);
            char_array[index] = current;
        }

        jbyteArray destination = env->NewByteArray(length_in_wchars);
        env->SetByteArrayRegion(destination, 0, length_in_wchars, (jbyte *) char_array);

        return destination;
    } else {
        return NULL;
    }
}