package tw.kevinzhang.newshub.github

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import tw.kevinzhang.marketplace.RepositoryAccessCredential
import tw.kevinzhang.newshub.security.EncryptedJsonStore

data class GitHubAccount(val id: Long, val login: String)
class GitHubStoredAccount(val account: GitHubAccount, val credential: RepositoryAccessCredential, val expiresAt: Long?)
interface GitHubAccountStorage {
    fun read(): GitHubStoredAccount?
    fun write(account: GitHubStoredAccount)
    fun clear()
}

class GitHubAccountVault(context: Context) : GitHubAccountStorage {
    private val store = EncryptedJsonStore(context, "github_account_v1", "newshub.github.account.v1", "GitHub account")
    override fun read(): GitHubStoredAccount? = try {
        store.read()?.let { value ->
            val json = JsonParser.parseString(value).asJsonObject
            GitHubStoredAccount(GitHubAccount(json.get("id").asLong, json.get("login").asString),
                RepositoryAccessCredential.githubToken(json.get("token").asString), json.get("expiresAt")?.asLong)
        }
    } catch (_: Exception) { store.clear(); null }
    override fun write(account: GitHubStoredAccount) {
        val json = JsonObject().apply {
            addProperty("id", account.account.id); addProperty("login", account.account.login)
            account.credential.withSecret { addProperty("token", it) }
            account.expiresAt?.let { addProperty("expiresAt", it) }
        }
        store.write(json.toString())
    }
    override fun clear() = store.clear()
}
