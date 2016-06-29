package net.sigmabeta.chipbox.dagger.module

import android.app.Application
import android.content.Context
import android.media.AudioManager
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Singleton

@Module
class AppModule(val application: Application) {
    @Provides @Singleton fun provideContext(): Context {
        logVerbose("[AppModule] Providing Context...")
        return application
    }

    @Provides @Singleton fun provideAudioService(context: Context): AudioManager {
        logVerbose("[AppModule] Providing Audio Manager...")

        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
}

