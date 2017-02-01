package net.sigmabeta.chipbox.dagger.module

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Environment
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.BuildConfig
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Named
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

    @Provides @Singleton @Named(DEP_NAME_APP_STORAGE_DIR) fun provideAppStorageDir(context: Context): String? {
        logVerbose("[AppModule] Providing files path.")
        val appStorageDir = context.filesDir
        return appStorageDir?.absolutePath
    }

    @Provides @Singleton @Named(Companion.DEP_NAME_BROWSER_START) fun provideBrowserStartPath(): String {
        logVerbose("[AppModule] Providing browser start path.")
        return Environment.getExternalStorageDirectory().absolutePath
    }

    companion object {
        const val DEP_NAME_APP_STORAGE_DIR = "${BuildConfig.APPLICATION_ID}.dependency.storage_dir"
        const val DEP_NAME_BROWSER_START = "${BuildConfig.APPLICATION_ID}.dependency.browser_start"
    }
}

