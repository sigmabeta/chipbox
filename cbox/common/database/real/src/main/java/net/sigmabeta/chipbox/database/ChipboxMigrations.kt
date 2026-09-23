package net.sigmabeta.chipbox.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v11 -> v12: add per-track scanner/reader versions. The scanner re-reads a folder when any stored
 * track was produced by older logic, so this must preserve existing rows (defaulting them to 0, a
 * value no current version uses) instead of letting `fallbackToDestructiveMigration` wipe the
 * library. Room compares column defaults during post-migration validation, so these `DEFAULT 0`
 * clauses must match `TrackEntity`'s `@ColumnInfo(defaultValue = "0")`.
 *
 * `migrate(SQLiteConnection)` is used on every target: the JVM/CLI/server builders configure the
 * bundled SQLite driver, and Android's framework path wraps its `SupportSQLiteDatabase` in a
 * `SupportSQLiteConnection` before dispatching to this overload.
 */
val MIGRATION_11_12 = object : Migration(SCHEMA_V11, SCHEMA_V12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE track ADD COLUMN scanner_version INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE track ADD COLUMN reader_version INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private const val SCHEMA_V11 = 11
private const val SCHEMA_V12 = 12
