package net.sigmabeta.chipbox.dagger.module

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import timber.log.Timber
import javax.inject.Singleton

@Module
class PreferenceModule() {
    @Provides @Singleton fun provideSharedPreferences(context: Context): SharedPreferences {
        Timber.v("Providing Shared Preferences...")
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}