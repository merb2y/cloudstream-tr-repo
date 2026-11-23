package com.keyiflerolsun

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URLEncoder

class FilmEkseni : MainAPI() {
    override var mainUrl = "https://filmekseni.pro"
    override var name = "FilmEkseni"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "$mainUrl/filmler/?page="                  to "Son Eklenenler",
        "$mainUrl/en-cok-izlenenler/?page="         to "En Çok İzlenenler",
        "$mainUrl/tur/aksiyon-filmleri/?page="      to "Aksiyon",
        "$mainUrl/tur/bilim-kurgu-filmleri/?page="  to "Bilim Kurgu",
        "$mainUrl/tur/dram-filmleri/?page="         to "Dram",
        "$mainUrl/tur/fantastik-filmler/?page="     to "Fantastik",
        "$mainUrl/tur/gerilim-filmleri/?page="      to "Gerilim",
        "$mainUrl/tur/komedi-filmleri/?page="       to "Komedi",
        "$mainUrl/tur/korku-filmleri/?page="        to "Korku",
        "$mainUrl/tur/macera-filmleri/?page="       to "Macera",
        "$mainUrl/tur/suc-filmleri/?page="          to "Suç",
        "$mainUrl/tur/animasyon-filmleri/?page="    to "Animasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}$page", headers = mapOf("User-Agent" to ua)).document
        val items = doc.select("a[class*='poster']").mapNotNull { it.toCardResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toCardResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (href.contains("/tur/") || href.contains("/kesfet")) return null
        val img = selectFirst("img")
        val title = img?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
            ?: selectFirst("h3, h2, span.font-medium")?.text()?.trim()
            ?: return null
        val poster = fixUrlNull(img?.attr("src")?.ifBlank { img.attr("data-src") })
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    data class SearchResp(
        @JsonProperty("data") val data: List<SearchItem>? = null
    )

    data class SearchItem(
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("posterUrl") val posterUrl: String? = null,
        @JsonProperty("releaseYear") val releaseYear: String? = null,
        @JsonProperty("contentableType") val contentableType: String? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val resp = app.get(
            "$mainUrl/api/search?q=$encoded",
            headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/")
        ).parsedSafe<SearchResp>() ?: return emptyList()
        return resp.data.orEmpty().mapNotNull { item ->
            val slug = item.slug?.trim()?.trimStart('/') ?: return@mapNotNull null
            val title = item.title?.replace(Regex("""\s+izle$""", RegexOption.IGNORE_CASE), "")?.trim() ?: return@mapNotNull null
            newMovieSearchResponse(title, "$mainUrl/$slug/", TvType.Movie) {
                this.posterUrl = fixUrlNull(item.posterUrl)
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to ua)).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("img[src*='/posters/']")?.attr("src")
        )
        val plot = doc.selectFirst("meta[name='description']")?.attr("content")?.trim()
        val tags = doc.select("a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val year = Regex("""\((\d{4})\)""").find(plot.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data, headers = mapOf("User-Agent" to ua)).text
        val rawMatch = Regex("""videoPlayerData\(JSON\.parse\('([^']+)'\)""").find(html)?.groupValues?.get(1)
            ?: return false
        val jsonStr = rawMatch
            .replace("""\u0022""", "\"")
            .replace("""\/""", "/")
            .replace("""\\""", "\\")

        val root = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return false
        val requests = mutableListOf<Triple<String, String, String>>()

        root.keys().forEach { langKey ->
            val arr = root.optJSONArray(langKey) ?: return@forEach
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val link = obj.optString("link").trim()
                if (link.isBlank()) continue
                val label = obj.optString("service_name", langKey).ifBlank { langKey }
                val langTitle = when (langKey.lowercase()) {
                    "dual" -> "TR Dublaj & Altyazı ($label)"
                    "dublaj" -> "Türkçe Dublaj ($label)"
                    "altyazili", "altyazi" -> "Türkçe Altyazı ($label)"
                    else -> "$langKey ($label)"
                }
                val playerUrl = if (link.startsWith("http")) link else "https://eksenload.top/eplayer/$link"
                requests.add(Triple(langTitle, playerUrl, link))
            }
        }

        var found = false
        requests.amap { (label, playerUrl, _) ->
            runCatching {
                val pHtml = app.get(
                    playerUrl,
                    headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/")
                ).text
                val cfgRaw = Regex("""JSON\.parse\('([^']+)'\)""").find(pHtml)?.groupValues?.get(1) ?: return@runCatching
                val cfgStr = cfgRaw
                    .replace("""\u0022""", "\"")
                    .replace("""\/""", "/")
                    .replace("""\\""", "\\")
                val cfg = JSONObject(cfgStr)
                val fileUrl = cfg.optString("file").replace("""\/""", "/").trim()
                if (fileUrl.isNotBlank()) {
                    val tracks = cfg.optJSONArray("tracks")
                    if (tracks != null) {
                        for (t in 0 until tracks.length()) {
                            val tr = tracks.optJSONObject(t) ?: continue
                            val subUrl = tr.optString("file").replace("""\/""", "/").trim()
                            val subLabel = tr.optString("label", "Türkçe").ifBlank { "Türkçe" }
                            if (subUrl.isNotBlank()) {
                                subtitleCallback.invoke(newSubtitleFile(subLabel, subUrl))
                            }
                        }
                    }
                    callback.invoke(
                        newExtractorLink(
                            name,
                            "$name - $label",
                            fileUrl,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://eksenload.top/"
                            this.headers = mapOf(
                                "User-Agent" to ua,
                                "Referer"    to "https://eksenload.top/",
                                "Origin"     to "https://eksenload.top"
                            )
                            this.quality = Qualities.P1080.value
                        }
                    )
                    found = true
                }
            }
        }
        return found
    }
}
