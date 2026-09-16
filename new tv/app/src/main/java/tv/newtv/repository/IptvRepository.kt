package tv.newtv.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import tv.newtv.network.readBounded
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tv.newtv.data.local.ChannelDao
import tv.newtv.data.local.ChannelEntity
import tv.newtv.data.local.ChannelStatus
import tv.newtv.data.local.InitialData
import tv.newtv.data.local.IptvPerformanceSettings
import tv.newtv.data.local.IptvSettingsManager
import tv.newtv.utils.M3uParser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.security.MessageDigest

class IptvRepository(
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient,
    private val context: Context? = null
) {
    // Share the connection pool and TLS context across all channel probes.
    private val pingClient by lazy { tv.newtv.network.OkHttpClientProvider.getFastPingOkHttpClient() }
    private val catalogMutex = kotlinx.coroutines.sync.Mutex()

    /** Prune old catalogs too, even offline. Keep channels independent, no merging between different streams. */
    suspend fun pruneCatalog(): List<ChannelEntity> = catalogMutex.withLock {
        val old = channelDao.getAllChannels().first()
        val curated = mergeCatalog(old, emptyList())
        channelDao.replaceAllChannels(curated)
        channelDao.getAllChannels().first()
    }

    private fun mergeCatalog(old: List<ChannelEntity>, incoming: List<ChannelEntity>): List<ChannelEntity> {
        val favoriteUrls = old.filter { it.isFavorite }.map { it.streamUrl.trim() }.toSet()
        val favoriteKeys = old.filter { it.isFavorite }.map { it.canonicalKey }.toSet()

        // 1. Daha önce birleştirilmiş olabilecek eski veritabanı kayıtlarını tekil kanallara ayır
        val sourcePool = mutableListOf<ChannelEntity>()
        for (ch in incoming + old) {
            val urls = ch.getStreamUrls().map { it.trim() }.filter { it.startsWith("http") }.distinct()
            val primaryUrl = urls.firstOrNull() ?: ch.streamUrl.trim()
            sourcePool.add(ch.copy(
                streamUrl = primaryUrl,
                streamUrlsJson = JSONArray(urls).toString(),
                sourceCount = urls.size
            ))
        }

        // 2. URL bazında tekilleştirme (SPORTS_JSON kanallarına öncelik verilir)
        val uniqueChannels = linkedMapOf<String, ChannelEntity>()
        val prioritizedPool = sourcePool.sortedBy { if (it.sourceListUrl == "SPORTS_JSON") 0 else 1 }

        for (channel in prioritizedPool) {
            val accepted = tv.newtv.utils.LiveChannelPolicy.resolve(channel.name, channel.groupTitle) ?: continue
            val cleanUrl = channel.streamUrl.trim()
            if (cleanUrl.isBlank() || !cleanUrl.startsWith("http")) continue
            if (uniqueChannels.containsKey(cleanUrl)) continue

            val isFav = favoriteUrls.contains(cleanUrl) || favoriteKeys.contains(accepted.key)
            val selfBackups = mutableListOf<String>()
            selfBackups.addAll(channel.getStreamUrls().filter { it.isNotBlank() })
            if (!selfBackups.contains(cleanUrl)) {
                selfBackups.add(0, cleanUrl)
            }

            uniqueChannels[cleanUrl] = channel.copy(
                name = channel.name.ifBlank { accepted.name },
                canonicalKey = accepted.key,
                groupTitle = accepted.category,
                logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() },
                sourceListUrl = channel.sourceListUrl,
                streamUrl = cleanUrl,
                streamUrlsJson = JSONArray(selfBackups.distinct()).toString(),
                sourceCount = selfBackups.distinct().size,
                isFavorite = isFav
            )
        }

        val channelList = uniqueChannels.values.toList()
        return channelList
    }

    fun catalogFingerprint(channels: List<ChannelEntity>): String {
        val content = channels.sortedBy { it.name + it.streamUrl }.joinToString("\n") { channel ->
            "${channel.canonicalKey}|${channel.streamUrl}" 
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun loadSportsJsonChannels(): List<ChannelEntity> {
        val result = mutableListOf<ChannelEntity>()
        var json: String? = null

        // 1. Önce yerel asset'ten sports.json oku (en hızlı ve garantili)
        if (context != null) {
            try {
                context.assets.open("sports.json").use { stream ->
                    json = stream.bufferedReader(Charsets.UTF_8).readText()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local sports.json load error: ${e.message}")
            }
        }

        // 2. Uzak GitHub'dan en güncel sports.json varsa dene
        try {
            val req = Request.Builder()
                .url("https://raw.githubusercontent.com/muco3327/seyir-tv/main/sports.json")
                .header("User-Agent", "SeyirTV-Sports")
                .build()
            val remoteClient = okHttpClient.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
            remoteClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        json = bodyStr
                    }
                }
            }
        } catch (_: Exception) {}

        if (json.isNullOrBlank()) return emptyList()

        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "Kanal ${i + 1}")
                val logo = obj.optString("logo", "")
                val category = obj.optString("category", "Spor")

                val headersMap = mutableMapOf<String, String>()
                val hObj = obj.optJSONObject("headers")
                if (hObj != null) {
                    val it = hObj.keys()
                    while (it.hasNext()) {
                        val k = it.next()
                        headersMap[k] = hObj.optString(k)
                    }
                }
                val headerQuery = if (headersMap.isNotEmpty()) {
                    "|" + headersMap.map { "${it.key}=${it.value}" }.joinToString("&")
                } else ""

                val urls = mutableListOf<String>()
                val uArr = obj.optJSONArray("urls")
                if (uArr != null) {
                    for (j in 0 until uArr.length()) {
                        val u = uArr.optString(j)?.trim() ?: ""
                        if (u.isNotBlank()) urls.add(u + headerQuery)
                    }
                } else if (obj.has("url")) {
                    val u = obj.optString("url").trim()
                    if (u.isNotBlank()) urls.add(u + headerQuery)
                }

                if (urls.isNotEmpty()) {
                    val primary = urls[0]
                    val accepted = tv.newtv.utils.LiveChannelPolicy.resolve(name, category)
                    result.add(
                        ChannelEntity(
                            name = accepted?.name ?: name,
                            logoUrl = logo.takeIf { it.isNotBlank() },
                            groupTitle = "Spor Kanalları",
                            streamUrl = primary,
                            sourceListUrl = "SPORTS_JSON",
                            canonicalKey = accepted?.key ?: name.lowercase().replace(" ", ""),
                            streamUrlsJson = JSONArray(urls).toString(),
                            sourceCount = urls.size,
                            isFavorite = false,
                            status = ChannelStatus.ACTIVE
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse sports.json error", e)
        }

        return result
    }

    /** Retain partial successful downloads at the deadline; never replace the cache with an empty download. */
    suspend fun syncInitialPlaylists(
        extraSources: List<String> = emptyList(),
        budgetMs: Long = 15_000L
    ) = catalogMutex.withLock {
        withContext(Dispatchers.IO) {
            // 0. sports.json kanallarını öncelikle kontrol et ve yükle
            val sportsChannels = loadSportsJsonChannels()
            val currentInDb = channelDao.getAllChannels().first()
            if (sportsChannels.isNotEmpty() && currentInDb.none { it.sourceListUrl == "SPORTS_JSON" }) {
                channelDao.replaceAllChannels(mergeCatalog(currentInDb, sportsChannels))
            }

            // İlk açılışta eski birleşmiş kayıtlar varsa derhal bağımsız kanallara dönüştür
            val currentUpdated = channelDao.getAllChannels().first()
            if (currentUpdated.any { it.sourceCount > 1 || it.getStreamUrls().size > 1 }) {
                channelDao.replaceAllChannels(mergeCatalog(currentUpdated, emptyList()))
            }

            context?.let { IptvSettingsManager.addCustomSources(it, extraSources) }
            val custom = context?.let { IptvSettingsManager.getCustomSources(it) } ?: emptyList()
            val sources = (extraSources + InitialData.iptvSources + custom).filter { it.isNotBlank() }.distinct()
            val incoming = java.util.Collections.synchronizedList(mutableListOf<ChannelEntity>())
            val semaphore = Semaphore(6)
            val downloadClient = okHttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
            kotlinx.coroutines.withTimeoutOrNull(budgetMs) {
                coroutineScope {
                    sources.map { url ->
                        async {
                            semaphore.withPermit {
                                try {
                                    val sample = downloadClient.readBounded(Request.Builder().url(url).build(), 8 * 1024 * 1024)
                                    if (sample.code in 200..299) {
                                        M3uParser.parse(sample.bytes.inputStream(), url).collect { incoming.add(it) }
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    Log.w(TAG, "Playlist download failed: $url", error)
                                }
                                Unit
                            }
                        }
                    }.awaitAll()
                }
            }
            ensureActive()
            val existing = channelDao.getAllChannels().first()
            val sortedIncoming = sportsChannels + incoming.sortedBy { sources.indexOf(it.sourceListUrl).takeIf { idx -> idx >= 0 } ?: 999 }
            channelDao.replaceAllChannels(mergeCatalog(existing, sortedIncoming))
        }
    }

    /** Short opening check, prioritising beIN. Results are stored as each probe completes. */
    suspend fun verifyStartupChannels(onProgress: (Int, Int) -> Unit) = coroutineScope {
        val channels = channelDao.getAllChannels().first().sortedBy { if (it.name.startsWith("beIN")) 0 else 1 }
        val probeClient = pingClient.newBuilder()
            .dispatcher(okhttp3.Dispatcher().apply { maxRequests = 24; maxRequestsPerHost = 24 })
            .callTimeout(3500, TimeUnit.MILLISECONDS).build()
        val semaphore = Semaphore(24)
        val completed = AtomicInteger()
        onProgress(0, channels.size)
        channels.map { channel ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    var status = ChannelStatus.OFFLINE
                    for (url in channel.getStreamUrls().take(2)) {
                        ensureActive()
                        try {
                            val sample = probeClient.readBounded(
                                Request.Builder().url(url).header("Range", "bytes=0-511").build(), 512
                            )
                            val prefix = sample.bytes.toString(Charsets.UTF_8).trimStart().lowercase()
                            if (sample.code in 200..299 && sample.bytes.isNotEmpty() &&
                                !sample.contentType.contains("text/html", true) &&
                                !prefix.startsWith("<html") && !prefix.startsWith("<!doctype")) {
                                status = ChannelStatus.ACTIVE
                                if (url != channel.streamUrl) channelDao.updateStreamUrl(channel.id, url)
                                break
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) { }
                    }
                    ensureActive()
                    val finalStatus = status
                    channelDao.updateChannelStatus(channel.id, finalStatus)
                    onProgress(completed.incrementAndGet(), channels.size)
                }
            }
        }.awaitAll()
    }
    private fun getCustomTimeoutClient(): OkHttpClient {
        val settings = context?.let { IptvSettingsManager.getSettings(it) } ?: IptvPerformanceSettings()
        val timeout = settings.timeoutSec.coerceIn(2, 20).toLong()
        return pingClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Tek bir URL'in erişilebilir olup olmadığını ve yanıt gecikmesini (ms) test eder.
     */
    fun pingUrlWithLatency(streamUrl: String): Pair<Boolean, Long> {
        val client = getCustomTimeoutClient()
        val startTime = System.currentTimeMillis()
        try {
            val headRequest = Request.Builder()
                .url(streamUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "*/*")
                .build()

            client.newCall(headRequest).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val code = response.code
                if (code in 200..399) {
                    return Pair(true, latency)
                }
            }

            val getRequest = Request.Builder()
                .url(streamUrl)
                .get()
                .header("Range", "bytes=0-128")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(getRequest).execute().use { getResp ->
                val latency = System.currentTimeMillis() - startTime
                val getCode = getResp.code
                if (getCode in 200..399) {
                    return Pair(true, latency)
                }
            }
        } catch (_: Exception) {}
        val latency = System.currentTimeMillis() - startTime
        return Pair(false, latency)
    }

    private fun pingUrl(streamUrl: String): Boolean {
        return pingUrlWithLatency(streamUrl).first
    }

    /**
     * Kanalın durumunu test eder: İlk kaynak çalışıyorsa hemen ACTIVE döner.
     * İlk kaynak çalışmıyorsa diğer yedek kaynakları (en fazla 2 yedek) test eder.
     */
    suspend fun checkChannelStatus(channel: ChannelEntity): ChannelStatus = withContext(Dispatchers.IO) {
        val urls = channel.getStreamUrls()
        // beIN gibi çok kaynaklı kanallar için en fazla 4 URL test et
        val toCheck = if (urls.size > 4) urls.take(4) else urls
        for ((index, url) in toCheck.withIndex()) {
            kotlin.coroutines.coroutineContext.ensureActive()
            if (pingUrl(url)) {
                kotlin.coroutines.coroutineContext.ensureActive()
                // Çalışan URL birincil değilse, birincil URL'yi güncelle
                if (index > 0) {
                    try {
                        channelDao.updateStreamUrl(channel.id, url)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {}
                }
                return@withContext ChannelStatus.ACTIVE
            }
        }
        ChannelStatus.OFFLINE
    }

    /**
     * Kanala ait tüm kaynakları test eder ve her birinin durumunu ve gecikme süresini döner.
     */
    suspend fun testChannelAllSources(channel: ChannelEntity): List<Pair<String, Pair<Boolean, Long>>> = withContext(Dispatchers.IO) {
        channel.getStreamUrls().map { url ->
            val result = pingUrlWithLatency(url)
            url to result
        }
    }

    /**
     * Kanalları IptvSettingsManager'daki eşzamanlı kanal test sayısıyla (varsayılan 15) doğrular.
     */
    suspend fun checkChannelsBatch(
        channels: List<ChannelEntity>,
        onProgress: (verified: Int, total: Int, channelId: Int, status: ChannelStatus) -> Unit = { _, _, _, _ -> }
    ): List<Pair<Int, ChannelStatus>> = coroutineScope {
        val settings = context?.let { IptvSettingsManager.getSettings(it) } ?: IptvPerformanceSettings()
        val semaphore = Semaphore(settings.concurrentTestCount.coerceIn(1, 30))
        val progressCount = AtomicInteger(0)
        val total = channels.size

        val deferreds = channels.map { channel ->
            async(Dispatchers.IO) {
                val status = semaphore.withPermit {
                    checkChannelStatus(channel)
                }
                ensureActive()
                val current = progressCount.incrementAndGet()
                onProgress(current, total, channel.id, status)
                channel.id to status
            }
        }
        deferreds.awaitAll()
    }
    private companion object {
        const val TAG = "IptvRepository"
    }
}
