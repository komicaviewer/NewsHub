package tw.kevinzhang.newshub.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.newshub.BuildConfig
import tw.kevinzhang.newshub.github.*

@RunWith(AndroidJUnit4::class)
class GitHubAccountVaultInstrumentedTest {
    @Test fun vaultUsesKeystoreAndClearsCorruption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = GitHubAccountVault(context)
        try {
            vault.write(GitHubStoredAccount(GitHubAccount(1, "test-account"), RepositoryAccessCredential.githubToken("fake-vault-token"), null))
            val payload = context.getSharedPreferences("github_account_v1", 0).getString("payload", "")!!
            assertFalse(payload.contains("fake-vault-token"))
            assertEquals("test-account", vault.read()?.account?.login)
            context.getSharedPreferences("github_account_v1", 0).edit().putString("payload", "corrupted").commit()
            assertNull(vault.read())
        } finally { vault.clear() }
    }

    @Test fun liveDeviceCodeEndpointAcceptsConfiguredApp() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("githubLiveDeviceCodeSmoke") == "true")
        val challenge = GitHubOAuthClient(GitHubHttp(OkHttpClient()), BuildConfig.GITHUB_OAUTH_CLIENT_ID).start()
        assertEquals("https://github.com/login/device", challenge.verificationUri)
        assertTrue(challenge.userCode.isNotBlank())
        assertTrue(challenge.expiresAt > System.currentTimeMillis())
        // No polling or user grant: this validates only the public app registration and device endpoint.
    }
}
