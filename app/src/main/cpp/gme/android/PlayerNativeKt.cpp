#include "../Music_Emu.h"
#include <stdio.h>
#include "net_sigmabeta_chipbox_util_external_PlayerNativeKt.h"
#include <android/log.h>

#define CHIPBOX_TAG "ChipboxNative"

Music_Emu* g_emu;

const char* g_last_error;

long g_buffer_size;

int g_fade_time_ms;

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_loadFileGme
	(JNIEnv * env, jclass clazz, jstring file, jint track, jint rate, jlong buffer_size, jint fade_time_ms)
{
	const char* filename = env->GetStringUTFChars(file, NULL);

	if (g_emu)
	{
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "Previously loaded emulator instance not cleared. Clearing...");
		delete g_emu;
		g_emu = NULL;
	}

	long sample_rate = (long)rate;

	// Determine file type.
	gme_type_t file_type;
	g_last_error = gme_identify_file(filename, &file_type );

	if (g_last_error)
	{
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "Error identifying file type.");
		return;
	}

	if (!file_type)
	{
		g_last_error = "Unsupported music type";
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
		return;//handle_error( "Unsupported music type" );
	}

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG,
                        "[loadFileGme] Creating emulator instance.");
	g_emu = file_type->new_emu();
	if (!g_emu)
	{
		g_last_error = "Out of memory";
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
		return;// handle_error( "Out of memory" );
	}

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileGme] Setting sample rate: %d",
                        rate);
	g_last_error = g_emu->set_sample_rate( sample_rate ) ;
    if (g_last_error)
	{
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
		return;
	}

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileGme] Loading file %s",
                        filename);
	g_last_error = g_emu->load_file(filename);
	if (g_last_error)
	{
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
		return;
	}

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileGme] Starting track: %d",
                        track);
	g_last_error = g_emu->start_track(track);
    if (g_last_error)
	{
		__android_log_print(ANDROID_LOG_ERROR, CHIPBOX_TAG, "%s", g_last_error);
		return;
	}

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileGme] Setting fade time: %lu",
                        fade_time_ms);
	gme_set_fade(g_emu, fade_time_ms);
	g_fade_time_ms = fade_time_ms;

    __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG, "[loadFileGme] Buffer size: %lu",
                        buffer_size);
	g_buffer_size = buffer_size;

	return;
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_readNextSamplesGme
	(JNIEnv * env, jclass clazz, jshortArray java_array)
{
    if (g_emu != NULL)
	{
		jboolean is_copy;
		jshort * target_array = env->GetShortArrayElements(java_array, &is_copy);

        if (target_array != NULL)
		{
			g_last_error = gme_play(g_emu, g_buffer_size, target_array);
			env->ReleaseShortArrayElements(java_array, target_array, 0);
        } else
		{
			g_last_error = "Couldn't write to Java buffer.";
		}
	}
	else
	{
		g_last_error = "Emulator not ready.";
	}
}

JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getMillisPlayedGme
  (JNIEnv * env, jclass clazz)
{
    if (g_emu != NULL)
	{
		return gme_tell(g_emu);
    } else
	{
		return -1;
	}
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_seekNativeGme
  (JNIEnv * env, jclass clazz, jint time_in_ms)
{
	gme_err_t seek_error;

    if (g_emu != NULL)
	{
		seek_error = gme_seek(g_emu, time_in_ms);
		gme_set_fade(g_emu, g_fade_time_ms);
    } else
	{
		seek_error = "Emulator is null.";
	}

	// C style string to Java String
    jstring result = env->NewStringUTF(seek_error);
	return result;
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_setTempoNativeGme
  (JNIEnv * env, jclass clazz, jdouble tempo)
{
    if (g_emu != NULL)
	{
        gme_set_tempo(g_emu, tempo);
		gme_set_fade(g_emu, 1000000000);
	}
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_muteVoiceNativeGme
  (JNIEnv *, jobject, jint voice_number, jint enabled)
{
    if (g_emu != NULL)
	{
		gme_mute_voice(g_emu, voice_number, enabled);
	}
}

JNIEXPORT jboolean JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_isTrackOverGme
	(JNIEnv * env, jclass clazz)
{
	if (g_emu != NULL)
	{
		if (gme_track_ended(g_emu))
		{
            return true;
		}
	}

	return false;
}

JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_teardownGme
	(JNIEnv * env, jclass clazz)
{
	g_fade_time_ms = 0;

	if (g_emu != NULL)
	{
        __android_log_print(ANDROID_LOG_VERBOSE, CHIPBOX_TAG,
                            "[teardownGme] Deleting emulator instance.");
		delete g_emu;
		g_emu = NULL;
	}
}

JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getLastErrorGme
	(JNIEnv * env, jclass clazz)
{
	jstring str = env->NewStringUTF(g_last_error);
	return str;
}

JNIEXPORT jint JNICALL
Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getVoiceCountNativeGme
  (JNIEnv * env, jclass clazz)
{
	if (g_emu != NULL)
	{
		jint result = gme_voice_count(g_emu);
		return result;
	}
	else
	{
		return 0;
	}
}

JNIEXPORT jstring JNICALL
Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getVoiceNameNativeGme
  (JNIEnv * env, jclass clazz, jint voice_number)
{
	const char* voice_name;

	if (g_emu != NULL)
	{
		voice_name = gme_voice_name(g_emu, voice_number);
	}
	else
	{
		voice_name = "Error";
	}

	return env->NewStringUTF(voice_name);
}


JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_muteVoiceNativeGme
  (JNIEnv * env, jclass clazz, jint voice_number, jint enabled)
{
	if (g_emu != NULL)
	{
		gme_mute_voice(g_emu, voice_number, enabled);
	}
}

