package net.sigmabeta.chipbox.backend

import android.content.SharedPreferences
import net.sigmabeta.chipbox.BuildConfig
import javax.inject.Inject

class PrefManager @Inject constructor(val preferences: SharedPreferences) {
    fun get(key: String): Boolean = preferences.getBoolean(key, false)

    fun set(key: String, value: Boolean) {
        val editor = preferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    companion object {
        private val TAG_PREF_KEY = BuildConfig.APPLICATION_ID + ".pref."
        val KEY_ONBOARDED = TAG_PREF_KEY + ".onboarded"
    }
}