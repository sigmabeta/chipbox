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
// emulator/scanner gate is sourced from vgmstream itself. Used to pick this emulator (kept last
// in the provider list, so existing chiptune emulators win shared extensions) and to gate scans.
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

// Scan-time metadata probe. Returns one "sampleRate\tlengthMs\tstreamName" line per subsong
// (array length = subsong count), or an empty array if the file isn't recognised. Uses the SAME
// loop/fade config as playback so the reported length matches what VgmstreamEmulator produces.
JNIEXPORT jobjectArray JNICALL
Java_net_sigmabeta_chipbox_player_emulators_vgmstream_VgmstreamProbe_probeInternal(
        JNIEnv *env,
        jobject thiz,
        jstring java_filename
) {
    const int MAX_SUBSONGS = 4096; // guard against pathological banks (FSB/AWB with 1000s)

    jclass string_class = env->FindClass("java/lang/String");
    jobjectArray empty = env->NewObjectArray(0, string_class, nullptr);

    const char *path = env->GetStringUTFChars(java_filename, nullptr);
    libvgmstream_set_log(LIBVGMSTREAM_LOG_LEVEL_NONE, nullptr);

    libstreamfile_t *sf = libstreamfile_open_from_stdio(path);
    if (!sf) {
        env->ReleaseStringUTFChars(java_filename, path);
        return empty;
    }

    libvgmstream_t *lib = libvgmstream_init();
    libvgmstream_config_t cfg = {};
    cfg.loop_count = 2.0;
    cfg.fade_time = 10.0;
    cfg.auto_downmix_channels = 2;
    cfg.force_sfmt = LIBVGMSTREAM_SFMT_PCM16;
    libvgmstream_setup(lib, &cfg);

    if (!lib || libvgmstream_open_stream(lib, sf, 0) < 0) {
        if (lib) libvgmstream_free(lib);
        libstreamfile_close(sf);
        env->ReleaseStringUTFChars(java_filename, path);
        return empty;
    }

    int count = lib->format->subsong_count;
    if (count < 1) count = 1;
    if (count > MAX_SUBSONGS) count = MAX_SUBSONGS;

    jobjectArray result = env->NewObjectArray(count, string_class, nullptr);
    for (int n = 1; n <= count; ++n) {
        if (libvgmstream_open_stream(lib, sf, n) < 0) continue;
        const libvgmstream_format_t *f = lib->format;
        long long length_ms = f->sample_rate > 0
                ? (long long) (f->play_samples * 1000 / f->sample_rate)
                : 0;
        char buf[512];
        snprintf(buf, sizeof(buf), "%d\t%lld\t%s", f->sample_rate, length_ms, f->stream_name);
        jstring s = env->NewStringUTF(buf);
        env->SetObjectArrayElement(result, n - 1, s);
        env->DeleteLocalRef(s);
    }

    libvgmstream_free(lib);
    libstreamfile_close(sf);
    env->ReleaseStringUTFChars(java_filename, path);
    return result;
}

}
