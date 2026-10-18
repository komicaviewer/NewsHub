package tw.kevinzhang.newshub.github

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GitHubOAuthClientTest {
    @Test fun `pending and slow down respect intervals without secret logging`() = runTest {
        val times = mutableListOf<Long>()
        val replies = ArrayDeque(listOf(
            """{"device_code":"secret-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":900,"interval":5}""",
            """{"error":"authorization_pending"}""", """{"error":"slow_down"}""",
            """{"access_token":"secret-token","token_type":"bearer","scope":"repo","expires_in":28800}"""))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            times += testScheduler.currentTime
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(replies.removeFirst().toResponseBody("application/json".toMediaType())).build()
        }.build()
        val oauth = GitHubOAuthClient(GitHubHttp(client), "client", { testScheduler.currentTime })
        val challenge = oauth.start()
        assertFalse(challenge.toString().contains("secret-code"))
        val token = oauth.awaitAuthorization(challenge)
        assertEquals(listOf(0L, 5000L, 10000L, 20000L), times)
        assertFalse(token.toString().contains("secret-token"))
    }

    @Test fun `denial and expiry end polling and unsafe URI is rejected`() = runTest {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"error":"access_denied"}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val oauth = GitHubOAuthClient(GitHubHttp(client), "client", { testScheduler.currentTime })
        val challenge = GitHubDeviceChallenge("secret", "ABCD-EFGH", "https://github.com/login/device", 900000, 5)
        try { oauth.awaitAuthorization(challenge); fail() } catch (_: GitHubFailure) { }
        assertEquals(1, calls)
        try { oauth.awaitAuthorization(GitHubDeviceChallenge("secret", "code", "https://github.com/login/device", 0, 5)); fail() }
        catch (_: GitHubFailure) { }
        assertEquals(1, calls)
    }
}
