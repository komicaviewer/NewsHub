package tw.kevinzhang.newshub.github

import com.google.gson.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import tw.kevinzhang.marketplace.RepositoryAccessCredential

data class GitHubRepo(val id: Long, val owner: String, val name: String, val defaultBranch: String,
    val private: Boolean, val description: String?) {
    val fullName: String get() = "$owner/$name"
    val url: String get() = "https://github.com/$fullName"
    fun matches(query: String): Boolean = fullName.contains(query, true) || description.orEmpty().contains(query, true)
}
data class GitHubRepoPage(val items: List<GitHubRepo>, val nextPage: Int?)

class GitHubRepositoryCatalog(private val http: GitHubHttp) {
    suspend fun getAccount(credential: RepositoryAccessCredential): GitHubAccount {
        val json = http.get("user", credential).first.asJsonObject
        return GitHubAccount(json.get("id").asLong, json.get("login").asString)
    }
    suspend fun listRepositories(credential: RepositoryAccessCredential, page: Int): GitHubRepoPage {
        require(page > 0)
        val (json, next) = http.get("user/repos?per_page=100&page=$page&sort=full_name&affiliation=owner,collaborator,organization_member", credential)
        return GitHubRepoPage(json.asJsonArray.map(::parseRepo), if (next) page + 1 else null)
    }
    suspend fun getRepository(credential: RepositoryAccessCredential, owner: String, name: String): GitHubRepo {
        val url = "https://api.github.com/".toHttpUrl().newBuilder().addPathSegment("repos")
            .addPathSegment(owner).addPathSegment(name).build()
        return parseRepo(http.get(url.encodedPath.removePrefix("/"), credential).first)
    }
    private fun parseRepo(value: JsonElement): GitHubRepo {
        val item = value.asJsonObject
        return GitHubRepo(item.get("id").asLong, item.getAsJsonObject("owner").get("login").asString,
            item.get("name").asString, item.get("default_branch").asString, item.get("private").asBoolean,
            item.get("description")?.takeUnless { it.isJsonNull }?.asString)
    }
}
