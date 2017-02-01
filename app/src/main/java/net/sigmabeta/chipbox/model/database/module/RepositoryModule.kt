package net.sigmabeta.chipbox.model.database.module

import android.content.Context
import dagger.Lazy
import dagger.Module
import dagger.Provides
import io.realm.Realm
import net.sigmabeta.chipbox.dagger.module.AppModule
import net.sigmabeta.chipbox.model.database.getRealmInstance
import net.sigmabeta.chipbox.model.repository.LibraryScanner
import net.sigmabeta.chipbox.model.repository.RealmRepository
import net.sigmabeta.chipbox.model.repository.Repository
import net.sigmabeta.chipbox.util.logVerbose
import javax.inject.Named
import javax.inject.Singleton

@Module
class RepositoryModule() {
    @Provides fun provideRealm(): Realm {
        return getRealmInstance()
    }

    @Provides fun provideRepository(context: Context, realm: Realm): Repository {
        logVerbose("[RepositoryModule] Providing Repository...")
        return RealmRepository(realm)
    }

    @Provides @Singleton fun provideScanner(repositoryLazy: Lazy<Repository>, @Named(AppModule.DEP_NAME_APP_STORAGE_DIR) externalFilesPath: String?): LibraryScanner {
        logVerbose("[RepositoryModule] Providing Library Scanner...")
        return LibraryScanner(repositoryLazy, externalFilesPath)
    }
}

