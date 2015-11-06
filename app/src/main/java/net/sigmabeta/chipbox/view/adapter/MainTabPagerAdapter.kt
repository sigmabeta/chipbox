package net.sigmabeta.chipbox.view.adapter

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.objects.Track
import net.sigmabeta.chipbox.view.fragment.ArtistListFragment
import net.sigmabeta.chipbox.view.fragment.GameGridFragment
import net.sigmabeta.chipbox.view.fragment.PlatformListFragment
import net.sigmabeta.chipbox.view.fragment.SongListFragment

class MainTabPagerAdapter(val fragManager: FragmentManager, val context: Context) : FragmentPagerAdapter(fragManager) {
    val TAB_TITLES = arrayOf(
            R.string.tab_main_system,
            R.string.tab_main_artist,
            R.string.tab_main_game,
            R.string.tab_main_songs
    )

    override fun getCount(): Int {
        return TAB_TITLES.size()
    }

    override fun getItem(position: Int): Fragment? {
        when (position) {
            0 -> return PlatformListFragment.newInstance()
            1 -> return ArtistListFragment.newInstance()
            2 -> return GameGridFragment.newInstance(Track.PLATFORM_ALL)
            3 -> return SongListFragment.newInstance(Track.PLATFORM_ALL.toLong())
            else -> return null
        }
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.getString(TAB_TITLES.get(position))
    }
}
