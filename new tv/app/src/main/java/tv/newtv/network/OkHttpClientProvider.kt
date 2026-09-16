package tv.newtv.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object OkHttpClientProvider {

    // Cloudflare oturum çerezleri ve diğer cookie'leri bellekte tutar
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(cookieStore) {
                val host = url.host
                val existingCookies = cookieStore[host] ?: mutableListOf()
                cookies.forEach { cookie ->
                    existingCookies.removeAll { it.name == cookie.name }
                    existingCookies.add(cookie)
                }
                cookieStore[host] = existingCookies
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return synchronized(cookieStore) {
                cookieStore[url.host]?.filter { !it.hasExpired() } ?: emptyList()
            }
        }
        
        private fun Cookie.hasExpired(): Boolean {
            return this.expiresAt < System.currentTimeMillis()
        }
    }

    // Anti-Bot: Eksik başlıkları tamamlar, çağırıcı tarafından belirlenen özel başlıkları ezmez
    private val userAgentInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
        if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        }
        if (originalRequest.header("Accept-Language").isNullOrEmpty()) {
            builder.header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
        }
        chain.proceed(builder.build())
    }

    // Süresi geçmiş/kırık sertifikaları bypass eder (SSLHandshakeException önleyici)
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .addInterceptor(userAgentInterceptor)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            throw RuntimeException("OkHttp Unsafe Client oluşturulamadı", e)
        }
    }

    fun getFastPingOkHttpClient(): OkHttpClient {
        return try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .addInterceptor(userAgentInterceptor)
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .callTimeout(3500, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        } catch (e: Exception) {
            getUnsafeOkHttpClient()
        }
    }

    // Film ve dizi afişlerinin (Coil) yüksek eşzamanlılıkla ve takılmadan indirilmesi için optimize istemci
    private val imageOkHttpClientInstance by lazy {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 8
            }
            val pool = okhttp3.ConnectionPool(16, 5, TimeUnit.MINUTES)

            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .addInterceptor(userAgentInterceptor)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            getUnsafeOkHttpClient()
        }
    }

    fun getImageOkHttpClient(): OkHttpClient = imageOkHttpClientInstance

    // Film ve dizi kazıyıcılarının (Scraper) paralel istekleri beklemeden hızlıca çekmesi için istemci
    private val scraperOkHttpClientInstance by lazy {
        try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 24
                maxRequestsPerHost = 6
            }
            val pool = okhttp3.ConnectionPool(12, 5, TimeUnit.MINUTES)

            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .addInterceptor(userAgentInterceptor)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            getUnsafeOkHttpClient()
        }
    }

    fun getScraperOkHttpClient(): OkHttpClient = scraperOkHttpClientInstance

    private val fastplayInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        val host = url.host
        val isFastplay = host.contains("fastplay", ignoreCase = true) || url.encodedPath.contains("/manifests/")
        if (isFastplay) {
            val builder = request.newBuilder()
            val token = FastplayTokenProvider.generateXSp()
            if (token != null) {
                builder.header("X-Sp", token)
            }
            builder.header("Origin", "https://$host")
            val referer = FastplayTokenProvider.currentReferer ?: "https://$host/"
            builder.header("Referer", referer)
            chain.proceed(builder.build())
        } else {
            chain.proceed(request)
        }
    }

    private val rapidrameMediaInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        val host = url.host
        val isRapidOrHdf = host.contains("rapidrame", ignoreCase = true) ||
                           host.contains("rplayer", ignoreCase = true) ||
                           host.contains("hdfilmcehennemi", ignoreCase = true) ||
                           host.contains("filmcehennemi", ignoreCase = true)
        if (isRapidOrHdf) {
            val builder = request.newBuilder()
            if (request.header("User-Agent").isNullOrEmpty()) {
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            }
            builder.header("Referer", "https://www.hdfilmcehennemi.nl/")
            builder.header("Origin", "https://www.hdfilmcehennemi.nl")
            chain.proceed(builder.build())
        } else {
            chain.proceed(request)
        }
    }

    /**
     * Video oynatıcı (ExoPlayer) HLS ve TS segment indirmeleri için optimize edilmiş istemci
     */
    fun getMediaOkHttpClient(): OkHttpClient {
        return try {
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            // HLS segmentleri için optimize: TCP bağlantılarını yeniden kullan (donma azaltıcı)
            val dispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 6
            }
            val pool = okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES)

            OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(pool)
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .addInterceptor(fastplayInterceptor)
                .addInterceptor(rapidrameMediaInterceptor)
                .addInterceptor(userAgentInterceptor)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            getUnsafeOkHttpClient()
        }
    }
}
