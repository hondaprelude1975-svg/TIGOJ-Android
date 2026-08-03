package com.tigoj.aipro.search

import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

data class WebResult(
    val title: String,
    val url: String,
    val snippet: String
)

object WebSearch {

    private const val SEARCH_TIMEOUT = 8_000
    private const val PAGE_TIMEOUT = 6_000

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16) " +
        "AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"

    fun search(query: String, limit: Int = 5): List<WebResult> {
        if (query.isBlank()) return emptyList()

        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")

        val searchUrls = listOf(
            "https://html.duckduckgo.com/html/?q=$encodedQuery",
            "https://lite.duckduckgo.com/lite/?q=$encodedQuery"
        )

        for (searchUrl in searchUrls) {
            val resultados = try {
                searchDuckDuckGo(searchUrl)
            } catch (_: Exception) {
                emptyList()
            }

            if (resultados.isNotEmpty()) {
                return ordenarResultados(resultados, limit)
            }
        }

        return emptyList()
    }

    private fun searchDuckDuckGo(searchUrl: String): List<WebResult> {
        val document = Jsoup.connect(searchUrl)
            .userAgent(USER_AGENT)
            .referrer("https://duckduckgo.com/")
            .timeout(SEARCH_TIMEOUT)
            .followRedirects(true)
            .get()

        return document
            .select("a.result__a, a.result-link")
            .mapNotNull { link ->
                val title = link.text().trim()

                val href = link.absUrl("href").ifBlank {
                    link.attr("href").trim()
                }

                val url = limpiarUrlDuckDuckGo(href)

                val contenedor = link.closest(".result")
                    ?: link.parent()?.parent()

                val snippet = contenedor
                    ?.selectFirst(".result__snippet, .result-snippet")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                if (
                    title.isBlank() ||
                    url.isBlank() ||
                    !url.startsWith("http")
                ) {
                    null
                } else {
                    WebResult(
                        title = title,
                        url = url,
                        snippet = snippet
                    )
                }
            }
            .distinctBy { it.url }
    }

    private fun limpiarUrlDuckDuckGo(href: String): String {
        return try {
            val parametro = Regex("[?&]uddg=([^&]+)")
                .find(href)
                ?.groupValues
                ?.getOrNull(1)

            if (parametro != null) {
                URLDecoder.decode(parametro, "UTF-8")
            } else {
                href
            }
        } catch (_: Exception) {
            href
        }
    }

    private fun ordenarResultados(
        resultados: List<WebResult>,
        limit: Int
    ): List<WebResult> {
        val dominiosFiables = listOf(
            "android.com",
            "developer.android.com",
            "google.com",
            "seg-social.es",
            "administracion.gob.es",
            "boe.es",
            "europa.eu",
            "wikipedia.org",
            "atptour.com",
            "wtatennis.com",
            "itftennis.com",
            "reuters.com",
            "bbc.com",
            "elpais.com",
            ".gov",
            ".gob.",
            ".edu"
        )

        return resultados
            .sortedByDescending { resultado ->
                dominiosFiables.any {
                    resultado.url.lowercase().contains(it)
                }
            }
            .take(limit)
    }

    fun readPage(
        url: String,
        maxCharacters: Int = 5_000
    ): String {
        if (!url.startsWith("http")) return ""

        val document = Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer("https://www.google.com/")
            .timeout(PAGE_TIMEOUT)
            .followRedirects(true)
            .ignoreHttpErrors(true)
            .get()

        document.select(
            "script, style, nav, footer, header, aside, " +
            "form, noscript, iframe, svg"
        ).remove()

        return document.body()
            ?.text()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(maxCharacters)
            .orEmpty()
    }
}
