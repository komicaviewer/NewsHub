package tw.kevinzhang.newshub.auth.oauth

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val PLURK_REGISTRATION_ID = "plurk-mobile"
private const val PLURK_CONSUMER_KEY_METADATA = "newshub.oauth1.plurk.consumer_key"
private const val PLURK_CONSUMER_SECRET_METADATA = "newshub.oauth1.plurk.consumer_secret"
private const val PLURK_CALLBACK_URI = "tw.kevinzhang.newshub.oauth://callback/plurk"

@Singleton
class PlurkOAuth1ProviderAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
) : OAuth1ProviderAdapter {
    override val providerId: String = "plurk"
    override val apiExactHosts: Set<String> = setOf("www.plurk.com")

    override fun registration(registrationId: String): OAuth1ClientRegistration? {
        if (registrationId != PLURK_REGISTRATION_ID) return null
        val metadata = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        }.metaData
        return OAuth1ClientRegistration(
            id = PLURK_REGISTRATION_ID,
            consumerKey = metadata?.getString(PLURK_CONSUMER_KEY_METADATA).orEmpty().trim(),
            consumerSecret = metadata?.getString(PLURK_CONSUMER_SECRET_METADATA).orEmpty().trim(),
            callbackUri = PLURK_CALLBACK_URI,
        )
    }

    override fun requestTokenRequest(
        registration: OAuth1ClientRegistration,
        signer: OAuth1Signer,
    ): Request {
        val url = "https://www.plurk.com/OAuth/request_token".toHttpUrl()
        return Request.Builder()
            .url(url)
            .header(
                "Authorization",
                signer.authorizationHeader(
                    method = "POST",
                    url = url,
                    consumerKey = registration.consumerKey,
                    consumerSecret = registration.consumerSecret,
                    additionalOAuthParameters = mapOf("oauth_callback" to registration.callbackUri),
                ),
            )
            .post(FormBody.Builder().build())
            .build()
    }

    override fun authorizationUri(registration: OAuth1ClientRegistration, requestToken: String): Uri =
        Uri.parse("https://www.plurk.com/OAuth/authorize").buildUpon()
            .appendQueryParameter("oauth_token", requestToken)
            .build()

    override fun accessTokenRequest(
        registration: OAuth1ClientRegistration,
        requestToken: String,
        requestTokenSecret: String,
        verifier: String,
        signer: OAuth1Signer,
    ): Request {
        val url = "https://www.plurk.com/OAuth/access_token".toHttpUrl()
        return Request.Builder()
            .url(url)
            .header(
                "Authorization",
                signer.authorizationHeader(
                    method = "POST",
                    url = url,
                    consumerKey = registration.consumerKey,
                    consumerSecret = registration.consumerSecret,
                    token = requestToken,
                    tokenSecret = requestTokenSecret,
                    additionalOAuthParameters = mapOf("oauth_verifier" to verifier),
                ),
            )
            .post(FormBody.Builder().build())
            .build()
    }
}
