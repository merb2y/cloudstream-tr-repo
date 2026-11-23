package com.keyiflerolsun

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class HDFilmDelisi : MainAPI() {
    override var mainUrl = "https://hdfilmdelisi1.com"
    override var name = "HDFilmDelisi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "$mainUrl/film-kesfet?sayfa="       to "Son Filmler",
        "$mainUrl/diziler?sayfa="           to "Yabancı Diziler",
        "$mainUrl/imdb-top-250?sayfa="      to "IMDb Top 250",
        "$mainUrl/tur/aksiyon?sayfa="       to "Aksiyon",
        "$mainUrl/tur/bilim-kurgu?sayfa="   to "Bilim Kurgu",
        "$mainUrl/tur/dram?sayfa="          to "Dram",
        "$mainUrl/tur/fantastik?sayfa="     to "Fantastik",
        "$mainUrl/tur/gerilim?sayfa="       to "Gerilim",
        "$mainUrl/tur/gizem?sayfa="         to "Gizem",
        "$mainUrl/tur/komedi?sayfa="        to "Komedi",
        "$mainUrl/tur/korku?sayfa="         to "Korku",
        "$mainUrl/tur/macera?sayfa="        to "Macera",
        "$mainUrl/tur/suc?sayfa="           to "Suç"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}$page", headers = mapOf("User-Agent" to ua)).document
        val items = doc.select("a.poster").mapNotNull { it.toCardResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toCardResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("/film/") && !href.contains("/dizi/")) return null
        val cleanHref = if (href.contains("/sezon-")) href.substringBefore("/sezon-") else href
        val img = selectFirst("img")
        val rawTitle = attr("title").ifBlank { img?.attr("alt").orEmpty() }
            .replace(Regex("""\s+izle$""", RegexOption.IGNORE_CASE), "")
            .trim()
        if (rawTitle.isBlank()) return null
        val poster = fixUrlNull(img?.attr("src")?.ifBlank { img.attr("data-src") })
        return if (cleanHref.contains("/dizi/")) {
            newTvSeriesSearchResponse(rawTitle, cleanHref, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(rawTitle, cleanHref, TvType.Movie) { this.posterUrl = poster }
        }
    }

    data class HdfdSearchResp(
        @JsonProperty("results") val results: List<HdfdSearchItem>? = null
    )

    data class HdfdSearchItem(
        @JsonProperty("slug") val slug: String? = null,
        @JsonProperty("baslik") val baslik: String? = null,
        @JsonProperty("afis") val afis: String? = null,
        @JsonProperty("tip") val tip: String? = null,
        @JsonProperty("yayinYili") val yayinYili: Int? = null
    )

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val resp = app.get(
            "$mainUrl/api/search?q=$encoded",
            headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/")
        ).parsedSafe<HdfdSearchResp>() ?: return emptyList()
        return resp.results.orEmpty().mapNotNull { item ->
            val slug = item.slug?.trim()?.trimStart('/') ?: return@mapNotNull null
            val title = item.baslik?.trim() ?: return@mapNotNull null
            val isSeries = item.tip.equals("dizi", true) || item.tip.equals("series", true)
            val href = if (isSeries) "$mainUrl/dizi/$slug" else "$mainUrl/film/$slug"
            if (isSeries) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = fixUrlNull(item.afis)
                    this.year = item.yayinYili
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = fixUrlNull(item.afis)
                    this.year = item.yayinYili
                }
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to ua)).document
        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: return null
        val title = rawTitle.replace(Regex("""\s+izle.*$""", RegexOption.IGNORE_CASE), "").trim()
        val poster = fixUrlNull(
            doc.selectFirst("meta[property='og:image']")?.attr("content")
                ?: doc.selectFirst("img[src*='/posters/']")?.attr("src")
        )
        val plot = doc.selectFirst("meta[name='description']")?.attr("content")?.trim()
        val tags = doc.select("a[href*='/tur/']").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()

        if (url.contains("/dizi/")) {
            val epRegex = Regex("""(/dizi/[^"'\s]+/sezon-(\d+)/bolum-(\d+))""")
            val episodes = epRegex.findAll(doc.html())
                .distinctBy { it.groupValues[1] }
                .map { m ->
                    val epUrl = fixUrl(m.groupValues[1])
                    val s = m.groupValues[2].toIntOrNull()
                    val e = m.groupValues[3].toIntOrNull()
                    newEpisode(epUrl) {
                        this.name = "${e ?: 1}. Bölüm"
                        this.season = s
                        this.episode = e
                    }
                }
                .toList()
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
        }
    }

    private fun decodeVixolity(html: String): String? {
        val match = Regex("""\}\)\('([0-9a-fA-F]+)',\s*(\d+)\)\s*;""").find(html) ?: return null
        val hex = match.groupValues[1]
        val shift = match.groupValues[2].toIntOrNull() ?: return null
        val sb = StringBuilder(hex.length / 2)
        var i = 0
        while (i + 1 < hex.length) {
            sb.append(hex.substring(i, i + 2).toInt(16).toChar())
            i += 2
        }
        val b64Layer1 = sb.reverse().toString()
        val shifted = String(Base64.decode(b64Layer1, Base64.DEFAULT), Charsets.ISO_8859_1)
        val unshifted = StringBuilder(shifted.length)
        for (ch in shifted) {
            unshifted.append((ch.code - shift).toChar())
        }
        val uriDecoded = String(Base64.decode(unshifted.toString(), Base64.DEFAULT), Charsets.ISO_8859_1)
        val decodedJs = URLDecoder.decode(uriDecoded, "UTF-8")
        return Regex(""""kaynak"\s*:\s*"([^"]+)"""").find(decodedJs)?.groupValues?.get(1)
            ?.replace("\\/", "/")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data, headers = mapOf("User-Agent" to ua)).text
        val vixUrls = Regex("""https?://pl\.vixolity\.com/[A-Za-z0-9_=-]+""")
            .findAll(html)
            .map { it.value }
            .distinct()
            .toList()

        var found = false
        vixUrls.amap { vixUrl ->
            runCatching {
                val vixHtml = app.get(
                    vixUrl,
                    headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/")
                ).text
                val streamUrl = decodeVixolity(vixHtml) ?: return@runCatching
                callback.invoke(
                    newExtractorLink(
                        name,
                        "$name - Delicore VIP (1080p)",
                        streamUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://pl.vixolity.com/"
                        this.headers = mapOf(
                            "User-Agent" to ua,
                            "Referer"    to "https://pl.vixolity.com/",
                            "Origin"     to "https://pl.vixolity.com"
                        )
                        this.quality = Qualities.P1080.value
                    }
                )
                found = true
            }
        }
        return found
    }
}
