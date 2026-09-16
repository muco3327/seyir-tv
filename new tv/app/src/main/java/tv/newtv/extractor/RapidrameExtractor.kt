package tv.newtv.extractor

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource
import java.net.URI

class RapidrameExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "rplayer,rapidrame,hdfilmcehennemi"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("rplayer") || lower.contains("rapidrame") ||
                (lower.contains("hdfilmcehennemi") && (lower.contains("video") || lower.contains("embed") || lower.contains("rapidrame_id")))
    }

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = url.trim()
            if (targetUrl.startsWith("//")) {
                targetUrl = "https:$targetUrl"
            }

            // rapidrame_id parametresini doğrudan rplayer URL'sine dönüştür
            val rapidrameIdMatch = Regex("""rapidrame_id=([a-zA-Z0-9_-]+)""").find(targetUrl)
            if (rapidrameIdMatch != null) {
                val rId = rapidrameIdMatch.groupValues[1]
                targetUrl = "https://www.hdfilmcehennemi.nl/rplayer/$rId/"
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "https://www.hdfilmcehennemi.nl/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }

            if (html.isBlank()) return@withContext null

            // 1. Dean Edwards packer ve dinamik matematiksel şifreyi çöz
            var streamUrl = decryptRPlayer(html)

            // 2. Yedek: Doğrudan m3u8 bağlantısı varsa
            if (streamUrl.isNullOrBlank()) {
                val directMatch = Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
                    ?: Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
                streamUrl = directMatch?.groupValues?.get(1)
            }

            if (streamUrl.isNullOrBlank()) return@withContext null

            // Altyazıları ayrıştır
            val subtitles = mutableListOf<Subtitle>()
            try {
                val tracksMatch = Regex("""tracks\s*:\s*(\[[^\]]+\])""").find(html)
                if (tracksMatch != null) {
                    val tracksArray = JSONArray(tracksMatch.groupValues[1])
                    for (i in 0 until tracksArray.length()) {
                        val trackObj = tracksArray.getJSONObject(i)
                        val file = trackObj.optString("file")
                        val label = trackObj.optString("label").ifEmpty { "Türkçe Altyazı" }
                        val kind = trackObj.optString("kind")
                        if (file.isNotEmpty() && kind != "thumbnails") {
                            val fullSubUrl = if (file.startsWith("http")) file
                            else if (file.startsWith("/")) "https://www.hdfilmcehennemi.nl$file"
                            else "https://www.hdfilmcehennemi.nl/$file"
                            subtitles.add(Subtitle(label, fullSubUrl))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("RapidrameExtractor", "Subtitle parse error: ${e.message}")
            }

            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to "https://www.hdfilmcehennemi.nl/",
                "Origin" to "https://www.hdfilmcehennemi.nl"
            )

            VideoSource(
                url = streamUrl,
                quality = "Auto",
                headers = headers,
                subtitles = subtitles,
                mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                serverName = "Rapidrame Sunucusu"
            )
        } catch (e: Exception) {
            Log.e("RapidrameExtractor", "Error extracting Rapidrame stream: ${e.message}", e)
            null
        }
    }

    private fun decryptRPlayer(html: String): String? {
        try {
            val evalIdx = html.indexOf("eval(function(p,a,c,k,e,d)")
            if (evalIdx < 0) return null
            val scriptEndIdx = html.indexOf("</script>", evalIdx)
            val packedJs = if (scriptEndIdx > evalIdx) {
                html.substring(evalIdx, scriptEndIdx).trim()
            } else {
                html.substring(evalIdx).trim()
            }

            val unpacked = unpackPacker(packedJs)
                .replace("\\'", "'")
                .replace("\\\"", "\"")

            if (unpacked.isBlank()) return null

            val varMatch = Regex("""var\s+([a-zA-Z0-9_]+)\s*=\s*(dc_[a-zA-Z0-9]+)\s*\(\s*(\[[^\]]+\])\s*\)""").find(unpacked)
                ?: return null
            val funcName = varMatch.groupValues[2]
            val arrayJson = varMatch.groupValues[3]

            val jsonArray = JSONArray(arrayJson)
            val sb = StringBuilder()
            for (i in 0 until jsonArray.length()) {
                sb.append(jsonArray.getString(i))
            }
            var result = sb.toString()

            val funcStart = unpacked.indexOf(funcName)
            if (funcStart < 0) return null
            val funcEnd = unpacked.indexOf("return unmix", funcStart)
            if (funcEnd < 0) return null
            val body = unpacked.substring(funcStart, funcEnd)

            val segments = body.split("result=")
            for (i in 1 until segments.size) {
                val seg = segments[i]
                if (seg.contains("atob(result)")) {
                    val pad = result.length % 4
                    val padded = if (pad != 0) result + "=".repeat(4 - pad) else result
                    val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
                    result = String(decodedBytes, Charsets.ISO_8859_1)
                } else if (seg.contains("reverse()")) {
                    result = result.reversed()
                } else if (seg.contains("replace") && seg.contains("%26")) {
                    val shiftMatch = Regex("""\(o-base\+(\d+)\)%26""").find(seg)
                    if (shiftMatch != null) {
                        val shift = shiftMatch.groupValues[1].toInt()
                        val chars = CharArray(result.length)
                        for (j in result.indices) {
                            val o = result[j].code
                            chars[j] = when {
                                o in 65..90 -> ((o - 65 + shift) % 26 + 65).toChar()
                                o in 97..122 -> ((o - 97 + shift) % 26 + 97).toChar()
                                else -> result[j]
                            }
                        }
                        result = String(chars)
                    }
                }
            }

            val unmixMatch = Regex("""\(charCode-\((\d+)%\(i\+(\d+)\)\)\)%256""").find(body)
                ?: return null
            val seed = unmixMatch.groupValues[1].toInt()
            val offset = unmixMatch.groupValues[2].toInt()

            val unmixed = CharArray(result.length)
            for (i in result.indices) {
                val code = result[i].code
                val modVal = ((code - (seed % (i + offset))) % 256 + 256) % 256
                unmixed[i] = modVal.toChar()
            }

            val finalUrl = String(unmixed)
            return if (finalUrl.startsWith("http")) finalUrl else null
        } catch (e: Exception) {
            Log.e("RapidrameExtractor", "Decryption failed: ${e.message}", e)
            return null
        }
    }

    private fun unpackPacker(packedJs: String): String {
        val pattern = Regex("""\}\('(.*)',\s*(\d+),\s*(\d+),\s*'([^']*)'\.split\('\|'\)""", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(packedJs) ?: return ""
        val payload = match.groupValues[1]
        val radix = match.groupValues[2].toIntOrNull() ?: 62
        val count = match.groupValues[3].toIntOrNull() ?: 0
        val symtab = match.groupValues[4].split('|')

        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        fun baseEncode(number: Int, r: Int): String {
            if (number == 0) return "0"
            var n = number
            val sb = StringBuilder()
            while (n > 0) {
                sb.append(chars[n % r])
                n /= r
            }
            return sb.reverse().toString()
        }

        val wordMap = HashMap<String, String>(count)
        for (i in 0 until count) {
            val token = baseEncode(i, radix)
            val word = if (i < symtab.size && symtab[i].isNotEmpty()) symtab[i] else token
            wordMap[token] = word
        }

        val wordRegex = Regex("""\b\w+\b""")
        return wordRegex.replace(payload) { m ->
            wordMap[m.value] ?: m.value
        }
    }
}
