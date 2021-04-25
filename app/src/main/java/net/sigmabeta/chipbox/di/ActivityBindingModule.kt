package net.sigmabeta.chipbox.di

import com.vgleadsheets.di.ActivityScope
import dagger.Module
import dagger.android.ContributesAndroidInjector
import net.sigmabeta.chipbox.activities.RemasterActivity

@Module
@Suppress("TooManyFunctions")
internal abstract class ActivityBindingModule {
    @ActivityScope
    @ContributesAndroidInjector
    internal abstract fun contributeMainActivityInjector(): RemasterActivity
}