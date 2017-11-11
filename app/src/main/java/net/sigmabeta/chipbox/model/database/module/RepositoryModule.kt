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
import timber.log.Timber
import javax.inject.Named
import javax.inject.Singleton

@Module
class RepositoryModule() {
    @Provides fun provideRealm(): Realm {
        return getRealmInstance()
    }

    @Provides fun provideRepository(context: Context, realm: Realm): Repository {
        Timber.v("Providing Repository...")
        return RealmRepository(realm)
    }

    @Provides @Singleton fun provideScanner(repositoryLazy: Lazy<Repository>, @Named(AppModule.DEP_NAME_APP_STORAGE_DIR) externalFilesPath: String?): LibraryScanner {
        Timber.v("Providing Library Scanner...")
        return LibraryScanner(repositoryLazy, externalFilesPath)
    }
}

