package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class KralSporHD : MainAPI() {
    override var mainUrl = "https://kralsportshd.com"
    override var name = "KralSporHD"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    private val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36"

    data class KralChannel(
        val id: String,
        val title: String,
        val category: String,
        val poster: String
    )

    private val channels = listOf(
        KralChannel("androstreamlivebs1", "beIN Sports 1 FHD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"),
        KralChannel("androstreamlivebs2", "beIN Sports 2 FHD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein2.png"),
        KralChannel("androstreamlivebs3", "beIN Sports 3 FHD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein3.png"),
        KralChannel("androstreamlivebs4", "beIN Sports 4 FHD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein4.png"),
        KralChannel("androstreamlivebs5", "beIN Sports 5 FHD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein5.png"),
        KralChannel("androstreamlivebsm1", "beIN Sports Max 1 HD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein1.png"),
        KralChannel("androstreamlivebsm2", "beIN Sports Max 2 HD", "beIN Sports (Canlı Maç)", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/bein2.png"),
        KralChannel("androstreamlivess1", "S Sport 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/ssport1.png"),
        KralChannel("androstreamlivess2", "S Sport 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/ssport2.png"),
        KralChannel("androstreamlivets1", "Tivibu Spor 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        KralChannel("androstreamlivets2", "Tivibu Spor 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        KralChannel("androstreamlivets3", "Tivibu Spor 3 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        KralChannel("androstreamlivets4", "Tivibu Spor 4 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tivibu1.png"),
        KralChannel("androstreamlivesm1", "Smart Spor 1 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/smart1.png"),
        KralChannel("androstreamlivesm2", "Smart Spor 2 HD", "Özel Spor Kanalları", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/smart1.png"),
        KralChannel("androstreamlivetbs", "Tabii Spor HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tabiispor.png"),
        KralChannel("androstreamlivetbs1", "Tabii Spor 1 HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tabiispor.png"),
        KralChannel("androstreamlivetbs2", "Tabii Spor 2 HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/tabiispor.png"),
        KralChannel("androstreamlivees1", "Eurosport 1 HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/eurosport1.png"),
        KralChannel("androstreamlivees2", "Eurosport 2 HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/eurosport2.png"),
        KralChannel("androstreamlivenba", "NBA TV HD", "Tabii & Diğer Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/nbatv.png")
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val lists = channels.groupBy { it.category }.map { (cat, list) ->
            HomePageList(
                cat,
                list.map { ch ->
                    newLiveSearchResponse(ch.title, "$mainUrl/kanal/${ch.id}", TvType.Live) {
                        this.posterUrl = ch.poster
                    }
                },
                isHorizontalImages = true
            )
        }
        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return channels.filter { it.title.contains(query, ignoreCase = true) }.map { ch ->
            newLiveSearchResponse(ch.title, "$mainUrl/kanal/${ch.id}", TvType.Live) {
                this.posterUrl = ch.poster
            }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val id = url.substringAfterLast('/')
        val ch = channels.find { it.id == id } ?: channels.first()
        return newLiveStreamLoadResponse(ch.title, url, url) {
            this.posterUrl = ch.poster
            this.plot = "${ch.title} - KralSporHD Çoklu Sunuculu Canlı Yayın"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val id = data.substringAfterLast('/')
        val ch = channels.find { it.id == id } ?: channels.first()

        val scrapedBases = runCatching {
            val html = app.get(
                "$mainUrl/event.html?id=$id",
                headers = mapOf("User-Agent" to ua, "Referer" to "$mainUrl/giris2/"),
                timeout = 5
            ).text
            val arrText = Regex("""this\.baseurls\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.groupValues?.get(1).orEmpty()
            Regex(""""(https?://[^"]+)"""").findAll(arrText).map { it.groupValues[1] }.toList()
        }.getOrDefault(emptyList())

        val fallbackBases = listOf(
            "https://andro.evrenesoglu235.click/checklist/",
            "https://andro.evrenesoglu237.click/checklist/",
            "https://andro.3749206.xyz/checklist/"
        )

        val allBases = (scrapedBases + fallbackBases).distinct()
        allBases.forEachIndexed { idx, base ->
            val norm = if (base.endsWith("/")) base else "$base/"
            val streamUrl = "${norm}${ch.id}.m3u8"
            callback.invoke(
                newExtractorLink(
                    name,
                    "${ch.title} - KralCDN-${idx + 1} (1080p)",
                    streamUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "$mainUrl/"
                    this.headers = mapOf(
                        "User-Agent" to ua,
                        "Referer"    to "$mainUrl/",
                        "Origin"     to mainUrl
                    )
                    this.quality = Qualities.P1080.value
                }
            )
        }
        return true
    }
}
