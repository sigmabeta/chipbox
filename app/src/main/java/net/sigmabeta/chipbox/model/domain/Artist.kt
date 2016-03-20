package net.sigmabeta.chipbox.model.domain

import com.raizlabs.android.dbflow.annotation.PrimaryKey
import com.raizlabs.android.dbflow.annotation.Table
import com.raizlabs.android.dbflow.sql.language.SQLite
import com.raizlabs.android.dbflow.structure.BaseModel
import net.sigmabeta.chipbox.ChipboxDatabase
import net.sigmabeta.chipbox.util.logError
import net.sigmabeta.chipbox.util.logInfo
import net.sigmabeta.chipbox.util.logVerbose
import rx.Observable
import java.util.*

@Table(database = ChipboxDatabase::class, allFields = true)
class Artist() : BaseModel() {
    constructor(name: String) : this() {
        this.name = name
    }

    @PrimaryKey (autoincrement = true) var id: Long? = null
    var name: String? = null

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

        fun getId(name: String?, artistMap: HashMap<Long, Artist>): Long {
            // Check if this artist has already been seen during this scan.
            artistMap.keys.forEach {
                val currentArtist = artistMap.get(it)
                if (currentArtist?.name == name) {
                    currentArtist?.id?.let {
                        logVerbose("[Artist] Found cached artist $name with id ${it}")
                        return it
                    }
                }
            }

            val artist = SQLite.select()
                    .from(Artist::class.java)
                    .where(Artist_Table.name.eq(name))
                    .querySingle()

            artist?.id?.let {
                artistMap.put(it, artist)
                return it
            } ?: let {
                val newArtist = addToDatabase(name ?: "Unknown Artist")
                newArtist.id?.let {
                    return it
                }
            }

            logError("[Artist] Unable to find artist ID.")
            return -1L
        }

        private fun addToDatabase(name: String): Artist {
            val artist = Artist(name)
            artist.insert()

            return artist
        }
    }
}