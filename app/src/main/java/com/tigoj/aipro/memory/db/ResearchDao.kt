package com.tigoj.aipro.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: ResearchEntity)

    @Query("SELECT * FROM research_memory WHERE normalizedQuery = :query LIMIT 1")
    suspend fun load(query: String): ResearchEntity?

    @Query("DELETE FROM research_memory")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM research_memory")
    suspend fun count(): Int
}
