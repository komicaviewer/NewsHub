package tw.kevinzhang.newshub.auth.oauth

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.Request
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

private const val REDDIT_REGISTRATION_ID = "reddit-installed"
private const val REDDIT_CLIENT_ID_METADATA = "newshub.oauth.reddit.client_id"
private const val REDDIT_REDIRECT_URI = "tw.kevinzhang.newshub.oauth://callback/reddit"

/** Reddit's public installed-app registration. No client secret is stored in the APK. */
@Singleton
class RedditOAuthProviderAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : OAuthProviderAdapter {
    override val providerId: String = "reddit"
    override val allowedScopes: Set<String> = setOf("identity", "read", "mysubreddits")
    override val apiExactHosts: Set<String> = setOf("oauth.reddit.com")

    override fun registration(registrationId: String): OAuthClientRegistration? {
        if (registrationId != REDDIT_REGISTRATION_ID) return null
        val applicationInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        }
        val clientId = applicationInfo.metaData?.getString(REDDIT_CLIENT_ID_METADATA).orEmpty().trim()
        return OAuthClientRegistration(REDDIT_REGISTRATION_ID, clientId, REDDIT_REDIRECT_URI)
    }

    override fun authorizationUri(
        registration: OAuthClientRegistration,
        scopes: Set<String>,
        state: String,
        codeChallenge: String,
    ): Uri = Uri.parse("https://www.reddit.com/api/v1/authorize.compact").buildUpon()
        .appendQueryParameter("client_id", registration.clientId)
        .appendQueryParameter("response_type", "code")
        .appendQueryParameter("state", state)
        .appendQueryParameter("redirect_uri", registration.redirectUri)
        .appendQueryParameter("duration", "permanent")
        .appendQueryParameter("scope", scopes.sorted().joinToString(" "))
        .build()

    override fun tokenExchangeRequest(
        registration: OAuthClientRegistration,
        code: String,
        codeVerifier: String,
    ): Request = tokenRequest(
        registration,
        FormBody.Builder()
            .addRequired("grant_type", "authorization_code")
            .addRequired("code", code)
            .addRequired("redirect_uri", registration.redirectUri)
            .build(),
    )

    override fun tokenRefreshRequest(registration: OAuthClientRegistration, refreshToken: String): Request =
        tokenRequest(
            registration,
            FormBody.Builder()
                .addRequired("grant_type", "refresh_token")
                .addRequired("refresh_token", refreshToken)
                .build(),
        )

    override fun parseTokenResponse(
        responseBody: String,
        requestedScopes: Set<String>,
        previousRefreshToken: String?,
        nowEpochMillis: Long,
    ): OAuthTokenSet {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val accessToken = json.get("access_token")?.asString?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("OAuth token response omitted access token")
        val tokenType = json.get("token_type")?.asString?.takeIf { it.equals("bearer", ignoreCase = true) }
            ?: throw IllegalArgumentException("OAuth token response used an unsupported token type")
        val expiresInSeconds = json.get("expires_in")?.asLong?.coerceIn(1L, 24L * 60L * 60L)
            ?: throw IllegalArgumentException("OAuth token response omitted expiry")
        val returnedScopes = json.get("scope")?.asString
            ?.split(' ')
            ?.filterTo(linkedSetOf(), String::isNotBlank)
            ?.takeIf(Set<String>::isNotEmpty)
            ?: requestedScopes
        require(returnedScopes.all { it in allowedScopes } && returnedScopes.containsAll(requestedScopes)) {
            "OAuth token response did not grant the requested scopes"
        }
        return OAuthTokenSet(
            accessToken = accessToken,
            refreshToken = json.get("refresh_token")?.asString?.takeIf(String::isNotBlank) ?: previousRefreshToken,
            tokenType = tokenType,
            scopes = returnedScopes,
            expiresAtEpochMillis = nowEpochMillis + expiresInSeconds * 1_000L,
        )
    }

    private fun tokenRequest(registration: OAuthClientRegistration, body: FormBody): Request {
        val basic = Base64.encodeToString(
            "${registration.clientId}:".toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP,
        )
        return Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .header("Authorization", "Basic $basic")
            .header("Accept", "application/json")
            .header("User-Agent", redditUserAgent())
            .post(body)
            .build()
    }

    private fun redditUserAgent(): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,32}")) } ?: "unknown"
        return "android:${context.packageName}:$version (by /u/Due-Valuable2441)"
    }
}
