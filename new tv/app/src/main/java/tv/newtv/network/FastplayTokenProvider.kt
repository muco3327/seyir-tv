package tv.newtv.network

object FastplayTokenProvider {
    @Volatile private var currentSp: String? = null
    @Volatile private var currentSpT: Long = 0L
    @Volatile var currentReferer: String? = null

    fun setSession(sp: String, spT: Long, referer: String) {
        currentSp = sp
        currentSpT = spT
        currentReferer = referer
    }

    fun generateXSp(): String? {
        val sp = currentSp ?: return null
        val spT = currentSpT
        val randVal = (Math.random() * 2176782336.0).toLong()
        val r = java.lang.Long.toString(randVal, 36)
        val combined = "$sp|$spT|$r"
        var t = 2166136261L
        for (ch in combined) {
            t = t xor ch.code.toLong()
            t = (t * 16777619L) and 0xFFFFFFFFL
        }
        val hashHex = java.lang.Long.toHexString(t)
        return "$spT.$r.$hashHex"
    }
}
