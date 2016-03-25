package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.*
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
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
    var tracks: List<Track>? = null

    fun getTracks(): List<Track> {
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

        fun get(artistId: Long): Observable<Artist> {
            return Observable.create {
                logInfo("[Artist] Getting artist #${artistId}...")

                if (artistId > 0) {
                    val artist = queryDatabase(artistId)

                    if (artist != null) {
                        it.onNext(artist)
                        it.onCompleted()
                    } else {
                        it.onError(Exception("Couldn't find game."))
                    }
                } else {
                    it.onError(Exception("Bad game ID."))
                }
            }
        }

        fun getAll(): Observable<List<Artist>> {
            return Observable.create {
                logInfo("[Artist] Reading artist list...")

                val artists = SQLite.select().from(Artist::class.java)
                        .where()
                        .orderBy(Artist_Table.name, true)
                        .queryList()

                logVerbose("[Artist] Found ${artists.size} artists.")

                it.onNext(artists)
                it.onCompleted()
            }
        }

        fun queryDatabase(id: Long): Artist? {
            return SQLite.select()
                    .from(Artist::class.java)
                    .where(Artist_Table.id.eq(id))
                    .querySingle()
        }

        fun get(name: String, artistMap: HashMap<String, Artist>): Artist {
            // Check if this artist has already been seen during this scan.
            artistMap.get(name)?.let {
                logVerbose("[Artist] Found cached artist ${it.name} with id ${it.id}")
                return it
            }

            val artist = SQLite.select()
                    .from(Artist::class.java)
                    .indexedBy(Artist_Table.index_name)
                    .where(Artist_Table.name.eq(name))
                    .querySingle()

            artist?.id?.let {
                artistMap.put(name, artist)
                return artist
            } ?: let {
                return addToDatabase(name ?: "Unknown Artist")
            }
        }

        private fun addToDatabase(name: String): Artist {
            val artist = Artist(name)
            artist.insert()

            return artist
        }
    }
}