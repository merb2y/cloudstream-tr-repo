package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class ZirveSpor : MainAPI() {
    override var mainUrl = "https://betmatiktv156.com"
    override var name = "ZirveSpor"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private val domainApi = "https://data-reality.com/domain.php"
    private val channelsApi = "https://data-reality.com/channels.php"
    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    data class StaticChannel(
        val id: String,
        val title: String,
        val category: String,
        val poster: String
    )

    private val defaultChannels = listOf(
        StaticChannel("zirve", "beIN Sports 1 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"),
        StaticChannel("b2", "beIN Sports 2 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein2.png"),
        StaticChannel("b3", "beIN Sports 3 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein3.png"),
        StaticChannel("b4", "beIN Sports 4 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein4.png"),
        StaticChannel("b5", "beIN Sports 5 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein5.png"),
        StaticChannel("bm1", "beIN Sports Max 1 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"),
        StaticChannel("bm2", "beIN Sports Max 2 HD", "beIN Sports (7/24)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein2.png"),
        StaticChannel("ss", "S Sport 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/ssport1.png"),
        StaticChannel("ss2", "S Sport 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/ssport2.png"),
        StaticChannel("smarts", "Smart Spor 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/smart1.png"),
        StaticChannel("sms2", "Smart Spor 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/smart1.png"),
        StaticChannel("t1", "Tivibu Spor 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        StaticChannel("t2", "Tivibu Spor 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        StaticChannel("t3", "Tivibu Spor 3 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        StaticChannel("t4", "Tivibu Spor 4 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        StaticChannel("trtspor", "TRT Spor HD", "Ulusal Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trtspor.png"),
        StaticChannel("trtspor2", "TRT Spor Yıldız HD", "Ulusal Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trtspor2.png"),
        StaticChannel("as", "A Spor HD", "Ulusal Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/aspor.png"),
        StaticChannel("tv85", "TV 8,5 HD", "Ulusal Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tv85.png")
    )

    private suspend fun fetchLiveChannels(): List<StaticChannel> {
        val out = mutableListOf<StaticChannel>()
        val seenIds = mutableSetOf<String>()
        runCatching {
            val html = app.get(
                channelsApi,
                headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/"),
                timeout = 6
            ).text
            val doc = Jsoup.parse(html)
            doc.select("a.single-match").forEach { el ->
                val href = el.attr("href")
                val id = Regex("""id=([^&\s]+)""").find(href)?.groupValues?.get(1) ?: return@forEach
                if (!seenIds.add(id)) return@forEach
                val home = el.selectFirst(".home")?.text()?.trim().orEmpty()
                val away = el.selectFirst(".away")?.text()?.trim().orEmpty()
                val time = el.selectFirst(".event")?.text()?.trim().orEmpty()
                val rawTitle = when {
                    home.isNotBlank() && away.isNotBlank() -> "$home - $away"
                    home.isNotBlank() -> home
                    else -> id.uppercase()
                }
                val title = if (time.isNotBlank() && !time.equals("7/24", true)) "[$time] $rawTitle" else rawTitle
                val img = el.selectFirst(".away img")?.attr("src")?.let {
                    if (it.startsWith("http")) it else "$mainUrl/${it.trimStart('/')}"
                } ?: defaultChannels.find { it.id == id }?.poster
                    ?: "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"
                val category = if (time.equals("7/24", true)) {
                    if (title.contains("bein", true)) "beIN Sports (7/24)" else "7/24 Spor Kanalları"
                } else {
                    "Günün Canlı Maçları"
                }
                out.add(StaticChannel(id, title, category, img))
            }
        }
        defaultChannels.forEach { def ->
            if (seenIds.add(def.id)) out.add(def)
        }
        return out
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = fetchLiveChannels()
        val lists = items.groupBy { it.category }.map { (cat, list) ->
            HomePageList(
                cat,
                list.map { ch ->
                    newLiveSearchResponse(ch.title, "$mainUrl/kanal/${ch.id}?n=${ch.title}", TvType.Live) {
                        this.posterUrl = ch.poster
                    }
                },
                isHorizontalImages = true
            )
        }
        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchLiveChannels().filter { it.title.contains(query, ignoreCase = true) }.map { ch ->
            newLiveSearchResponse(ch.title, "$mainUrl/kanal/${ch.id}?n=${ch.title}", TvType.Live) {
                this.posterUrl = ch.poster
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfter("/kanal/").substringBefore("?")
        val title = url.substringAfter("?n=", "").ifBlank { id.uppercase() }
        val poster = defaultChannels.find { it.id == id }?.poster
            ?: "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"
        return newLiveStreamLoadResponse(title, url, url) {
            this.posterUrl = poster
            this.plot = "$title - ZirveSpor Canlı HD Yayın"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringAfter("/kanal/").substringBefore("?")
        val title = data.substringAfter("?n=", "").ifBlank { id.uppercase() }

        val dynamicBase = runCatching {
            val json = app.get(
                domainApi,
                headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/"),
                timeout = 5
            ).text
            Regex(""""baseurl"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
                ?.replace("\\/", "/")
        }.getOrNull()

        val baseUrls = listOfNotNull(dynamicBase, "https://340.zirvedesin251.lol/").distinct()
        val referers = listOf("$mainUrl/", "https://101baywintv.live/")

        baseUrls.forEachIndexed { idx, base ->
            val normalizedBase = if (base.endsWith("/")) base else "$base/"
            val streamUrl = "${normalizedBase}${id}/mono.m3u8"
            val ref = referers.getOrElse(idx) { "$mainUrl/" }
            val origin = ref.trimEnd('/')
            callback.invoke(
                newExtractorLink(
                    name,
                    "$title - ZirveCDN-${idx + 1} (1080p)",
                    streamUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = ref
                    this.headers = mapOf(
                        "User-Agent" to ua,
                        "Referer"    to ref,
                        "Origin"     to origin
                    )
                    this.quality = Qualities.P1080.value
                }
            )
        }
        return true
    }
}
