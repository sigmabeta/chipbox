package net.sigmabeta.chipbox.model.database.module

import android.content.Context
import dagger.Module
import dagger.Provides
import io.realm.Realm
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.logVerbose

@Module
class RepositoryModule() {
    @Provides fun provideRealm(): Realm {
        return Realm.getDefaultInstance()
    }

    @Provides fun provideRepository(context: Context, realm: Realm): Repository {
        logVerbose("[RepositoryModule] Providing Repository...")
        return RealmRepository(context)
    }
}

