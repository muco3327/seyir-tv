package tv.newtv.utils

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NextJsAesDecryptor {
    private const val SECRET_PHRASE = "!!22xx!!90!!"

    private val keyBytes: ByteArray by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(SECRET_PHRASE.toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        b64.substring(0, 32).toByteArray(Charsets.UTF_8)
    }

    private val ivBytes = ByteArray(16) // 16 zero bytes

    fun decrypt(cipherTextB64: String): JSONObject? {
        return try {
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(ivBytes)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val cipherBytes = Base64.decode(cipherTextB64, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(cipherBytes)
            val jsonStr = String(decryptedBytes, Charsets.UTF_8).trim()
            if (jsonStr.startsWith("[")) {
                JSONObject().put("result", JSONArray(jsonStr))
            } else {
                JSONObject(jsonStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun extractAndDecryptFromHtml(html: String): JSONObject? {
        // 1. Doğrudan "secureData" kalıbı (Ana sayfa, film-izle, kategori sayfaları)
        val regex = Regex(""""secureData"\s*:\s*"([^"]+)"""")
        val match = regex.find(html)
        if (match != null) {
            decrypt(match.groupValues[1])?.let { return it }
        }

        // 2. __NEXT_DATA__ JSON scripti içindeki sayfa özellikleri (imdb-top, marvel, anime, kdrama)
        val nextRegex = Regex("""<script id="__NEXT_DATA__"[^>]*>([^<]+)</script>""")
        val nextMatch = nextRegex.find(html)
        if (nextMatch != null) {
            try {
                val parsed = JSONObject(nextMatch.groupValues[1])
                val pageProps = parsed.optJSONObject("props")?.optJSONObject("pageProps")
                if (pageProps != null) {
                    val merged = JSONObject()
                    val keys = pageProps.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = pageProps.opt(key)
                        if (value is String && value.length > 40) {
                            val decrypted = decrypt(value)
                            if (decrypted != null) {
                                val resArr = decrypted.optJSONArray("result")
                                if (resArr != null) {
                                    merged.put(key, resArr)
                                } else {
                                    merged.put(key, decrypted)
                                }
                            }
                        } else if (value is JSONArray || value is JSONObject) {
                            merged.put(key, value)
                        }
                    }
                    if (merged.length() > 0) return merged
                }
            } catch (e: Exception) {
                // Hataları sessizce yut
            }
        }

        return null
    }
}
