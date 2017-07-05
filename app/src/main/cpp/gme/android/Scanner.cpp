#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "../Music_Emu.h"
#include "net_sigmabeta_chipbox_backend_gme_ScannerImpl.h"

Music_Emu *g_file_info_reader;

gme_info_t *g_track_info;

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_fileInfoSetup
        (JNIEnv *env, jobject, jstring java_path) {
    if (g_file_info_reader) {
        teardown();
        return env->NewStringUTF("Previous file not torn down");
    }

    const char *path = env->GetStringUTFChars(java_path, NULL);

    // Find out what type of VGM track the given file is.
    gme_type_t file_type;
    const char *error = gme_identify_file(path, &file_type);
    if (error) {
        teardown();
        return env->NewStringUTF(error);
    }

    // If unsupported file type, do nothing else.
    if (!file_type) {
        teardown();
        return env->NewStringUTF("Unsupported file type");
    }

    // Open new emulator from which to get track info.
    gme_open_file(path, &g_file_info_reader, 44100);
    if (!g_file_info_reader) {
        teardown();
        return env->NewStringUTF("Out of memory");
    }

    return NULL;
}


JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_fileInfoGetTrackCount
        (JNIEnv *env, jobject) {
    if (g_file_info_reader)
        return gme_track_count(g_file_info_reader);
    else
        return -1;
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_fileInfoSetTrackNumber
        (JNIEnv *env, jobject, jint track_number) {
    if (g_track_info) {
        delete g_track_info;
        g_track_info = NULL;
    }

    gme_track_info(g_file_info_reader, &g_track_info, track_number);
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_fileInfoTeardown
        (JNIEnv *env, jobject) {
    teardown();
}


JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileTrackLength
        (JNIEnv *env, jobject) {
    return g_track_info->length;
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileIntroLength
        (JNIEnv *env, jobject) {
    return g_track_info->intro_length;
}


JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileLoopLength
        (JNIEnv *env, jobject) {
    return g_track_info->loop_length;
}


JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileTitle
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_track_info->song);
}


JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileGameTitle
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_track_info->game);
}


JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFilePlatform
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_track_info->system);
}


JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_backend_gme_ScannerImpl_getFileArtist
        (JNIEnv *env, jobject) {
    return get_java_byte_array(env, g_track_info->author);
}

/**
 * Private Methods
 */

jbyteArray get_java_byte_array(JNIEnv *env, const char *source) {
    size_t length = strlen(source);

    jbyteArray destination = env->NewByteArray(length);
    env->SetByteArrayRegion(destination, 0, length, (jbyte *) source);

    return destination;
}

void teardown() {
    if (g_file_info_reader) {
        delete g_file_info_reader;
        g_file_info_reader = NULL;
    }

    if (g_track_info) {
        delete g_track_info;
        g_track_info = NULL;
    }
}

