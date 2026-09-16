package tv.newtv.utils

/**
 * Vidmoly, Rapidrame gibi sağlayıcıların video kaynaklarını gizlemek için kullandığı
 * eval(function(p,a,c,k,e,d){...}) şeklindeki obfuscation yapısını çözer.
 */
object JsUnpacker {

    fun unpack(javascript: String): String {
        // eval(function(p,a,c,k,e,d)...) paternini eşler
        val pattern = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*d\s*\).*?\}\s*\(\s*'(.*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'(.*?)'\.split\s*\(\s*'\|'\s*\)""")
        
        val matchResult = pattern.find(javascript)
        if (matchResult != null) {
            val p = matchResult.groupValues[1]
            val a = matchResult.groupValues[2].toIntOrNull() ?: return javascript
            val c = matchResult.groupValues[3].toIntOrNull() ?: return javascript
            val k = matchResult.groupValues[4].split("|")
            
            return decode(p, a, c, k)
        }
        
        return javascript
    }

    private fun decode(p: String, a: Int, c: Int, k: List<String>): String {
        var unpacked = p
        
        // Kelime kelime dolaşarak şifreli anahtarları sözlükteki karşılıklarıyla (k) değiştirir
        val wordRegex = Regex("""\b\w+\b""")
        unpacked = wordRegex.replace(unpacked) { match ->
            val word = match.value
            try {
                val index = unbase(word, a)
                if (index != -1 && index < k.size && k[index].isNotEmpty()) {
                    k[index]
                } else {
                    word
                }
            } catch (e: Exception) {
                word
            }
        }
        
        // Kaçış karakterlerini temizle
        unpacked = unpacked.replace("\\'", "'").replace("\\\\", "\\")

        return unpacked
    }

    private fun unbase(value: String, base: Int): Int {
        val dict = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var result = 0
        for (char in value) {
            val digit = dict.indexOf(char)
            if (digit == -1 || digit >= base) {
                return -1
            }
            result = result * base + digit
        }
        return result
    }
}
