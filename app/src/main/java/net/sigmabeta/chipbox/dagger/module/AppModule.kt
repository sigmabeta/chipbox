package net.sigmabeta.chipbox.dagger.module

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Singleton

@Module
class AppModule(val application: Application) {
    @Provides @Singleton fun provideSharedPreferences(): SharedPreferences {
        logVerbose("[AppModule] Providing Shared Preferences...")
        return PreferenceManager.getDefaultSharedPreferences(application)
    }

    @Provides @Singleton fun provideContext(): Context {
        logVerbose("[AppModule] Providing Context...")
        return application
    }

    @Provides @Singleton fun provideAudioService(context: Context): AudioManager {
        logVerbose("[AppModule] Providing Audio Manager...")

        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
}

