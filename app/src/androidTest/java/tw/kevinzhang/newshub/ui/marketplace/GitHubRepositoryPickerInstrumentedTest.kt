package tw.kevinzhang.newshub.ui.marketplace

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.newshub.github.*

@RunWith(AndroidJUnit4::class)
class GitHubRepositoryPickerInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun searchSelectAndLogoutKeepsNoPrivateResults() {
        val storage = object : GitHubAccountStorage {
            var saved: GitHubStoredAccount? = GitHubStoredAccount(GitHubAccount(1, "test-account"), RepositoryAccessCredential.githubToken("fake-token"), null)
            override fun read() = saved
            override fun write(account: GitHubStoredAccount) { saved = account }
            override fun clear() { saved = null }
        }
        val http = GitHubHttp(OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""[{"id":1,"owner":{"login":"team"},"name":"extensions","default_branch":"release/stable","private":true,"description":null}]""".toResponseBody()).build()
        }.build())
        val catalog = GitHubRepositoryCatalog(http)
        val session = GitHubAccountSession(GitHubOAuthClient(http, "fake-client"), catalog, storage)
        val vm = GitHubRepositoryPickerViewModel(session, catalog)
        var selected: GitHubRepo? = null
        val showing = mutableStateOf(true)
        compose.setContent { MaterialTheme {
            GitHubRepositoryPicker({ repo, _ -> selected = repo; showing.value = false }, {}, vm, showRepositories = showing.value)
        } }
        compose.onNodeWithText("test-account").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodesWithText("team/extensions").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("搜尋儲存庫").performTextInput("extensions")
        compose.onNodeWithText("team/extensions").performClick()
        compose.runOnIdle { assertEquals("release/stable", selected?.defaultBranch) }
        compose.onNodeWithText("team/extensions").assertDoesNotExist()
        compose.runOnIdle { showing.value = true }
        compose.onNodeWithText("team/extensions").assertIsDisplayed()
        compose.onNodeWithText("extensions").assertIsDisplayed()
        compose.onNodeWithContentDescription("帳號選單").performClick()
        compose.onNodeWithText("登出 GitHub").performClick()
        compose.onNodeWithText("繼續連接 GitHub").assertIsDisplayed()
        compose.onNodeWithText("team/extensions").assertDoesNotExist()
        compose.runOnIdle { assertNull(storage.saved) }
    }
}
