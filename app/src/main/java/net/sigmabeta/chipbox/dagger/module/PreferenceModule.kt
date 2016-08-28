package net.sigmabeta.chipbox.dagger.module

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Singleton

@Module
class PreferenceModule() {
    @Provides @Singleton fun provideSharedPreferences(context: Context): SharedPreferences {
        logVerbose("[AppModule] Providing Shared Preferences...")
        return PreferenceManager.getDefaultSharedPreferences(context)
    }
}