#include "../Music_Emu.h"
#include <stdio.h>
#include "net_sigmabeta_chipbox_util_external_FileNativeKt.h"
#include <android/log.h>

#define CHIPBOX_TAG "ChipboxNative"

Music_Emu * g_file_info_reader;

gme_info_t * g_track_info;	

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_fileInfoSetupNative
  (JNIEnv * env, jclass clazz, jstring filePath)
{	
	if (g_file_info_reader) {
		teardown();
		return env->NewStringUTF("Previous file not torn down");
	}

	const char* path = env->GetStringUTFChars(filePath, NULL);
//	__android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Opening file at: %s", path);

	// Find out what type of VGM track the given file is.
	gme_type_t file_type;
	const char* error = gme_identify_file(path, &file_type);
	if (error)
	{
		teardown();
		return env->NewStringUTF(error);
	}

	// If unsupported file type, do nothing else.
	if (!file_type) 
	{
		teardown();
		return env->NewStringUTF("Unsupported file type");
	}

	// Open new emulator from which to get track info.
	gme_open_file(path, &g_file_info_reader, 44100);
	if (!g_file_info_reader) 
	{
		teardown();
		return env->NewStringUTF("Out of memory");
	}

	return NULL;
}

JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_fileInfoGetTrackCount
  (JNIEnv * env, jclass clazz)
{
	if (g_file_info_reader)
		return gme_track_count(g_file_info_reader);
	else
		return -1;
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_fileInfoSetTrackNumberNative
  (JNIEnv * env, jclass clazz, jint track_number)
{
	if (g_track_info)
	{
		delete g_track_info;
		g_track_info = NULL;
	}

//	__android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "Opening track #: %i", track_number);

	gme_track_info(g_file_info_reader, &g_track_info, track_number);
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_fileInfoTeardownNative
  (JNIEnv * env, jclass clazz)
{
	teardown();
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileTrackLength
  (JNIEnv * env, jclass clazz)
{
	return g_track_info->length;
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileIntroLength
  (JNIEnv * env, jclass clazz)
{
	return g_track_info->intro_length;
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileLoopLength
  (JNIEnv * env, jclass clazz)
{
	return g_track_info->loop_length;
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileTitle
  (JNIEnv * env, jclass clazz)
{
	return get_java_byte_array(env, g_track_info->song);
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileGameTitle
  (JNIEnv * env, jclass clazz)
{
	return get_java_byte_array(env, g_track_info->game);
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFilePlatform
  (JNIEnv * env, jclass clazz)
{
	return get_java_byte_array(env, g_track_info->system);
}

JNIEXPORT jbyteArray JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getFileArtist
  (JNIEnv * env, jclass clazz)
{
	return get_java_byte_array(env, g_track_info->author);
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_FileNativeKt_getPlatformNative
  (JNIEnv * env, jclass clazz, jstring jPath)
{
	const char* path = env->GetStringUTFChars(jPath, NULL);

	// Find out what type of VGM track the given file is.
	gme_type_t file_type;
	const char* last_track_error = gme_identify_file(path, &file_type );

	// TODO Output this error.
	if( last_track_error )
	{
		return NULL;
	}

	// If unsupported file type, do nothing else.
	if (!file_type) 
	{
		last_track_error = "Unsupported music type";
		return NULL;
	} 
	else 
	{
		__android_log_print(ANDROID_LOG_INFO, CHIPBOX_TAG, "%s track at: %s", file_type->system, path);
		return env->NewStringUTF(file_type->system);
	}
}

jbyteArray get_java_byte_array(JNIEnv * env, const char * source)
{
	int length = strlen(source);

	jbyteArray destination = env->NewByteArray(length);
    env->SetByteArrayRegion(destination, 0, length, (jbyte *)source);

	return destination;
}

void teardown() 
{
	if (g_file_info_reader)
	{
		delete g_file_info_reader;
		g_file_info_reader = NULL;
	}

	if (g_track_info)
	{
		delete g_track_info;
		g_track_info = NULL;
	}
}

