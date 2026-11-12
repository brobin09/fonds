package com.brianrobinson.fonds.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CollectionEntity::class,
        BoxEntity::class,
        SeriesEntity::class,
        FolderEntity::class,
        DocumentEntity::class,
        PageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FondsDatabase : RoomDatabase() {
    abstract fun dao(): FondsDao

    companion object {
        fun build(context: Context): FondsDatabase =
            Room.databaseBuilder(context, FondsDatabase::class.java, "fonds.db").build()
    }
}
