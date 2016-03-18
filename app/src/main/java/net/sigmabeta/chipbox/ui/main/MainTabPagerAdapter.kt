package net.sigmabeta.chipbox.ui.main

import android.content.Context
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.TopLevelFragment
import net.sigmabeta.chipbox.ui.artist.ArtistListFragment
import net.sigmabeta.chipbox.ui.games.GameGridFragment
import net.sigmabeta.chipbox.ui.platform.PlatformListFragment
import net.sigmabeta.chipbox.ui.song.SongListFragment
import net.sigmabeta.chipbox.util.logError

class MainTabPagerAdapter(val fragManager: FragmentManager, val context: Context) : FragmentPagerAdapter(fragManager) {
    val TAB_TITLES = arrayOf(
            R.string.tab_main_system,
            R.string.tab_main_artist,
            R.string.tab_main_game,
            R.string.tab_main_songs
    )

    val fragments = Array<TopLevelFragment>(TAB_TITLES.size, {
        return@Array object: TopLevelFragment {
            override fun isScrolledToBottom(): Boolean {
                throw UnsupportedOperationException()
            }
        }
    })

    override fun getCount(): Int {
        return TAB_TITLES.size
    }

    override fun getItem(position: Int): Fragment? {
        when (position) {
            0 -> return PlatformListFragment.newInstance()
            1 -> return ArtistListFragment.newInstance()
            2 -> return GameGridFragment.newInstance(Track.PLATFORM_ALL)
            3 -> return SongListFragment.newInstance(Artist.ARTIST_ALL)
            else -> return null
        }
    }

    override fun instantiateItem(container: ViewGroup?, position: Int): Any? {
        val fragment = super.instantiateItem(container, position) as Fragment

        if (fragment is TopLevelFragment) {
            fragments.set(position, fragment)
        } else {
            logError("[MainTabPagerAdapter] Invalid fragment at position ${position}")
        }

        return fragment
    }

    override fun getPageTitle(position: Int): CharSequence? {
        return context.getString(TAB_TITLES.get(position))
    }
}
