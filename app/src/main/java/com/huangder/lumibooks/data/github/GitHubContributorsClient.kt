package com.huangder.lumibooks.data.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class Contributor(
    val login: String,
    val avatarUrl: String,
    val htmlUrl: String,
    val contributions: Int
)

/**
 * Fetches Lumi repository contributors from the GitHub API,
 * ordered by contribution count (huangder first, then others).
 */
@Singleton
class GitHubContributorsClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchContributors(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO
    ): List<Contributor> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/contributors?per_page=100")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lumi-App")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.body == null) {
                error("GitHub contributors request failed: HTTP ${response.code}")
            }
            val array = JSONArray(response.body!!.string())
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Contributor(
                            login = item.optString("login"),
                            avatarUrl = item.optString("avatar_url"),
                            htmlUrl = item.optString("html_url"),
                            contributions = item.optInt("contributions", 0)
                        )
                    )
                }
            }.filter { it.login.isNotBlank() && it.avatarUrl.isNotBlank() }
        }
    }

    companion object {
        const val DEFAULT_OWNER = "huangder"
        const val DEFAULT_REPO = "Lumi_Books"
    }
}
