package tw.kevinzhang.newshub.auth.oauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuth1SignerTest {
    @Test fun `matches the OAuth 1 HMAC-SHA1 reference signature`() {
        val header = OAuth1Signer(
            nonce = { "kllo9940pd9333jh" },
            epochSeconds = { 1_191_242_096L },
        ).authorizationHeader(
            method = "GET",
            url = "http://photos.example.net/photos?file=vacation.jpg&size=original".toHttpUrl(),
            consumerKey = "dpf43f3p2l4k3l03",
            consumerSecret = "kd94hf93k423kf44",
            token = "nnch734d00sl2jdk",
            tokenSecret = "pfkkdhi9sl3r4s00",
        )

        assertTrue(header.startsWith("OAuth "))
        assertTrue(header.contains("oauth_signature=\"tR3%2BTy81lMeYAr%2FFid0kMTYa%2FWM%3D\""))
        assertFalse(header.contains("kd94hf93k423kf44"))
        assertFalse(header.contains("pfkkdhi9sl3r4s00"))
    }

    @Test fun `encodes UTF-8 and spaces with RFC 3986 rules`() {
        assertEquals("a%20b%2Bc%2F%E5%99%97", oauth1PercentEncode("a b+c/噗"))
    }

    @Test fun `parses request and access token responses without exposing unrelated fields`() {
        assertEquals(
            OAuth1RequestToken("token value", "secret+value"),
            parseRequestToken("oauth_token=token+value&oauth_token_secret=secret%2Bvalue&user_id=7"),
        )
        assertEquals(
            OAuth1RequestToken("token", "secret"),
            parseRequestToken(
                "oauth_token=token&oauth_token_secret=secret&oauth_callback_confirmed=true",
                requireCallbackConfirmed = true,
            ),
        )
        assertTrue(
            runCatching {
                parseRequestToken(
                    "oauth_token=token&oauth_token_secret=secret&oauth_callback_confirmed=false",
                    requireCallbackConfirmed = true,
                )
            }.isFailure,
        )
    }

    @Test fun `callback comparison ignores query but fixes the scheme host and path`() {
        val expected = "tw.kevinzhang.newshub.oauth://callback/plurk"
        assertTrue(oauth1CallbackMatches("$expected?oauth_token=t&oauth_verifier=v", expected))
        assertFalse(oauth1CallbackMatches("tw.kevinzhang.newshub.oauth://callback/reddit?oauth_token=t", expected))
        assertFalse(oauth1CallbackMatches("https://callback/plurk?oauth_token=t", expected))
    }
}
