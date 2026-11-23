package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class TRTCanli : MainAPI() {
    override var mainUrl = "https://www.trtizle.com"
    override var name = "TRT Canlı"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    data class TrtChannel(
        val id: String,
        val title: String,
        val url: String,
        val category: String,
        val poster: String
    )

    private val channels = listOf(
        TrtChannel("trt1", "TRT 1 HD", "https://tv-trt1.medya.trt.com.tr/master.m3u8", "Ulusal & Kültür", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trt2", "TRT 2 HD", "https://tv-trt2.medya.trt.com.tr/master.m3u8", "Ulusal & Kültür", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trtspor", "TRT Spor HD", "https://tv-trtspor1.medya.trt.com.tr/master.m3u8", "Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trtspor.png"),
        TrtChannel("trtspor2", "TRT Spor Yıldız HD", "https://tv-trtspor2.medya.trt.com.tr/master.m3u8", "Spor", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trtspor2.png"),
        TrtChannel("trthaber", "TRT Haber HD", "https://tv-trthaber.medya.trt.com.tr/master.m3u8", "Haber & Belgesel", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trthaber.png"),
        TrtChannel("trtbelgesel", "TRT Belgesel HD", "https://tv-trtbelgesel.medya.trt.com.tr/master.m3u8", "Haber & Belgesel", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trtbelgesel.png"),
        TrtChannel("trtcocuk", "TRT Çocuk HD", "https://tv-trtcocuk.medya.trt.com.tr/master.m3u8", "Çocuk & Müzik & Uluslararası", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trtmuzik", "TRT Müzik HD", "https://tv-trtmuzik.medya.trt.com.tr/master.m3u8", "Çocuk & Müzik & Uluslararası", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trtturk", "TRT Türk HD", "https://tv-trtturk.medya.trt.com.tr/master.m3u8", "Çocuk & Müzik & Uluslararası", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trtavaz", "TRT Avaz HD", "https://tv-trtavaz.medya.trt.com.tr/master.m3u8", "Çocuk & Müzik & Uluslararası", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trt1.png"),
        TrtChannel("trtworld", "TRT World HD", "https://tv-trtworld.medya.trt.com.tr/master.m3u8", "Çocuk & Müzik & Uluslararası", "https://raw.githubusercontent.com/merb1y/cloudstream-tr-repo/main/assets/logos/trthaber.png")
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
            this.plot = "${ch.title} - Resmi TRT Kesintisiz Canlı Yayın"
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
        callback.invoke(
            newExtractorLink(
                name,
                "${ch.title} (Resmi HLS)",
                ch.url,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )
        return true
    }
}
