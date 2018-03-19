package net.sigmabeta.chipbox.ui.game

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import net.sigmabeta.chipbox.R
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Track
import net.sigmabeta.chipbox.ui.BaseArrayAdapter
import net.sigmabeta.chipbox.ui.ListView
import timber.log.Timber

class GameTrackListAdapter(view: ListView<Track ,GameTrackViewHolder>) : BaseArrayAdapter<Track, GameTrackViewHolder>(view) {
    var game: Game? = null
        set (value) {
            field = value
            if (value != null) {
                notifyItemChanged(0)
            }
        }

    var playingTrackId: String? = null
        set (value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getLayoutId() = R.layout.list_item_track_game

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): GameTrackViewHolder? {
        if (viewType == TYPE_HEADER) {
            val headerView = LayoutInflater.from(parent?.context)?.inflate(getHeaderLayoutId(), parent, false)

            if (headerView != null) {
                return createHeaderViewHolder(headerView)
            } else {
                Timber.e("Unable to inflate view...")
                return null
            }
        } else {
            val itemView = LayoutInflater.from(parent?.context)?.inflate(getLayoutId(), parent, false)

            if (itemView != null) {
                return createViewHolder(itemView)
            } else {
                Timber.e("Unable to inflate view...")
                return null
            }
        }
    }

    override fun onBindViewHolder(holder: GameTrackViewHolder, position: Int) {
        getItem(position)?.let {
            bind(holder, it)
        } ?: let {
            if (position == 0 && holder is GameHeaderViewHolder) {
                holder.bind()
            }
        }
    }

    override fun getItemCount(): Int {
        return (datasetInternal?.size ?: 0) + 1
    }

    override fun getItem(position: Int): Track? {
        if (position > 0) {
            return datasetInternal?.get(position - 1)
        } else {
            return null
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            TYPE_HEADER
        } else {
            TYPE_ITEM
        }
    }

    fun createHeaderViewHolder(view: View): GameTrackViewHolder? {
        return GameHeaderViewHolder(view, this)
    }

    fun getHeaderLayoutId(): Int = R.layout.list_header_game


    override fun createViewHolder(view: View): GameTrackViewHolder {
        return GameTrackViewHolder(view, this)
    }

    override fun bind(holder: GameTrackViewHolder, item: Track) {
        holder.bind(item)
    }

    override fun showFromEmptyList(value: List<Track>) {
        datasetInternal = value
        notifyDataSetChanged()
    }

    companion object {
        val TYPE_HEADER = 0
        val TYPE_ITEM = 1
    }

}