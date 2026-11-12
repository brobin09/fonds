package com.brianrobinson.fonds.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CollectionEntity::class,
        BoxEntity::class,
        SeriesEntity::class,
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class FondsDatabase : RoomDatabase() {
    abstract fun dao(): FondsDao

    companion object {
        /** v2: single items (collections.kind) and where a single item came from. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN kind TEXT NOT NULL DEFAULT 'collection'")
                db.execSQL("ALTER TABLE documents ADD COLUMN sourceRepository TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN sourceCollection TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN sourceLocation TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): FondsDatabase =
            Room.databaseBuilder(context, FondsDatabase::class.java, "fonds.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
