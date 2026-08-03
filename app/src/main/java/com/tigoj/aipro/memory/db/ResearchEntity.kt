package com.tigoj.aipro.memory.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "research_memory")
data class ResearchEntity(
    @PrimaryKey
    val normalizedQuery: String,
    val originalQuery: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
)
