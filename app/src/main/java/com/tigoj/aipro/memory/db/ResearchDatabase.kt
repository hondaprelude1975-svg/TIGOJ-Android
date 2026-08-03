package com.tigoj.aipro.memory.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ResearchEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ResearchDatabase : RoomDatabase() {

    abstract fun researchDao(): ResearchDao

    companion object {
        @Volatile
        private var INSTANCE: ResearchDatabase? = null

        fun getInstance(context: Context): ResearchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ResearchDatabase::class.java,
                    "research_memory.db"
                ).build().also {
                    INSTANCE = it
                }
            }
        }
    }
}
