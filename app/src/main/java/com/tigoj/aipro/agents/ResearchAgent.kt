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

        var results = WebSearch.search(query, limit = maxSources)

        if (results.isEmpty()) {
            val simplifiedQuery = query
                .lowercase()
                .replace(
                    Regex(
                        "\\b(investiga|investigar|busca|buscar|buscame|búscame|en internet|por internet|dime|quiero saber|puedes decirme|por favor|sobre|acerca de)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    " "
                )
                .replace(Regex("[¿?¡!.,;:]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (simplifiedQuery.isNotBlank() && simplifiedQuery != query.lowercase().trim()) {
                results = WebSearch.search(simplifiedQuery, limit = maxSources)
            }
        }

        if (results.isEmpty()) {
            return ResearchReport(
                query = query,
                summarySource = "",
                sources = emptyList()
            )
        }

        val combinedText = results
            .mapNotNull { result ->

                val snippet = result.snippet.trim()

                try {
                    val pageText = WebSearch.readPage(
                        url = result.url,
                        maxCharacters = 1800
                    ).trim()

                    if (pageText.isBlank() && snippet.isBlank()) {
                        null
                    } else {
                        """
                        FUENTE: ${result.title}
                        URL: ${result.url}

                        RESUMEN DEL BUSCADOR:
                        ${if (snippet.isNotBlank()) snippet else "No disponible"}

                        CONTENIDO DE LA PÁGINA:
                        ${if (pageText.isNotBlank()) pageText else "No disponible"}
                        """.trimIndent()
                    }

                } catch (_: Exception) {

                    if (snippet.isBlank()) {
                        null
                    } else {
                        """
                        FUENTE: ${result.title}
                        URL: ${result.url}

                        RESUMEN DEL BUSCADOR:
                        $snippet
                        """.trimIndent()
                    }
                }
            }
            .joinToString("\n\n---\n\n")
            .take(7000)

        return ResearchReport(
            query = query,
            summarySource = combinedText,
            sources = results
        )
    }
}
