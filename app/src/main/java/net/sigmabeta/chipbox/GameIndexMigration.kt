package net.sigmabeta.chipbox

import com.raizlabs.android.dbflow.annotation.Migration
import com.raizlabs.android.dbflow.sql.migration.IndexMigration
import net.sigmabeta.chipbox.model.domain.Game
import net.sigmabeta.chipbox.model.domain.Game_Table

@Migration(version = 0, database = ChipboxDatabase::class)
class GameIndexMigration : IndexMigration<Game>("titlePlatform", Game::class.java) {
    override fun onPreMigrate() {
        super.onPreMigrate()
        addColumn(Game_Table.title)
        addColumn(Game_Table.platform)
    }
}