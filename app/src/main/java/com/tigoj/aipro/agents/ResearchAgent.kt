package com.tigoj.aipro.agents

import com.tigoj.aipro.search.WebResult
import com.tigoj.aipro.search.WebSearch

data class ResearchReport(
    val query: String,
    val summarySource: String,
    val sources: List<WebResult>
)

object ResearchAgent {

    fun investigate(query: String, maxSources: Int = 3): ResearchReport {
        val results = WebSearch.search(query, limit = maxSources)

        if (results.isEmpty()) {
            return ResearchReport(
                query = query,
                summarySource = "",
                sources = emptyList()
            )
        }

        val combinedText = results
            .mapNotNull { result ->
                try {
                    val pageText = WebSearch.readPage(
                        url = result.url,
                        maxCharacters = 3500
                    )

                    if (pageText.isBlank()) {
                        null
                    } else {
                        """
                        FUENTE: ${result.title}
                        URL: ${result.url}
                        CONTENIDO:
                        $pageText
                        """.trimIndent()
                    }
                } catch (_: Exception) {
                    if (result.snippet.isBlank()) {
                        null
                    } else {
                        """
                        FUENTE: ${result.title}
                        URL: ${result.url}
                        RESUMEN:
                        ${result.snippet}
                        """.trimIndent()
                    }
                }
            }
            .joinToString("\n\n---\n\n")
            .take(10_000)

        return ResearchReport(
            query = query,
            summarySource = combinedText,
            sources = results
        )
    }
}
