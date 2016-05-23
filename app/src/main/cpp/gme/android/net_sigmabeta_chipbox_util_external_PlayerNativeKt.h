/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class net_sigmabeta_chipbox_util_external_PlayerNativeKt */

#ifndef _Included_net_sigmabeta_chipbox_util_external_PlayerNativeKt
#define _Included_net_sigmabeta_chipbox_util_external_PlayerNativeKt
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    loadFile
 * Signature: (Ljava/lang/String;IIJ)V
 */
JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_loadFile
  (JNIEnv *, jclass, jstring, jint, jint, jlong, jint);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    readNextSamples
 * Signature: ([S)V
 */
JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_readNextSamples
  (JNIEnv *, jclass, jshortArray);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    getMillisPlayed
 * Signature: ()I
 */
JNIEXPORT jlong JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getMillisPlayed
  (JNIEnv *, jclass);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    seekNative
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_seekNative
  (JNIEnv *, jclass, jint);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    setTempoNative
 * Signature: (D)V
 */
JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_setTempoNative
  (JNIEnv *, jclass, jdouble);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    getVoiceCountNative
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getVoiceCountNative
  (JNIEnv *, jclass);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    getVoiceNameNative
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getVoiceNameNative
  (JNIEnv *, jclass, jint);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    muteVoiceNative
 * Signature: (II)V
 */
JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_muteVoiceNative
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    isTrackOver
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_isTrackOver
  (JNIEnv *, jclass);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    teardown
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_teardown
  (JNIEnv *, jclass);

/*
 * Class:     net_sigmabeta_chipbox_util_external_PlayerNativeKt
 * Method:    getLastError
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_net_sigmabeta_chipbox_util_external_PlayerNativeKt_getLastError
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif