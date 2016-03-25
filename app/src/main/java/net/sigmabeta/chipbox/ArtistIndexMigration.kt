package net.sigmabeta.chipbox

import com.raizlabs.android.dbflow.annotation.Migration
import com.raizlabs.android.dbflow.sql.migration.IndexMigration
import net.sigmabeta.chipbox.model.domain.Artist
import net.sigmabeta.chipbox.model.domain.Artist_Table

@Migration(version = 0, database = ChipboxDatabase::class)
class ArtistIndexMigration : IndexMigration<Artist>("name", Artist::class.java) {
    override fun onPreMigrate() {
        super.onPreMigrate()
        addColumn(Artist_Table.name)
    }
}