package tv.seyir.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class DizillaParserTest {
    @Test
    public void testEnrichWithoutSecureDataDoesNotCrash() {
        JSONObject data = new JSONObject();
        DizillaParser.enrich(data);
        assertNull(data.optJSONArray("frames"));
    }

    @Test
    public void testDecryptionRoundTrip() throws Exception {
        // Encrypt dummy payload with same key/iv
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        byte[] key = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] iv = new byte[16];
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
        String json = "{\"RelatedResults\":{\"getEpisodeSources\":{\"result\":[{\"source_content\":\"<iframe src=\\\"//four.pichive.online/iframe.php?v=123\\\"></iframe>\",\"language_name\":\"Türkçe Dublaj\",\"source_name\":\"PUB\"}]}}}";
        byte[] enc = cipher.doFinal(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String b64 = java.util.Base64.getEncoder().encodeToString(enc);

        JSONObject data = new JSONObject();
        data.put("secureData", b64);
        DizillaParser.enrich(data);

        JSONArray frames = data.optJSONArray("frames");
        assertNotNull(frames);
        assertEquals(1, frames.length());
        assertEquals("https://four.pichive.online/iframe.php?v=123", frames.getString(0));

        JSONArray actions = data.optJSONArray("actions");
        assertNotNull(actions);
        assertEquals(1, actions.length());
        assertEquals("Türkçe Dublaj · PUB", actions.getJSONObject(0).getString("label"));
        assertEquals("https://four.pichive.online/iframe.php?v=123", actions.getJSONObject(0).getString("frame"));
    }
}
