package tv.newtv.extractor

import android.util.Base64
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class VidmixiExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "vidmixi"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val embedUrl = if (url.contains("/f/")) url.replace("/f/", "/embed/") else url

            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "https://filmmodu.cc/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            response.close()

            // bePlayer('QndabjhpenhPUitHUDhBNWxWNWt3dz09', '{"ct":"...","iv":"...","s":"..."}')
            val bePlayerRegex = Regex("""bePlayer\s*\(\s*['"]([^'"]+)['"]\s*,\s*['"](\{.+?\})['"]""")
            val match = bePlayerRegex.find(html) ?: return@withContext null

            val passphraseStr = match.groupValues[1]
            val ctJsonStr = match.groupValues[2]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")

            val ctJson = JSONObject(ctJsonStr)
            val ctB64 = ctJson.getString("ct")
            val ivHex = ctJson.getString("iv")
            val saltHex = ctJson.getString("s")

            val ctBytes = Base64.decode(ctB64, Base64.DEFAULT)
            val ivBytes = hexStringToByteArray(ivHex)
            val saltBytes = hexStringToByteArray(saltHex)
            val passphraseBytes = passphraseStr.toByteArray(Charsets.UTF_8)

            // CryptoJS EVP_BytesToKey (MD5 derivation)
            val md = MessageDigest.getInstance("MD5")
            var d = ByteArray(0)
            var dI = ByteArray(0)
            while (d.size < 32 + 16) {
                md.reset()
                md.update(dI)
                md.update(passphraseBytes)
                md.update(saltBytes)
                dI = md.digest()

                val next = ByteArray(d.size + dI.size)
                System.arraycopy(d, 0, next, 0, d.size)
                System.arraycopy(dI, 0, next, d.size, dI.size)
                d = next
            }

            val keyBytes = ByteArray(32)
            System.arraycopy(d, 0, keyBytes, 0, 32)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
            val decryptedBytes = cipher.doFinal(ctBytes)
            val decryptedJsonStr = String(decryptedBytes, Charsets.UTF_8)

            val data = JSONObject(decryptedJsonStr)
            val videoLocation = data.optString("video_location")
            if (videoLocation.isEmpty()) return@withContext null

            // Altyazıları ayıkla
            val subtitles = mutableListOf<Subtitle>()
            val subArray = data.optJSONArray("strSubtitles")
            if (subArray != null) {
                for (i in 0 until subArray.length()) {
                    val sObj = subArray.optJSONObject(i) ?: continue
                    var file = sObj.optString("file")
                    val label = sObj.optString("label").ifEmpty { "Türkçe" }
                    if (file.isNotEmpty()) {
                        if (file.startsWith("/")) file = "https://vidmixi.com$file"
                        subtitles.add(Subtitle(language = label, url = file))
                    }
                }
            }

            val headers = mapOf(
                "Referer" to "https://vidmixi.com/",
                "User-Agent" to userAgent
            )

            VideoSource(
                url = videoLocation,
                quality = "1080p",
                mimeType = MimeTypes.APPLICATION_M3U8,
                headers = headers,
                subtitles = subtitles,
                serverName = "Vidmixi Sunucusu"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
