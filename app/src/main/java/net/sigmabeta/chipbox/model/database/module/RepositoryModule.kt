package net.sigmabeta.chipbox.model.database.module

import android.content.Context
import dagger.Module
import dagger.Provides
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.logVerbose

@Module
class RepositoryModule() {
    @Provides fun provideRepository(context: Context): Repository {
        logVerbose("[RepositoryModule] Providing Repository...")
        return RealmRepository(context)
    }
}

