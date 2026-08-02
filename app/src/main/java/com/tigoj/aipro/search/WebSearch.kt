package com.tigoj.aipro.search

import org.jsoup.Jsoup
import java.net.URLEncoder

data class WebResult(
    val title: String,
    val url: String,
    val snippet: String
)

object WebSearch {

    fun search(query: String, limit: Int = 5): List<WebResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://html.duckduckgo.com/html/?q=$encodedQuery"

        val document = Jsoup.connect(searchUrl)
            .userAgent(
                "Mozilla/5.0 (Linux; Android 16) " +
                "AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
            )
            .timeout(15_000)
            .get()

        return document
            .select(".result")
            .mapNotNull { element ->
                val link = element.selectFirst(".result__a")
                    ?: return@mapNotNull null

                val title = link.text().trim()
                val url = link.absUrl("href").ifBlank {
                    link.attr("href").trim()
                }

                val snippet = element
                    .selectFirst(".result__snippet")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                if (title.isBlank() || url.isBlank()) {
                    null
                } else {
                    WebResult(
                        title = title,
                        url = url,
                        snippet = snippet
                    )
                }
            }
            .filter {
                val u = it.url.lowercase()

                u.contains("wikipedia.org") ||
                u.contains("atptour.com") ||
                u.contains("wtatennis.com") ||
                u.contains("itftennis.com") ||
                u.contains("reuters.com") ||
                u.contains("bbc.com") ||
                u.contains("elpais.com") ||
                u.contains(".gov") ||
                u.contains(".edu") ||
                u.contains(".org")
            }
            .take(limit)
    }

    fun readPage(url: String, maxCharacters: Int = 5_000): String {
        val document = Jsoup.connect(url)
            .userAgent(
                "Mozilla/5.0 (Linux; Android 16) " +
                "AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
            )
            .timeout(15_000)
            .get()

        document.select(
            "script, style, nav, footer, header, aside, form, noscript"
        ).remove()

        return document.body()
            .text()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxCharacters)
    }
}
