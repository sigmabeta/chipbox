package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.*
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import java.util.*

@ModelContainer
@ManyToMany(referencedTable = Track::class)
@Table(database = ChipboxDatabase::class, allFields = true, indexGroups = arrayOf(IndexGroup(number = 1, name = "name")))
class Artist() : BaseModel() {
    constructor(name: String) : this() {
        this.name = name
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null

    @Index(indexGroups = intArrayOf(1)) var name: String? = null

    @ColumnIgnore
    @JvmField
    var tracks: MutableList<Track>? = null

    fun getTracks(): MutableList<Track> {
        this.tracks?.let {
            if (!it.isEmpty()) {
                return it
            }
        }

        val relations = SQLite.select()
                .from(Artist_Track::class.java)
                .where(Artist_Track_Table.artist_id.eq(id))
                .queryList()

        val tracks = ArrayList<Track>(relations.size)

        relations.forEach {
            tracks.add(it.track)
        }

        this.tracks = tracks
        return tracks
    }

    companion object {
        val ARTIST_ALL = -1L
    }
}