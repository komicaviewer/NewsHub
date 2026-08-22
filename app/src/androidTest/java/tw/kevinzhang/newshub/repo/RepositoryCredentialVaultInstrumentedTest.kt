package tw.kevinzhang.newshub.repo

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.marketplace.RepositoryAccessCredential

@RunWith(AndroidJUnit4::class)
class RepositoryCredentialVaultInstrumentedTest {
    @Test
    fun credentialIsEncryptedAtRestAndCanBeRevoked() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = RepositoryCredentialVault(context)
        val domainId = "00000000-0000-4000-8000-000000000777"
        val token = "github_pat_test_secret_123"
        try {
            vault.saveCredential(domainId, RepositoryAccessCredential.githubToken(token))

            val persistedPayload = context.getSharedPreferences(
                "repository_credentials_v1",
                Context.MODE_PRIVATE,
            ).getString("payload", null).orEmpty()
            assertFalse(persistedPayload.contains(token))
            assertEquals(token, vault.getCredential(domainId)?.withSecret { it })

            vault.deleteCredential(domainId)
            assertNull(vault.getCredential(domainId))
        } finally {
            vault.deleteCredential(domainId)
        }
    }
}
