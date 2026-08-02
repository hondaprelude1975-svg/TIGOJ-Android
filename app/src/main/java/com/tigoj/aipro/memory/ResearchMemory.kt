package com.tigoj.aipro.memory

data class MemoryEntry(
    val query: String,
    val answer: String,
    val timestamp: Long = System.currentTimeMillis()
)

object ResearchMemory {

    private val memory = mutableMapOf<String, MemoryEntry>()

    fun save(query: String, answer: String) {
        memory[query.lowercase().trim()] =
            MemoryEntry(query, answer)
    }

    fun load(query: String): MemoryEntry? {
        return memory[query.lowercase().trim()]
    }

    fun clear() {
        memory.clear()
    }

    fun size(): Int = memory.size
}
