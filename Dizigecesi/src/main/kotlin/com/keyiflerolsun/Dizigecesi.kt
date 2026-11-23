package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class Dizigecesi : MainAPI() {
    override var mainUrl = "https://dizigecesi.com"
    override var name = "Dizigecesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "$mainUrl/diziler?page="  to "Diziler",
        "$mainUrl/filmler?page="  to "Filmler",
        "$mainUrl/populer?page="  to "Popüler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("${request.data}$page", headers = mapOf("User-Agent" to ua)).document
        val items = doc.select("a.card-series").mapNotNull { it.toCardResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    private fun Element.toCardResult(): SearchResponse? {
        val href = fixUrlNull(attr("href")) ?: return null
        if (!href.contains("/dizi/") && !href.contains("/film/")) return null
        val img = selectFirst("img.card-series__image-media, img")
        val title = img?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
            ?: selectFirst(".card-series__title, h3, span")?.text()?.trim()
            ?: return null
        val poster = fixUrlNull(img?.attr("data-src")?.ifBlank { img.attr("src") })
        return if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val doc = app.get("$mainUrl/arama/$encoded", headers = mapOf("User-Agent" to ua)).document
        return doc.select("a.card-series").mapNotNull { it.toCardResult() }.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to ua)).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            doc.selectFirst("img[src*='/upload/cover/']")?.attr("src")
                ?: doc.selectFirst("meta[property='og:image']")?.attr("content")
        )
        val plot = doc.selectFirst(".series-info__description, p.description, meta[name='description']")
            ?.let { it.attr("content").ifBlank { it.text() } }?.trim()

        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        val epRegex = Regex("""(\d+)-sezon/(\d+)-bolum""")
        val episodes = doc.select("a[href*='-sezon/'][href*='-bolum']")
            .mapNotNull { el ->
                val href = fixUrlNull(el.attr("href")) ?: return@mapNotNull null
                val m = epRegex.find(href) ?: return@mapNotNull null
                val s = m.groupValues[1].toIntOrNull()
                val e = m.groupValues[2].toIntOrNull()
                newEpisode(href) {
                    this.name = "${e ?: 1}. Bölüm"
                    this.season = s
                    this.episode = e
                }
            }
            .distinctBy { it.data }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    private suspend fun resolveVidMoly(embedUrl: String, referer: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val fixedUrl = embedUrl
            .replace("vidmoly.me", "vidmoly.biz")
            .replace("vidmoly.to", "vidmoly.biz")
            .let { if (it.contains("/w/")) it.replaceFirst("/w/", "/embed-") + ".html" else it }

        val resp = runCatching {
            app.get(
                fixedUrl,
                referer = referer,
                headers = mapOf("User-Agent" to ua, "Sec-Fetch-Dest" to "iframe")
            ).text
        }.getOrNull() ?: return false

        val m3u8 = Regex("""file\s*:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").find(resp)?.groupValues?.get(1)
            ?: Regex("""(https?://[^\s'"]+\.m3u8[^\s'"]*)""").find(resp)?.groupValues?.get(1)
            ?: return false

        val origin = Regex("""^(https?://[^/]+)""").find(fixedUrl)?.groupValues?.get(1) ?: "https://vidmoly.biz"
        Regex("""file\s*:\s*['"]([^'"]+\.(?:vtt|srt)[^'"]*)['"]\s*,\s*label\s*:\s*['"]([^'"]+)['"]""").findAll(resp).forEach { m ->
            val subUrl = if (m.groupValues[1].startsWith("http")) m.groupValues[1] else "$origin${m.groupValues[1]}"
            subtitleCallback.invoke(newSubtitleFile(m.groupValues[2], subUrl))
        }
        callback.invoke(
            newExtractorLink(name, "$name - VidMoly (1080p)", m3u8, ExtractorLinkType.M3U8) {
                this.referer = "$origin/"
                this.headers = mapOf("User-Agent" to ua, "Referer" to "$origin/", "Origin" to origin)
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mapOf("User-Agent" to ua)).document
        val embedIds = doc.select("[data-embed]")
            .mapNotNull { el ->
                val id = el.attr("data-embed").trim()
                if (id.isBlank()) null else id
            }
            .distinct()

        var found = false
        embedIds.amap { embedId ->
            runCatching {
                val ajaxResp = app.post(
                    "$mainUrl/ajax/embed",
                    data = mapOf("id" to embedId),
                    headers = mapOf(
                        "User-Agent"       to ua,
                        "Referer"          to data,
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).text
                val iframeSrc = Regex("""src=["']([^"']+)["']""").find(ajaxResp)?.groupValues?.get(1)
                    ?.let { fixUrl(it) } ?: return@runCatching

                val targetUrl = if (iframeSrc.contains("$mainUrl/video/")) {
                    val vHtml = app.get(iframeSrc, headers = mapOf("User-Agent" to ua, "Referer" to data)).text
                    Regex("""<iframe[^>]+id=["']main-iframe["'][^>]+src=["']([^"']+)["']""")
                        .find(vHtml)?.groupValues?.get(1)
                        ?: Regex("""<iframe[^>]+src=["']([^"']+)["']""").find(vHtml)?.groupValues?.get(1)
                        ?: iframeSrc
                } else {
                    iframeSrc
                }

                val cleanTarget = fixUrl(targetUrl).substringBefore("#")
                if (cleanTarget.contains("vidmoly", ignoreCase = true)) {
                    if (resolveVidMoly(cleanTarget, iframeSrc, subtitleCallback, callback)) {
                        found = true
                        return@runCatching
                    }
                }
                if (loadExtractor(cleanTarget, iframeSrc, subtitleCallback, callback)) {
                    found = true
                }
            }
        }
        return found
    }
}
