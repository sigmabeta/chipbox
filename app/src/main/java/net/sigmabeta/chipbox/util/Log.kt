package net.sigmabeta.chipbox.util

import android.util.Log
import net.sigmabeta.chipbox.BuildConfig

private val TAG = "ChipBox"

fun logVerbose(message: String) {
    if (BuildConfig.DEBUG) {
        Log.v(TAG, message)
    }
}

fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, message)
    }
}

fun logInfo(message: String) {
    Log.i(TAG, message)
}

fun logWarning(message: String) {
    Log.w(TAG, message)
}

fun logError(message: String) {
    Log.e(TAG, message)
}

fun logWtf(message: String) {
    Log.wtf(TAG, message)
}