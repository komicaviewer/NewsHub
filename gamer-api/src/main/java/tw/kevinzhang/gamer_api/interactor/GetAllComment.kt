package tw.kevinzhang.gamer_api.interactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.gildor.coroutines.okhttp.await
import tw.kevinzhang.gamer_api.model.GComment
import tw.kevinzhang.gamer_api.parser.CommentListParser
import tw.kevinzhang.gamer_api.request.RequestBuilderImpl

class GetAllComment(
    private val client: OkHttpClient,
) {
    suspend fun invoke(req: Request): List<GComment> = withContext(Dispatchers.IO) {
        val res = client.newCall(req).await()
        CommentListParser(RequestBuilderImpl()).parse(res.body!!, req)
    }
}