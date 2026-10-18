package tw.kevinzhang.newshub.github

import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.Request
import tw.kevinzhang.marketplace.RepositoryAccessCredential

class GitHubDeviceChallenge internal constructor(internal val deviceCode: String, val userCode: String,
    val verificationUri: String, val expiresAt: Long, internal val intervalSeconds: Long) {
    override fun toString() = "GitHubDeviceChallenge(REDACTED)"
}

class GitHubOAuthToken(val credential: RepositoryAccessCredential, val expiresAt: Long?) {
    override fun toString() = "GitHubOAuthToken(REDACTED)"
}

class GitHubOAuthClient(private val http: GitHubHttp, val clientId: String,
    private val now: () -> Long = System::currentTimeMillis) {
    suspend fun start(): GitHubDeviceChallenge {
        check(clientId.isNotBlank()) { "GitHub 登入尚未設定。" }
        val json = post("device/code", FormBody.Builder().add("client_id", clientId).add("scope", "repo").build())
        val uri = json.get("verification_uri")?.asString
        if (uri != "https://github.com/login/device") throw GitHubFailure()
        val expires = json.get("expires_in").asLong.coerceIn(1, 900)
        return GitHubDeviceChallenge(json.get("device_code").asString, json.get("user_code").asString,
            uri, now() + expires * 1000, json.get("interval")?.asLong?.coerceIn(1, 900) ?: 5)
    }

    suspend fun awaitAuthorization(challenge: GitHubDeviceChallenge): GitHubOAuthToken {
        var interval = challenge.intervalSeconds
        while (now() < challenge.expiresAt) {
            val wait = interval * 1000
            if (now() + wait >= challenge.expiresAt) break
            delay(wait)
            val json = post("oauth/access_token", FormBody.Builder().add("client_id", clientId)
                .add("device_code", challenge.deviceCode).add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
            when (json.get("error")?.asString) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = maxOf(interval + 5, json.get("interval")?.asLong ?: 0)
                null -> {
                    if (json.get("token_type")?.asString != "bearer" ||
                        "repo" !in (json.get("scope")?.asString ?: "").split(',', ' ')) throw GitHubFailure()
                    return GitHubOAuthToken(RepositoryAccessCredential.githubToken(json.get("access_token").asString),
                        json.get("expires_in")?.asLong?.let { now() + it.coerceIn(1, 86400) * 1000 })
                }
                else -> throw GitHubFailure(kind = json.get("error").asString)
            }
        }
        throw GitHubFailure(kind = "expired_token")
    }

    private suspend fun post(path: String, body: FormBody) = http.json(Request.Builder()
        .url("https://github.com/login/$path").header("Accept", "application/json").post(body).build()).first.asJsonObject
}
